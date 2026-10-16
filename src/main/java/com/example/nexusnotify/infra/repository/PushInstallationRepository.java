package com.example.nexusnotify.infra.repository;

import com.example.nexusnotify.domain.PushInstallation;
import com.example.nexusnotify.domain.PushInstallationStatus;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;

@Repository
public class PushInstallationRepository {

    private final MongoTemplate template;

    public PushInstallationRepository(MongoTemplate template) {
        this.template = template;
    }

    public PushInstallation register(Integer profileId, String installationId, Instant now) {
        Query query = Query.query(Criteria.where("installationId").is(installationId));
        Update update = new Update()
                .setOnInsert("createdAt", now)
                .set("profileId", profileId)
                .set("status", PushInstallationStatus.ACTIVE)
                .set("updatedAt", now)
                .set("lastSeenAt", now)
                .set("disabledAt", null)
                .set("disabledReason", null);
        PushInstallation installation;
        try {
            installation = template.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options().upsert(true).returnNew(true),
                    PushInstallation.class
            );
        } catch (DuplicateKeyException exception) {
            // Outro cadastro pode ter criado o mesmo FID entre o inicio do upsert e a insercao.
            installation = template.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options().returnNew(true),
                    PushInstallation.class
            );

            if (installation == null) {
                throw exception;
            }
        }

        if (installation == null) {
            throw new IllegalStateException("Nao foi possivel cadastrar a instalacao de push");
        }

        return installation;
    }

    public void revoke(ObjectId id, Integer profileId, Instant now) {
        Query query = Query.query(Criteria.where("id").is(id).and("profileId").is(profileId));
        Update update = new Update()
                .set("profileId", null)
                .set("status", PushInstallationStatus.REVOKED)
                .set("updatedAt", now)
                .set("disabledAt", now)
                .set("disabledReason", "LOGOUT");

        template.updateFirst(query, update, PushInstallation.class);
    }

    public List<PushInstallation> findActiveByProfileId(Integer profileId) {
        Query query = Query.query(
                Criteria.where("profileId").is(profileId)
                        .and("status").is(PushInstallationStatus.ACTIVE)
        );

        return template.find(query, PushInstallation.class);
    }

    public boolean isCurrentActive(PushInstallation installation) {
        return template.exists(activeSnapshot(installation), PushInstallation.class);
    }

    public boolean markInvalid(PushInstallation installation, Instant now) {
        Update update = new Update()
                .set("status", PushInstallationStatus.INVALID)
                .set("updatedAt", now)
                .set("disabledAt", now)
                .set("disabledReason", "FCM_UNREGISTERED");

        return template.updateFirst(activeSnapshot(installation), update, PushInstallation.class)
                .getModifiedCount() > 0;
    }

    private Query activeSnapshot(PushInstallation installation) {
        return Query.query(
                Criteria.where("id").is(installation.id())
                        .and("installationId").is(installation.installationId())
                        .and("profileId").is(installation.profileId())
                        .and("status").is(PushInstallationStatus.ACTIVE)
                        .and("updatedAt").is(installation.updatedAt())
        );
    }
}
