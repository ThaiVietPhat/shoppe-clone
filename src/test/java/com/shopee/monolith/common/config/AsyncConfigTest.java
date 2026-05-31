package com.shopee.monolith.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = AsyncConfigTest.TestConfig.class)
class AsyncConfigTest {

    @Autowired
    private AsyncConfig asyncConfig;

    @Autowired
    @Qualifier("eventTaskExecutor")
    private Executor executor;

    @Autowired
    private AsyncHelper asyncHelper;

    @Test
    void shouldConfigureEventTaskExecutorProperly() {
        assertNotNull(executor);
        assertTrue(executor instanceof ThreadPoolTaskExecutor);

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertEquals(4, taskExecutor.getCorePoolSize());
        assertEquals(16, taskExecutor.getMaxPoolSize());
        assertEquals("event-async-", taskExecutor.getThreadNamePrefix());
    }

    @Test
    void shouldProvideAsyncUncaughtExceptionHandler() {
        AsyncUncaughtExceptionHandler handler = asyncConfig.getAsyncUncaughtExceptionHandler();
        assertNotNull(handler);
        // Ensure no exception is thrown when logging is invoked
        handler.handleUncaughtException(
                new RuntimeException("Test async exception"),
                this.getClass().getDeclaredMethods()[0],
                new Object[]{"param1"}
        );
    }

    @Test
    void shouldExecuteOnEventTaskExecutorWithCorrectPrefix() throws ExecutionException, InterruptedException {
        // Act
        CompletableFuture<String> future = asyncHelper.getAsyncThreadName();
        String threadName = future.get(); // Waits for async task to complete

        // Assert
        assertTrue(threadName.startsWith("event-async-"), 
                "Task should run on thread with prefix 'event-async-', but was: " + threadName);
    }

    @Configuration
    @EnableAsync
    @Import(AsyncConfig.class)
    static class TestConfig {
        @Bean
        public AsyncHelper asyncHelper() {
            return new AsyncHelper();
        }
    }

    static class AsyncHelper {
        @Async("eventTaskExecutor")
        public CompletableFuture<String> getAsyncThreadName() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }
    }
}
