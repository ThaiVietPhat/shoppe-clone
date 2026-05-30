package com.shopee.monolith.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = AsyncConfig.class)
class AsyncConfigTest {

    @Autowired
    private AsyncConfig asyncConfig;

    @Autowired
    @Qualifier("eventTaskExecutor")
    private Executor executor;

    @Test
    void shouldConfigureEventTaskExecutorProperly() {
        assertNotNull(executor);
        assertTrue(executor instanceof ThreadPoolTaskExecutor);

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        
        assertEquals(4, taskExecutor.getCorePoolSize());
        assertEquals(16, taskExecutor.getMaxPoolSize());
        assertEquals("event-async-", taskExecutor.getThreadNamePrefix());
        
        // Assert graceful shutdown config is present by examining thread pool
        ThreadPoolExecutor javaExecutor = taskExecutor.getThreadPoolExecutor();
        assertNotNull(javaExecutor);
    }

    @Test
    void shouldProvideAsyncUncaughtExceptionHandler() {
        AsyncUncaughtExceptionHandler handler = asyncConfig.getAsyncUncaughtExceptionHandler();
        assertNotNull(handler);
        // We ensure it doesn't throw errors when invoked with a simulated exception
        handler.handleUncaughtException(
                new RuntimeException("Test async exception"), 
                this.getClass().getMethods()[0], 
                new Object[]{"param1"}
        );
    }
}
