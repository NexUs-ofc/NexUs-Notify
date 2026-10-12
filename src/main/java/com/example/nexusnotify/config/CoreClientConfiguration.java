package com.example.nexusnotify.config;

import com.example.nexusnotify.infra.core.CoreClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(CoreProperties.class)
public class CoreClientConfiguration {

    @Bean
    HttpClient coreHttpClient(CoreProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean
    CoreClient coreClient(CoreProperties properties, HttpClient coreHttpClient) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(coreHttpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
        return new CoreClient(restClient, properties);
    }
}
