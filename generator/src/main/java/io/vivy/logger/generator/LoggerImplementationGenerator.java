package io.vivy.logger.generator;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
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
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class LoggerImplementationGenerator extends AbstractProcessor {

    private static final Logger LOGGER = Logger.getLogger(LoggerImplementationGenerator.class.toString());

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
                roundEnv.getElementsAnnotatedWith(annotation)
                        .forEach(element -> {

                            ClassName loggerClassName = ClassName.get(processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString(), "MDCLogger");
                            Class<org.slf4j.Logger> slf4jLoggerClass = org.slf4j.Logger.class;

                            TypeSpec.Builder logger = TypeSpec.classBuilder(loggerClassName)
                                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                                    .addSuperinterface(ClassName.get(loggerClassName.packageName(), "ContextLogger"))
                                    .addField(FieldSpec.builder(slf4jLoggerClass, "logger", Modifier.PRIVATE, Modifier.FINAL).build())
                                    .addMethod(
                                            MethodSpec.constructorBuilder()
                                                    .addModifiers(Modifier.PUBLIC)
                                                    .addParameter(slf4jLoggerClass, "logger")
                                                    .addStatement("this.$N = $N", "logger", "logger")
                                                    .build()
                                    );

                            // all `with` methods
                            for (int i = 1; i <= 5; i++) {
                                MethodSpec.Builder with = MethodSpec.methodBuilder("with")
                                        .addModifiers(Modifier.PUBLIC)
                                        .returns(slf4jLoggerClass);

                                // key-value pairs
                                for (int c = 1; c <= i; c++) {
                                    with
                                            .addParameter(String.class, "key" + c)
                                            .addParameter(Object.class, "value" + c)
                                            .addStatement("$1T.put(key$3L, value$3L instanceof $2T ? ($2T) value$3L : $2T.valueOf(value$3L))", MDC.class, String.class, c);
                                }

                                logger.addMethod(
                                        with
                                                .addStatement("return this")
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
                                    .map(it -> (ExecutableElement) it)
                                    .map(it -> {
                                        MethodSpec.Builder overriding = MethodSpec.overriding(it);

                                        String args = it.getParameters().stream().map(VariableElement::getSimpleName).collect(joining(", "));

                                        if (it.getReturnType().getKind() != TypeKind.VOID) {
                                            overriding.addStatement("return logger.$L($L)", it.getSimpleName(), args);
                                        } else {
                                            overriding.addCode("try {")
                                                    .addStatement("logger.$L($L)", it.getSimpleName(), args)
                                                    .addCode("} finally {")
                                                    .addStatement("$T.clear()", MDC.class)
                                                    .addCode("}");
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
        }

        return false;
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
        public ProcessingException(String message, String simpleName, JavaFileObject.Kind kind, Exception cause) {
            super(String.format("%s: %s (%s)", message, simpleName, kind), cause);
        }
    }
}
