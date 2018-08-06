# Context in structured form using MDC

- https://logback.qos.ch/manual/mdc.html

To actually see it, add `%X{}` to pattern.
In spring can be:

`LOG_LEVEL_PATTERN` (or `logging.pattern.level` with Logback) as `logging.pattern.level=%5p %X{}`

## Usage:

```java
 WithContext.of(log)
                .with(
                        "string", "something",
                        "integer", 12
                )
                .error("error_logged", new RuntimeException("This is test error!"));
```
