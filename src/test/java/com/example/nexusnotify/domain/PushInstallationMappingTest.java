package com.example.nexusnotify.domain;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PushInstallationMappingTest {

    private MongoMappingContext context;
    private MappingMongoConverter converter;

    @BeforeEach
    void setUp() {
        MongoCustomConversions conversions = MongoCustomConversions.create(adapter -> {});
        context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.setInitialEntitySet(Set.of(PushInstallation.class));
        context.afterPropertiesSet();
        converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
    }

    @Test
    void mapsTheRevokedDocumentWithANullProfileAndMongoDates() {
        ObjectId id = new ObjectId();
        Instant created = Instant.parse("2026-10-15T18:00:00Z");
        Instant revoked = created.plusSeconds(3600);
        Document document = new Document("_id", id).append("profile_id", null)
                .append("installation_id", "fid-one").append("status", "REVOKED")
                .append("created_at", Date.from(created)).append("updated_at", Date.from(revoked))
                .append("last_seen_at", Date.from(created)).append("disabled_at", Date.from(revoked))
                .append("disabled_reason", "LOGOUT");

        PushInstallation installation = converter.read(PushInstallation.class, document);

        assertThat(installation.id()).isEqualTo(id);
        assertThat(installation.profileId()).isNull();
        assertThat(installation.status()).isEqualTo(PushInstallationStatus.REVOKED);
        assertThat(installation.createdAt()).isEqualTo(created);
        assertThat(installation.lastSeenAt()).isEqualTo(created);
        assertThat(installation.updatedAt()).isEqualTo(revoked);
        assertThat(installation.disabledAt()).isEqualTo(revoked);
        assertThat(installation.disabledReason()).isEqualTo("LOGOUT");
        Document written = new Document();
        converter.write(installation, written);
        assertThat(written).containsEntry("installation_id", "fid-one")
                .containsEntry("disabled_reason", "LOGOUT").containsEntry("disabled_at", Date.from(revoked))
                .doesNotContainKeys("platform", "installationId", "profileId", "lastSeenAt");
    }

    @Test
    void mapsTheCollectionWithoutDeclaringIndexesOwnedByNexusDb() {
        MongoPersistentEntityIndexResolver resolver = new MongoPersistentEntityIndexResolver(context);

        assertThat(context.getRequiredPersistentEntity(PushInstallation.class).getCollection())
                .isEqualTo("push_installations");
        assertThat(resolver.resolveIndexFor(PushInstallation.class)).isEmpty();
    }
}
