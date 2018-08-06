package io.vivy.logger;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class StructuredLoggingTest {

    @Test
    public void shouldCallContext() {
        ContextLogger.of(log)
                .with(
                        "string", "something",
                        "integer", 12
                )
                .error("error_logged", new RuntimeException("This is test error!"));
    }

    @Test
    public void shouldWorkWithSimpleContext() {
        ContextLogger.of(log)
                .with(
                        "string", "something"
                )
                .info("error_logged {}", "argument");
    }

    @Test
    public void shouldLogWarnLevel() {
        ContextLogger.of(log)
                .with(
                        "multilined_string", "frodo\ncan\n\rjump\r\nvery far"
                )
                .info("error_logged");
    }

    @Test
    public void shouldWorkWithDebugLevel() {
        ContextLogger.of(log)
                .with(
                        "string", "something"
                )
                .debug("this_is_debug");
    }

    @Test
    public void shouldWorkWithTraceLevel() {
        ContextLogger.of(log)
                .with(
                        "string", "something"
                )
                .trace("this_is_trace");
    }
}