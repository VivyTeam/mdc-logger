package io.vivy.logger;

import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StructuredLoggingTest {
    private static final Logger log = LoggerFactory.getLogger(StructuredLoggingTest.class);

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @Test
    void shouldWorkWithSimpleContext() {
        ContextLogger.of(log)
                .event("error_happens")
                .with(
                        "string", "something"
                )
                .with(
                        "another", "whateva",
                        "and", "more"
                )
                .with(
                        "and", "moore",
                        "foo", "bar",
                        "kukareku", "kuku"
                )
                .with(
                        "and", "moore",
                        "foo", "bar",
                        "lala", "ololo",
                        "kukareku", "kuku"
                )
                .with(
                        "and", "moore",
                        "foo", "bar",
                        "lala", "ololo",
                        "kukareku", "kuku",
                        "kukarekuruku", "kukuru"
                )
                .info("{}", "argument");
    }

    @Test
    void canUseShortForm() {
        ContextLogger logger = ContextLogger.of(log)
                .event("log_event_happens");

        logger.trace();
        logger.debug();
        logger.info();
        logger.warn();
        logger.warn(new RuntimeException("test exception"));
        logger.error(new RuntimeException("test exception"));
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
        val mock = Mockito.mock(Logger.class);
        when(mock.isErrorEnabled()).thenReturn(true);

        val logger = ContextLogger.of(mock)
                .with(
                        "string", "something",
                        "integer", 12
                );

        AtomicReference<Map<String, String>> map = new AtomicReference<>();
        doAnswer(invocation -> {
            map.set(MDC.getCopyOfContextMap());
            return null;
        }).when(mock).error(anyString(), any(Throwable.class));

        assertThat(MDC.getCopyOfContextMap()).isNull();
        assertThat(map.get()).isNull();

        logger.error("error_logged", new RuntimeException("This is test error!"));

        assertThat(map.get())
                .hasSize(2)
                .containsEntry("string", "something")
                .containsEntry("integer", "12");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void shouldNotCallContextIfLevelDisabled() {
        val mock = Mockito.mock(Logger.class);
        when(mock.isInfoEnabled()).thenReturn(false);

        ContextLogger.of(mock)
                .with(
                        "string", "something",
                        "integer", 12
                )
                .info();

        verify(mock, never()).info(anyString());
    }

    @Test
    void shouldSaveAndEnrichContext() {
        val mock = Mockito.mock(Logger.class);
        when(mock.isInfoEnabled()).thenReturn(true);

        val logger = ContextLogger.of(mock).with("gandalf", "gray");
        val logger2 = logger.with("frodo", "baggins");

        AtomicReference<Map<String, String>> map = new AtomicReference<>();
        doAnswer(invocation -> {
            map.set(MDC.getCopyOfContextMap());
            return null;
        }).when(mock).info(anyString());


        logger.info("hello");

        assertThat(map.get())
                .hasSize(1)
                .containsEntry("gandalf", "gray");

        logger2.info("hello");

        assertThat(map.get())
                .hasSize(2)
                .containsEntry("gandalf", "gray")
                .containsEntry("frodo", "baggins");

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void shouldUseThreadLocalForMDCButInstanceBound() throws InterruptedException {
        val mock = Mockito.mock(Logger.class);
        when(mock.isInfoEnabled()).thenReturn(true);

        ContextLogger.of(mock).with("saruman", "rainbow");
        val logger = ContextLogger.of(mock).with("gandalf", "gray");

        AtomicReference<Map<String, String>> map = new AtomicReference<>();
        doAnswer(invocation -> {
            map.set(MDC.getCopyOfContextMap());
            return null;
        }).when(mock).info(anyString());


        CountDownLatch latch = new CountDownLatch(1);
        Thread hobbitThread = new Thread(() -> {
            logger.with("frodo", "baggins").info("hobbit");
            latch.countDown();

        });
        hobbitThread.setName("separate_thread");
        hobbitThread.start();

        latch.await(1, TimeUnit.SECONDS);
        assertThat(map.get())
                .hasSize(2)
                .containsEntry("frodo", "baggins")
                .containsEntry("gandalf", "gray");

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }


    @Test
    void shouldNotOverrideGlobalMDCInOtherThread() throws InterruptedException {
        val mock = Mockito.mock(Logger.class);
        when(mock.isInfoEnabled()).thenReturn(true);

        MDC.put("saruman", "rainbow");
        MDC.put("gandalf", "gray");
        MDC.put("frodo", "bugins");

        val logger = ContextLogger.of(mock).with("gandalf", "white");

        assertThat(MDC.getCopyOfContextMap())
                .containsEntry("saruman", "rainbow")
                .containsEntry("gandalf", "gray")
                .containsEntry("frodo", "bugins");

        AtomicReference<Map<String, String>> map = new AtomicReference<>();
        doAnswer(invocation -> {
            map.set(MDC.getCopyOfContextMap());
            return null;
        }).when(mock).info(anyString());


        CountDownLatch latch = new CountDownLatch(1);
        Thread hobbitThread = new Thread(() -> {
            logger.with("frodo", "baggins").info("hobbit");
            latch.countDown();

        });
        hobbitThread.setName("separate_thread");
        hobbitThread.start();

        latch.await(1, TimeUnit.SECONDS);

        verify(mock).info(anyString());

        assertThat(map.get())
                .hasSize(2)
                .containsEntry("frodo", "baggins")
                .containsEntry("gandalf", "white");

        assertThat(MDC.getCopyOfContextMap())
                .containsEntry("saruman", "rainbow")
                .containsEntry("gandalf", "gray")
                .containsEntry("frodo", "bugins");
    }

    @Test
    void shouldOverrideGlobalMDC() throws InterruptedException {
        val mock = Mockito.mock(Logger.class);
        when(mock.isInfoEnabled()).thenReturn(true);

        MDC.put("saruman", "rainbow");
        MDC.put("gandalf", "gray");
        MDC.put("frodo", "bugins");

        val logger = ContextLogger.of(mock).with("gandalf", "white");

        assertThat(MDC.getCopyOfContextMap())
                .containsEntry("saruman", "rainbow")
                .containsEntry("gandalf", "gray")
                .containsEntry("frodo", "bugins");

        AtomicReference<Map<String, String>> map = new AtomicReference<>();
        doAnswer(invocation -> {
            map.set(MDC.getCopyOfContextMap());
            return null;
        }).when(mock).info(anyString());


        logger.with("frodo", "baggins").info("hobbit");

        assertThat(map.get())
                .hasSize(3)
                .containsEntry("saruman", "rainbow")
                .containsEntry("frodo", "baggins")
                .containsEntry("gandalf", "white");

        assertThat(MDC.getCopyOfContextMap())
                .doesNotContainKeys("gandalf", "frodo")
                .containsEntry("saruman", "rainbow");
    }
}