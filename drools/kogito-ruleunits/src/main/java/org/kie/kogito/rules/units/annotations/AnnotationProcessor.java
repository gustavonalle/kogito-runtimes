package org.kie.kogito.codegen.rules.annotations;

import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import org.kie.kogito.rules.units.When;

@SupportedAnnotationTypes("org.kie.kogito.rules.units.When")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class AnnotationProcessor extends AbstractProcessor {

    private Elements utils;
    private Filer filer;
    private Messager logger;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        this.utils = processingEnvironment.getElementUtils();
        this.filer = processingEnvironment.getFiler();
        this.logger = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        logger.printMessage(Diagnostic.Kind.NOTE,
                            "Annotation processing: " + annotations);

        Set<String> classes = new HashSet<>();

        for (Element el : roundEnv.getElementsAnnotatedWith(When.class)) {
            String packageName = utils.getPackageOf(el).getQualifiedName().toString();
            if (el.getKind() == ElementKind.METHOD) {
                Element enclosingElement = el.getEnclosingElement();
                if (enclosingElement.getKind() == ElementKind.CLASS) {
                    String name = utils.getBinaryName((TypeElement) enclosingElement)
                            .toString();
                    classes.add(name);
                    logger.printMessage(Diagnostic.Kind.NOTE, name);
                } else {
                    logger.printMessage(Diagnostic.Kind.ERROR, "Only top-level methods can be annotated with When: " + el.getSimpleName());
                }
            }
        }

        try {
            FileObject codeRuleUnits =
                    filer.createResource(
                            StandardLocation.CLASS_OUTPUT, "", "CodeRuleUnits");

            Writer writer = codeRuleUnits.openWriter();
            for (String className : classes) {
                writer.append(className);
                writer.append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }
}