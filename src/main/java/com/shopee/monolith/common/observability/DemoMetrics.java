package com.shopee.monolith.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Demo observability counters (Task 7).
 * Exposed via Actuator/Prometheus: AI fallback rate, search degraded responses,
 * duplicate payment webhooks, scheduler throughput and indexing failures.
 */
@Component
public class DemoMetrics {

    private final Counter aiFallback;
    private final Counter searchDegraded;
    private final Counter webhookDuplicate;
    private final Counter indexingFailure;
    private final MeterRegistry registry;

    public DemoMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.aiFallback = Counter.builder("app.ai.fallback")
                .description("AI provider unavailable — deterministic fallback served")
                .register(registry);
        this.searchDegraded = Counter.builder("app.search.degraded")
                .description("Search served a degraded response (ES down → DB fallback)")
                .register(registry);
        this.webhookDuplicate = Counter.builder("app.payment.webhook.duplicate")
                .description("Duplicate payment webhook events ignored idempotently")
                .register(registry);
        this.indexingFailure = Counter.builder("app.indexing.failure")
                .description("Search/embedding index refresh failures")
                .register(registry);
    }

    public void incrementAiFallback() {
        aiFallback.increment();
    }

    public void incrementSearchDegraded() {
        searchDegraded.increment();
    }

    public void incrementWebhookDuplicate() {
        webhookDuplicate.increment();
    }

    public void incrementIndexingFailure() {
        indexingFailure.increment();
    }

    public void incrementSchedulerProcessed(String scheduler, int count) {
        if (count <= 0) {
            return;
        }
        registry.counter("app.scheduler.processed", "scheduler", scheduler).increment(count);
    }
}
