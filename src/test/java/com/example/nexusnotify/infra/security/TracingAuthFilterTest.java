package com.example.nexusnotify.infra.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TracingAuthFilterTest {

    @Test
    void allowsTheConfiguredKeyWithoutAUserToken() throws Exception {
        TracingAuthFilter filter = new TracingAuthFilter("metrics-test-key");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/metrics");
        request.addParameter("key", "metrics-test-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"wrong-key", "metrics-test-key "})
    void rejectsMissingOrIncorrectKeysWithoutExposingThem(String suppliedKey) throws Exception {
        TracingAuthFilter filter = new TracingAuthFilter("metrics-test-key");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/metrics");
        if (suppliedKey != null) {
            request.addParameter("key", suppliedKey);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("\"error\":")
                .doesNotContain("metrics-test-key", "wrong-key");
        verifyNoInteractions(chain);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void blocksMonitoringWhenTheServerKeyIsNotConfigured(String configuredKey) throws Exception {
        TracingAuthFilter filter = new TracingAuthFilter(configuredKey);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/metrics");
        request.addParameter("key", "any-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("TRACING_ACESS nao configurada")
                .doesNotContain("any-key");
        verifyNoInteractions(chain);
    }
}
