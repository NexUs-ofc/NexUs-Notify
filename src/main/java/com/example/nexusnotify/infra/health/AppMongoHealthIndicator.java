package com.example.nexusnotify.infra.health;

import com.mongodb.client.MongoClient;
import org.bson.Document;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.mongodb.autoconfigure.MongoProperties;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifica o Mongo consultando apenas o banco da aplicação.
 * O indicador padrão percorre todos os bancos, incluindo o "local",
 * que o Atlas bloqueia mesmo para atlasAdmin ("not authorized on local").
 */
@Component
public class AppMongoHealthIndicator extends AbstractHealthIndicator {

    private final MongoClient mongoClient;

    private final String database;

    public AppMongoHealthIndicator(MongoClient mongoClient, MongoProperties mongoProperties) {
        super("MongoDB health check failed");
        this.mongoClient = mongoClient;
        this.database = mongoProperties.getDatabase();
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        List<String> databases = new ArrayList<>();
        mongoClient.listDatabaseNames().forEach(databases::add);
        Document result = mongoClient.getDatabase(database).runCommand(new Document("hello", 1));
        builder.up().withDetail("databases", databases).withDetail("maxWireVersion",
                result.getInteger("maxWireVersion"));
    }
}
