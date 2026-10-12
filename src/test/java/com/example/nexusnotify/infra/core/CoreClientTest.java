package com.example.nexusnotify.infra.core;

import com.example.nexusnotify.config.CoreProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CoreClientTest {

    private static final String TOKEN = "notify-test-token";
    private static final LocalDate FROM = LocalDate.of(2026, 10, 12);
    private static final LocalDate TO = LocalDate.of(2026, 10, 15);
    private static final String PRODUCTS = "http://core.test/internal/notifications/products"
            + "?from=2026-10-12&to=2026-10-15&page=0&size=100";
    private static final String EVENTS = "http://core.test/internal/notifications/events"
            + "?from=2026-10-12&to=2026-10-15&page=0&size=100";

    private MockRestServiceServer server;
    private CoreClient client;
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://core.test");
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        client = new CoreClient(restClient, properties(TOKEN));
    }

    @Test
    void readsProductsAndSendsSearchParametersAndToken() {
        server.expect(requestTo(PRODUCTS))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Notify-Token", TOKEN))
                .andRespond(withSuccess("""
                        {
                          "content": [
                            {
                              "profileId": 12,
                              "itemId": 45,
                              "foodName": "Leite",
                              "expiryDate": "2026-10-15"
                            }
                          ],
                          "hasNext": true
                        }
                        """, MediaType.APPLICATION_JSON));

        CandidatePage<ProductCandidate> result = client.products(FROM, TO, 0, 100);

        assertEquals(List.of(new ProductCandidate(12, 45, "Leite", TO)), result.content());
        assertTrue(result.hasNext());
        server.verify();
    }

    @Test
    void readsEventsAndConvertsTheirDates() {
        server.expect(requestTo(EVENTS))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Notify-Token", TOKEN))
                .andRespond(withSuccess("""
                        {
                          "content": [
                            {
                              "profileId": 19,
                              "eventId": "507f1f77bcf86cd799439011",
                              "title": "Jantar",
                              "date": "2026-10-15T19:30:00"
                            }
                          ],
                          "hasNext": false
                        }
                        """, MediaType.APPLICATION_JSON));

        CandidatePage<EventCandidate> result = client.events(FROM, TO, 0, 100);

        EventCandidate expected = new EventCandidate(19, "507f1f77bcf86cd799439011",
                "Jantar", LocalDateTime.of(2026, 10, 15, 19, 30));
        assertEquals(List.of(expected), result.content());
        assertFalse(result.hasNext());
        server.verify();
    }

    @Test
    void acceptsAnEmptyBatch() {
        server.expect(requestTo(PRODUCTS))
                .andRespond(withSuccess("""
                        {"content": [], "hasNext": false}
                        """, MediaType.APPLICATION_JSON));

        CandidatePage<ProductCandidate> result = client.products(FROM, TO, 0, 100);

        assertTrue(result.content().isEmpty());
        assertFalse(result.hasNext());
        server.verify();
    }

    @Test
    void reportsAuthenticationAndServerErrors() {
        server.expect(requestTo(PRODUCTS)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(PRODUCTS)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        for (int status : List.of(401, 500)) {
            CoreIntegrationException exception = assertThrows(CoreIntegrationException.class,
                    () -> client.products(FROM, TO, 0, 100));
            RestClientResponseException cause = assertInstanceOf(
                    RestClientResponseException.class, exception.getCause());
            assertEquals(status, cause.getStatusCode().value());
        }
        server.verify();
    }

    @Test
    void reportsCommunicationTimeouts() {
        server.expect(requestTo(EVENTS)).andRespond(withException(new SocketTimeoutException("timeout")));

        CoreIntegrationException exception = assertThrows(CoreIntegrationException.class,
                () -> client.events(FROM, TO, 0, 100));

        assertInstanceOf(SocketTimeoutException.class, exception.getCause().getCause());
        server.verify();
    }

    @Test
    void rejectsAbsentOrMalformedResponses() {
        server.expect(requestTo(PRODUCTS)).andRespond(withStatus(HttpStatus.NO_CONTENT));
        server.expect(requestTo(PRODUCTS)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThrows(CoreIntegrationException.class, () -> client.products(FROM, TO, 0, 100));
        assertThrows(CoreIntegrationException.class, () -> client.products(FROM, TO, 0, 100));
        server.verify();
    }

    @Test
    void rejectsCallsWithoutAConfiguredToken() {
        for (String token : List.of("", " ")) {
            CoreClient unconfiguredClient = new CoreClient(restClient, properties(token));
            assertThrows(CoreIntegrationException.class,
                    () -> unconfiguredClient.products(FROM, TO, 0, 100));
        }
        server.verify();
    }

    private CoreProperties properties(String token) {
        return new CoreProperties(URI.create("http://core.test"), token,
                Duration.ofSeconds(2), Duration.ofSeconds(5));
    }
}
