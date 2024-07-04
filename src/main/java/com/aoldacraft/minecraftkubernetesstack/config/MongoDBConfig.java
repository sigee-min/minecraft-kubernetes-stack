package com.aoldacraft.minecraftkubernetesstack.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.TransitionWalker;
import de.flapdoodle.reverse.transitions.Start;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

@Configuration
public class MongoDBConfig {

    private static final Logger logger = LoggerFactory.getLogger(MongoDBConfig.class);

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
                .applyToConnectionPoolSettings(builder ->
                        builder.maxConnectionIdleTime(60, TimeUnit.SECONDS)
                                .maxConnectionLifeTime(60, TimeUnit.SECONDS))
                .applyToSocketSettings(builder ->
                        builder.connectTimeout(30, TimeUnit.SECONDS)
                                .readTimeout(30, TimeUnit.SECONDS))
                .applyToClusterSettings(builder ->
                        builder.serverSelectionTimeout(30, TimeUnit.SECONDS))
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
        try {
            int port = 27017;
            if (!isPortAvailable(port)) {
                throw new RuntimeException("Port " + port + " is already in use");
            }

            logger.info("Starting embedded MongoDB on port {}", port);
            final var mongod = Mongod.builder()
                    .net(Start.to(Net.class).initializedWith(Net.defaults().withPort(port)))
                    .build();
            emMongod = mongod.start(Version.V4_4_5);

            final String embeddedMongoUrl = "mongodb://localhost:%s".formatted(port);
            emMongoClient = MongoClients.create(embeddedMongoUrl);

            logger.info("Embedded MongoDB started successfully on port {}", port);
        } catch (Exception e) {
            logger.error("Failed to start embedded MongoDB", e);
            throw new RuntimeException("Failed to start embedded MongoDB", e);
        }

        return new MongoTemplate(new SimpleMongoClientDatabaseFactory(emMongoClient, databaseName));
    }

    @PreDestroy
    public void close() throws IOException {
        if (emMongoClient == null || emMongod == null) {
            return;
        }
        try {
            emMongoClient.close();
            emMongod.close();
            logger.info("Embedded MongoDB closed successfully");
        } catch (Exception e) {
            logger.error("Failed to close embedded MongoDB", e);
        }
    }

    private boolean isPortAvailable(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 200);
            return false;
        } catch (IOException e) {
            return true;
        }
    }
}
