package io.vivy.logger.generator;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import io.vivy.logger.generator.annotations.GenerateContextLogger;
import org.slf4j.MDC;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ContextLoggerImplementationGenerator extends AbstractProcessor {

    private static final Logger LOGGER = Logger.getLogger(ContextLoggerImplementationGenerator.class.toString());

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return new HashSet<>(Collections.singletonList(GenerateContextLogger.class.getName()));
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {

            if (roundEnv.processingOver()) {
                return false;
            }

            if (annotations.isEmpty()) {
                LOGGER.fine("Not annotated with " + GenerateContextLogger.class);
                return false;
            }

            for (TypeElement annotation : annotations) {
                roundEnv.getElementsAnnotatedWith(annotation).forEach(element -> {
                    ClassName loggerClassName = ClassName.get(processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString(), "MDCLogger");
                    ClassName contextLogger = ClassName.get(loggerClassName.packageName(), "ContextLogger");
                    Class<org.slf4j.Logger> slf4jLoggerClass = org.slf4j.Logger.class;

                    TypeSpec.Builder logger = TypeSpec.classBuilder(loggerClassName)
                            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                            .addSuperinterface(contextLogger)
                            .addField(FieldSpec.builder(slf4jLoggerClass, "logger", Modifier.PRIVATE, Modifier.FINAL).build())
                            .addField(FieldSpec.builder(ParameterizedTypeName.get(Map.class, String.class, String.class), "context", Modifier.PRIVATE, Modifier.FINAL).build())
                            .addMethod(
                                    MethodSpec.constructorBuilder()
                                            .addModifiers(Modifier.PUBLIC)
                                            .addParameter(slf4jLoggerClass, "logger")
                                            .addStatement("this(logger, $T.emptyMap())", Collections.class)
                                            .build()
                            )
                            .addMethod(
                                    MethodSpec.constructorBuilder()
                                            .addModifiers(Modifier.PRIVATE)
                                            .addParameter(slf4jLoggerClass, "logger")
                                            .addParameter(ParameterizedTypeName.get(Map.class, String.class, String.class), "context")
                                            .addStatement("this.logger = logger")
                                            .addStatement("this.context = context")
                                            .build()
                            );

                    // all `with` methods
                    for (int i = 1; i <= 5; i++) {
                        MethodSpec.Builder with = MethodSpec.methodBuilder("with")
                                .addAnnotation(Override.class)
                                .addModifiers(Modifier.PUBLIC)
                                .returns(contextLogger)
                                .addStatement("$T ctx = new $T<>(context)", ParameterizedTypeName.get(Map.class, String.class, String.class), HashMap.class);

                        // key-value pairs
                        for (int c = 1; c <= i; c++) {
                            with
                                    .addParameter(String.class, "key" + c)
                                    .addParameter(Object.class, "value" + c)
                                    .addStatement("ctx.put(key$2L, value$2L instanceof $1T ? ($1T) value$2L : $1T.valueOf(value$2L))", String.class, c);
                        }

                        logger.addMethod(
                                with
                                        .addStatement("return new $T(logger, ctx)", loggerClassName)
                                        .build()
                        );
                    }


                    // delegation
                    processingEnv.getElementUtils().getAllMembers((TypeElement) element)
                            .stream()
                            .filter(it -> it.getKind() == ElementKind.METHOD)
                            .filter(it -> !it.getModifiers().contains(Modifier.STATIC))
                            .filter(it -> !it.getModifiers().contains(Modifier.FINAL))
                            .filter(it -> !it.getModifiers().contains(Modifier.NATIVE))
                            .filter(it -> !it.getEnclosingElement().equals(element))
                            .map(ExecutableElement.class::cast)
                            .map(it -> {
                                MethodSpec.Builder overriding = MethodSpec.overriding(it);

                                String args = it.getParameters().stream().map(VariableElement::getSimpleName).collect(joining(", "));

                                if (it.getReturnType().getKind() != TypeKind.VOID) {
                                    overriding.addStatement("return logger.$L($L)", it.getSimpleName(), args);
                                } else {
                                    if (asList("trace", "debug", "info", "warn", "error").contains(it.getSimpleName().toString())) {
                                        overriding.addCode("if (!is$LEnabled()) { return; }\n", capitalizeName(it));
                                    }


                                    overriding
                                            .addCode("for ($T entry : context.entrySet()) {\n  ", ParameterizedTypeName.get(Map.Entry.class, String.class, String.class))
                                            .addStatement("$T.put(entry.getKey(), entry.getValue())", MDC.class)
                                            .addCode("}\n")

                                            .addCode("try {\n  ")
                                            .addStatement("logger.$L($L)", it.getSimpleName(), args)
                                            .addCode("} finally {\n  ")

                                            .addCode("for ($T key : context.keySet()) {\n    ", String.class)
                                            .addStatement("$T.remove(key)", MDC.class)
                                            .addCode("  }\n")
                                            .addCode("}\n");
                                }

                                return overriding.build();
                            })
                            .forEach(logger::addMethod);

                    JavaFile build = JavaFile
                            .builder(
                                    loggerClassName.packageName(),
                                    logger.build()
                            )
                            .build();

                    write(processingEnv).accept(build);
                });
            }

            LOGGER.info("All classes were successfully processed!");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, format("Can't generate, because of: %s", e.getMessage()), e);
            throw new ProcessingException("Can't generate logger implementation", e);
        }

        return false;
    }

    private static String capitalizeName(ExecutableElement it) {
        return String.valueOf(it.getSimpleName().charAt(0)).toUpperCase() + it.getSimpleName().toString().substring(1);
    }


    /**
     * Consumer which writes classes as java files
     */
    private static Consumer<JavaFile> write(ProcessingEnvironment processingEnv) {
        return file -> {
            try {
                file.writeTo(processingEnv.getFiler());
            } catch (IOException e) {
                JavaFileObject obj = file.toJavaFileObject();
                throw new ProcessingException("Can't write", obj.getName(), obj.getKind(), e);
            }
        };
    }

    public static class ProcessingException extends RuntimeException {
        public ProcessingException(String message, Exception cause) {
            super(message, cause);
        }

        public ProcessingException(String message, String simpleName, JavaFileObject.Kind kind, Exception cause) {
            super(String.format("%s: %s (%s)", message, simpleName, kind), cause);
        }
    }
}
