package io.vivy.logger;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class StructuredLoggingTest {

    @Test
    public void shouldCallContext() {
        WithContext.of(log)
                .with(
                        "string", "something",
                        "integer", 12
                )
                .error("error_logged", new RuntimeException("This is test error!"));
    }

    @Test
    public void shouldWorkWithSimpleContext() {
        WithContext.of(log)
                .with(
                        "string", "something"
                )
                .info("error_logged {}", "argument");
    }

    @Test
    public void shouldLogWarnLevel() {
        WithContext.of(log)
                .with(
                        "multilined_string", "frodo\ncan\n\rjump\r\nvery far"
                )
                .info("error_logged");
    }

    @Test
    public void shouldWorkWithDebugLevel() {
        WithContext.of(log)
                .with(
                        "string", "something"
                )
                .debug("this_is_debug");
    }

    @Test
    public void shouldWorkWithTraceLevel() {
        WithContext.of(log)
                .with(
                        "string", "something"
                )
                .trace("this_is_trace");
    }
}