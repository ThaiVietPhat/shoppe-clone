package com.shopee.monolith;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for Integration Tests.
 *
 * Starts database, cache, and search engine containers in a static block.
 * Containers persist across all subclasses in the same JVM run.
 * Dynamic properties are registered using @ServiceConnection.
 *
 * Naming convention: integration test files must end with *IT.java.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    // pgvector/pgvector:pg16 bundles the vector extension required by V15 migration.
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("shopee_db_test")
            .withUsername("test")
            .withPassword("test");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.0.1")
                    .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("xpack.security.http.ssl.enabled", "false")
            .withEnv("xpack.security.transport.ssl.enabled", "false")
            .withStartupTimeout(java.time.Duration.ofMinutes(3));

    static {
        POSTGRES.start();
        REDIS.start();
        ELASTICSEARCH.start();
    }

    @DynamicPropertySource
    static void elasticsearchProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", ELASTICSEARCH::getHttpHostAddress);
    }
}
