package com.example.nexusnotify.config;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

@Configuration
@ConditionalOnProperty(prefix = "notify.push", name = "enabled", havingValue = "true")
public class FirebaseConfiguration {

    @Bean(destroyMethod = "delete")
    FirebaseApp notifyFirebaseApp(
            @Value("${notify.push.project-id:}") String projectId,
            @Value("${notify.push.credentials-base64:}") String credentialsBase64) {

        if (projectId.isBlank() || credentialsBase64.isBlank()) {
            throw new IllegalStateException(
                    "Configure FIREBASE_PROJECT_ID e FIREBASE_CREDENTIALS_BASE64 para habilitar o Firebase.");
        }

        ServiceAccountCredentials credentials = loadCredentials(credentialsBase64);
        if (!projectId.equals(credentials.getProjectId())) {
            throw new IllegalStateException(
                    "FIREBASE_PROJECT_ID deve corresponder ao projeto da credencial.");
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(projectId)
                .setConnectTimeout(5_000)
                .setReadTimeout(10_000)
                .setWriteTimeout(10_000)
                .build();
        return FirebaseApp.initializeApp(options, "nexus-notify");
    }

    @Bean
    FirebaseMessaging firebaseMessaging(FirebaseApp notifyFirebaseApp) {
        return FirebaseMessaging.getInstance(notifyFirebaseApp);
    }

    private ServiceAccountCredentials loadCredentials(String encodedCredentials) {
        try {
            byte[] json = Base64.getDecoder().decode(encodedCredentials.trim());
            try (ByteArrayInputStream input = new ByteArrayInputStream(json)) {
                return ServiceAccountCredentials.fromStream(input);
            }
        } catch (IOException | RuntimeException exception) {
            // Erros de leitura podem incluir partes do JSON; preserve apenas a mensagem segura.
            throw new IllegalStateException(
                    "FIREBASE_CREDENTIALS_BASE64 deve conter um JSON de conta de servico valido em Base64.");
        }
    }
}
