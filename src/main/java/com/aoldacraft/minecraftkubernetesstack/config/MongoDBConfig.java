package com.aoldacraft.minecraftkubernetesstack.config;

import com.mongodb.client.MongoClients;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.TransitionWalker;
import de.flapdoodle.reverse.transitions.Start;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

import java.io.Closeable;
import java.io.IOException;

@Configuration
public class MongoDBConfig implements Closeable {

    @Value("${MONGO_URL:mongodb://root:1234@localhost:27017/}")
    private String mongoUrl;

    @Value("${MONGO_DATABASE_NAME:mks}")
    private String databaseName;

    private TransitionWalker.ReachedState<RunningMongodProcess> emMongod = null;
    private MongoClient emMongoClient = null;

    @Bean
    @Profile("!local")
    public MongoClient mongo() {
        ConnectionString connectionString = new ConnectionString(mongoUrl);
        MongoClientSettings mongoClientSettings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build();

        return MongoClients.create(mongoClientSettings);
    }

    @Bean
    @Profile("!local")
    public MongoTemplate mongoTemplate() {
        return new MongoTemplate(mongo(), databaseName);
    }

    @Bean(name = "mongoTemplate")
    @Profile("local")
    public MongoTemplate embeddedMongoTemplate() {
        final var mongod = Mongod.builder()
                .net(Start.to(Net.class).initializedWith(Net.defaults().withPort(27017)))
                .build();
        final String embeddedMongoUrl = "mongodb://localhost:27017";
        emMongod = mongod.start(Version.V4_4_5);
        emMongoClient = MongoClients.create(embeddedMongoUrl);

        return new MongoTemplate(new SimpleMongoClientDatabaseFactory(emMongoClient, databaseName));
    }

    @Override
    public void close() throws IOException {
        if(emMongoClient == null || emMongod == null) {
            return;
        }
        try {
            emMongoClient.close();
            emMongod.close();

        }
        catch (Exception ignore) {
        }
    }
}
