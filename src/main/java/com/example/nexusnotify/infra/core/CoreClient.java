package com.example.nexusnotify.infra.core;

import com.example.nexusnotify.config.properties.CoreProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import java.time.LocalDate;
import java.util.Optional;

public class CoreClient {

    private static final ParameterizedTypeReference<CandidatePage<ProductCandidate>> PRODUCT_RESPONSE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<CandidatePage<EventCandidate>> EVENT_RESPONSE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<CandidatePage<NearbyStoreCandidate>> NEARBY_STORE_RESPONSE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<CandidatePage<NewStoreCandidate>> NEW_STORE_RESPONSE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final CoreProperties properties;

    public CoreClient(RestClient restClient, CoreProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public CandidatePage<ProductCandidate> products(LocalDate from, LocalDate to, int page, int size) {
        return fetch("/internal/notifications/products", from, to, null, page, size, PRODUCT_RESPONSE);
    }

    public CandidatePage<EventCandidate> events(LocalDate from, LocalDate to, int page, int size) {
        return fetch("/internal/notifications/events", from, to, null, page, size, EVENT_RESPONSE);
    }

    public CandidatePage<NearbyStoreCandidate> nearbyStores(double radiusKm, int page, int size) {
        return fetch("/internal/notifications/nearby-stores", null, null, radiusKm,
                page, size, NEARBY_STORE_RESPONSE);
    }

    public CandidatePage<NewStoreCandidate> newStores(LocalDate from, LocalDate to, double radiusKm,
                                                     int page, int size) {
        return fetch("/internal/notifications/new-stores", from, to, radiusKm, page, size, NEW_STORE_RESPONSE);
    }

    private <T> CandidatePage<T> fetch(String path, LocalDate from, LocalDate to, Double radiusKm, int page, int size,
                                       ParameterizedTypeReference<CandidatePage<T>> responseType) {
        if (properties.token() == null || properties.token().isBlank()) {
            throw new CoreIntegrationException("Configure NOTIFY_CORE_TOKEN antes de consultar o Core");
        }
        try {
            CandidatePage<T> result = restClient.get()
                    .uri(builder -> builder.path(path)
                            .queryParamIfPresent("from", Optional.ofNullable(from))
                            .queryParamIfPresent("to", Optional.ofNullable(to))
                            .queryParamIfPresent("radiusKm", Optional.ofNullable(radiusKm))
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
