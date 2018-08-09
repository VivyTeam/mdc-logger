package io.vivy.logger;


import io.vivy.logger.generator.annotations.GenerateContextLogger;
import org.slf4j.Logger;

@GenerateContextLogger
public interface ContextLogger extends Logger {

    static ContextLogger of(Logger logger) {
        return new MDCLogger(logger);
    }

    default ContextLogger event(String event) {
        return with("event", event);
    }

    ContextLogger with(String key, Object value);
    ContextLogger with(String k1, Object v1, String k2, Object v2);
    ContextLogger with(
            String k1, Object v1,
            String k2, Object v2,
            String k3, Object v3
    );
    ContextLogger with(
            String k1, Object v1,
            String k2, Object v2,
            String k3, Object v3,
            String k4, Object v4
    );
    ContextLogger with(
            String k1, Object v1,
            String k2, Object v2,
            String k3, Object v3,
            String k4, Object v4,
            String k5, Object v5
    );

    default void trace() {
        trace("");
    }

    default void debug() {
        debug("");
    }

    default void info() {
        info("");
    }

    default void warn() {
        warn("");
    }

    default void warn(Throwable t) {
        warn("", t);
    }

    default void error(Throwable t) {
        error("", t);
    }
}
