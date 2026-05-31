package com.shopee.monolith;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for Integration Tests that do not require search index capabilities.
 * Starts PostgreSQL and Redis only, while disabling Elasticsearch auto-configurations
 * and repository scanning to optimize startup speed and avoid timeout issues.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.autoconfigure.exclude=" +
                "org.springframework.boot.data.elasticsearch.autoconfigure.DataElasticsearchAutoConfiguration," +
                "org.springframework.boot.data.elasticsearch.autoconfigure.DataElasticsearchRepositoriesAutoConfiguration," +
                "org.springframework.boot.data.elasticsearch.autoconfigure.DataElasticsearchReactiveRepositoriesAutoConfiguration," +
                "org.springframework.boot.data.elasticsearch.autoconfigure.health.DataElasticsearchReactiveHealthContributorAutoConfiguration," +
                "org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchClientAutoConfiguration," +
                "org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchRestClientAutoConfiguration," +
                "org.springframework.boot.elasticsearch.autoconfigure.health.ElasticsearchRestHealthContributorAutoConfiguration",
                "spring.data.elasticsearch.repositories.enabled=false"
        }
)
@ActiveProfiles("test")
public abstract class BasePostgresRedisIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("shopee_db_test")
            .withUsername("test")
            .withPassword("test");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }
}
