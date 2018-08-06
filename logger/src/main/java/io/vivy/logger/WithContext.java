package io.vivy.logger;


import io.vivy.logger.generator.annotations.GenerateContextLogger;
import org.slf4j.Logger;

@GenerateContextLogger
public interface WithContext extends Logger {

    static LoggerWithContext of(Logger logger) {
        return new LoggerWithContext(logger);
    }
}
