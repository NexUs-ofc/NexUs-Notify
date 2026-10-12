package com.example.nexusnotify.infra.core;

import com.example.nexusnotify.config.CoreProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import java.time.LocalDate;

public class CoreClient {

    private static final ParameterizedTypeReference<CandidatePage<ProductCandidate>> PRODUCT_RESPONSE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<CandidatePage<EventCandidate>> EVENT_RESPONSE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final CoreProperties properties;

    public CoreClient(RestClient restClient, CoreProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public CandidatePage<ProductCandidate> products(LocalDate from, LocalDate to, int page, int size) {
        return fetch("/internal/notifications/products", from, to, page, size, PRODUCT_RESPONSE);
    }

    public CandidatePage<EventCandidate> events(LocalDate from, LocalDate to, int page, int size) {
        return fetch("/internal/notifications/events", from, to, page, size, EVENT_RESPONSE);
    }

    private <T> CandidatePage<T> fetch(String path, LocalDate from, LocalDate to, int page, int size,
                                       ParameterizedTypeReference<CandidatePage<T>> responseType) {
        if (properties.token() == null || properties.token().isBlank()) {
            throw new CoreIntegrationException("Configure NOTIFY_CORE_TOKEN antes de consultar o Core");
        }
        try {
            CandidatePage<T> result = restClient.get()
                    .uri(builder -> builder.path(path)
                            .queryParam("from", from)
                            .queryParam("to", to)
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .build())
                    .header("X-Notify-Token", properties.token())
                    .retrieve()
                    .body(responseType);
            if (result == null || result.content() == null) {
                throw new CoreIntegrationException("O Core retornou uma resposta invalida em " + path);
            }
            return result;
        } catch (RestClientResponseException exception) {
            throw new CoreIntegrationException(
                    "O Core respondeu com status " + exception.getStatusCode().value() + " em " + path, exception);
        } catch (RestClientException exception) {
            throw new CoreIntegrationException("Falha ao consultar o Core em " + path, exception);
        }
    }
}
