package io.vivy.logger;


import io.vivy.logger.generator.annotations.GenerateContextLogger;
import org.slf4j.Logger;

@GenerateContextLogger
public interface ContextLogger extends Logger {

    static MDCLogger of(Logger logger) {
        return new MDCLogger(logger);
    }
}
