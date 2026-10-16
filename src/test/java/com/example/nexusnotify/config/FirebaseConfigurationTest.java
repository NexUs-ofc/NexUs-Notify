package com.example.nexusnotify.config;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseConfigurationTest {

    private static final String PROJECT_ID = "notify-test";
    private static final String INVALID_CREDENTIAL_MESSAGE =
            "FIREBASE_CREDENTIALS_BASE64 deve conter um JSON de conta de servico valido em Base64.";
    private static String testPrivateKey;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(FirebaseConfiguration.class);

    @BeforeAll
    static void generateTestKey() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        testPrivateKey = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
    }

    @Test
    void doesNotInitializeFirebaseByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(FirebaseApp.class).doesNotHaveBean(FirebaseMessaging.class);
        });
    }

    @Test
    void ignoresCredentialsWhenDisabled() {
        runner.withPropertyValues("notify.push.enabled=false", "notify.push.credentials-base64=invalid-secret!")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(FirebaseApp.class).doesNotHaveBean(FirebaseMessaging.class);
                });
    }

    @Test
    void initializesMessagingAndDeletesItsAppWhenTheContextCloses() {
        AtomicReference<FirebaseApp> registered = new AtomicReference<>();
        configured(credentials(PROJECT_ID)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(FirebaseApp.class).hasSingleBean(FirebaseMessaging.class);
            FirebaseApp app = context.getBean(FirebaseApp.class);
            registered.set(app);
            assertThat(app.getName()).isEqualTo("nexus-notify");
            assertThat(app.getOptions().getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(app.getOptions().getConnectTimeout()).isEqualTo(5_000);
            assertThat(app.getOptions().getReadTimeout()).isEqualTo(10_000);
            assertThat(app.getOptions().getWriteTimeout()).isEqualTo(10_000);
            assertThat(context.getBean(FirebaseMessaging.class)).isSameAs(FirebaseMessaging.getInstance(app));
            assertThat(FirebaseApp.getApps()).contains(app);
        });
        assertThat(registered.get()).isNotNull();
        assertThat(FirebaseApp.getApps()).doesNotContain(registered.get());

        // Um novo contexto deve conseguir usar o mesmo nome apos o encerramento anterior.
        configured(credentials(PROJECT_ID)).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void loadsTheDeploymentVariablesThroughApplicationYaml() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.config.import=", "NOTIFY_PUSH_ENABLED=true",
                        "FIREBASE_PROJECT_ID=" + PROJECT_ID,
                        "FIREBASE_CREDENTIALS_BASE64=" + credentials(PROJECT_ID))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FirebaseMessaging.class);
                    assertThat(context.getBean(FirebaseApp.class).getOptions().getProjectId()).isEqualTo(PROJECT_ID);
                });
    }

    @Test
    void loadsCredentialsFromTheDiscloudEnvFile(@TempDir Path directory) throws IOException {
        Path envFile = directory.resolve(".env");
        Files.writeString(envFile, "NOTIFY_PUSH_ENABLED=true\nFIREBASE_PROJECT_ID=" + PROJECT_ID
                + "\nFIREBASE_CREDENTIALS_BASE64=" + credentials(PROJECT_ID) + "\n", StandardCharsets.UTF_8);

        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.config.import=optional:" + envFile.toUri() + "[.properties]")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FirebaseMessaging.class);
                    assertThat(context.getBean(FirebaseApp.class).getOptions().getProjectId()).isEqualTo(PROJECT_ID);
                });
    }

    @Test
    void rejectsAMissingProjectIdWhenEnabled() {
        runner.withPropertyValues("notify.push.enabled=true",
                        "notify.push.credentials-base64=" + credentials(PROJECT_ID))
                .run(context -> assertFailure(context.getStartupFailure(),
                        "Configure FIREBASE_PROJECT_ID e FIREBASE_CREDENTIALS_BASE64 para habilitar o Firebase."));
    }

    @Test
    void rejectsMissingCredentialsWhenEnabled() {
        runner.withPropertyValues("notify.push.enabled=true", "notify.push.project-id=" + PROJECT_ID)
                .run(context -> assertFailure(context.getStartupFailure(),
                        "Configure FIREBASE_PROJECT_ID e FIREBASE_CREDENTIALS_BASE64 para habilitar o Firebase."));
    }

    @Test
    void rejectsInvalidBase64WithoutExposingItsValue() {
        configured("invalid-secret!")
                .run(context -> assertFailure(context.getStartupFailure(), INVALID_CREDENTIAL_MESSAGE));
    }

    @Test
    void rejectsMalformedJsonWithoutExposingItsValue() {
        configured(encode("{\"private_key\":\"secret-value-that-must-not-be-logged"))
                .run(context -> assertFailure(context.getStartupFailure(), INVALID_CREDENTIAL_MESSAGE));
    }

    @Test
    void rejectsTheAndroidConfigurationFile() {
        configured(encode("{\"project_info\":{\"project_id\":\"notify-test\"}}"))
                .run(context -> assertFailure(context.getStartupFailure(), INVALID_CREDENTIAL_MESSAGE));
    }

    @Test
    void rejectsAnInvalidPrivateKey() {
        String json = """
                {"type":"service_account","project_id":"notify-test","client_id":"1234567890",
                 "client_email":"notify-test@notify-test.iam.gserviceaccount.com",
                 "private_key_id":"test-key","private_key":"secret-invalid-key"}
                """;
        configured(encode(json))
                .run(context -> assertFailure(context.getStartupFailure(), INVALID_CREDENTIAL_MESSAGE));
    }

    @Test
    void rejectsCredentialsFromAnotherProject() {
        configured(credentials("another-project"))
                .run(context -> assertFailure(context.getStartupFailure(),
                        "FIREBASE_PROJECT_ID deve corresponder ao projeto da credencial."));
    }

    private ApplicationContextRunner configured(String encodedCredentials) {
        return runner.withPropertyValues("notify.push.enabled=true", "notify.push.project-id=" + PROJECT_ID,
                "notify.push.credentials-base64=" + encodedCredentials);
    }

    private static String credentials(String projectId) {
        String json = """
                {"type":"service_account","project_id":"%s","client_id":"1234567890",
                 "client_email":"notify-test@notify-test.iam.gserviceaccount.com",
                 "private_key_id":"test-key",
                 "private_key":"-----BEGIN PRIVATE KEY-----\\n%s\\n-----END PRIVATE KEY-----\\n",
                 "token_uri":"https://oauth2.googleapis.com/token"}
                """.formatted(projectId, testPrivateKey);
        return encode(json);
    }

    private static String encode(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertFailure(Throwable failure, String message) {
        assertThat(failure).isNotNull();
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertThat(root).isInstanceOf(IllegalStateException.class).hasMessage(message).hasNoCause();
    }
}
