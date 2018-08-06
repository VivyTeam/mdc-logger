package io.vivy.logger;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@Slf4j
class StructuredLoggingTest {

    @Test
    void shouldWorkWithSimpleContext() {
        ContextLogger.of(log)
                .with(
                        "string", "something"
                )
                .info("error_logged {}", "argument");
    }

    @Test
    void shouldLogWarnLevel() {
        ContextLogger.of(log)
                .with(
                        "multilined_string", "frodo\ncan\n\rjump\r\nvery far"
                )
                .info("error_logged");
    }

    @Test
    void shouldWorkWithDebugLevel() {
        ContextLogger.of(log)
                .with(
                        "string", "something"
                )
                .debug("this_is_debug");
    }

    @Test
    void shouldWorkWithTraceLevel() {
        ContextLogger.of(log)
                .with(
                        "string", "something"
                )
                .trace("this_is_trace");
    }


    @Test
    void shouldCallContext() {
        val logger = ContextLogger.of(log)
                .with(
                        "string", "something",
                        "integer", 12
                );

        assertThat(MDC.getCopyOfContextMap())
                .hasSize(2)
                .containsEntry("string", "something")
                .containsEntry("integer", "12");

        logger.error("error_logged", new RuntimeException("This is test error!"));

        assertThat(MDC.getCopyOfContextMap()).isNull();
    }

    @Test
    void shouldClearContextOnlyOnLogging() {
        ContextLogger.of(log).with("gandalf", "gray");
        val logger = ContextLogger.of(log).with("frodo", "baggins");

        assertThat(MDC.getCopyOfContextMap())
                .hasSize(2)
                .containsEntry("gandalf", "gray")
                .containsEntry("frodo", "baggins");

        logger.info("hello");

        assertThat(MDC.getCopyOfContextMap()).isNull();
    }

    @Test
    void shouldUseThreadLocalForMDC() throws InterruptedException {
        val mock = Mockito.mock(Logger.class);
        ContextLogger.of(mock).with("gandalf", "gray");

        AtomicReference<Map<String, String>> map = new AtomicReference<>();
        doAnswer(invocation -> {
            map.set(MDC.getCopyOfContextMap());
            return null;
        }).when(mock).info(anyString());


        CountDownLatch latch = new CountDownLatch(1);
        Thread hobbitThread = new Thread(() -> {
            ContextLogger.of(mock).with("frodo", "baggins").info("hobbit");
            latch.countDown();

        });
        hobbitThread.setName("separate_thread");
        hobbitThread.start();

        latch.await(1, TimeUnit.SECONDS);
        assertThat(map.get())
                .hasSize(1)
                .containsEntry("frodo", "baggins");

        assertThat(MDC.getCopyOfContextMap())
                .hasSize(1)
                .containsEntry("gandalf", "gray");
    }
}