package org.kie.kogito.codegen.rules;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import org.drools.core.io.impl.ByteArrayResource;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceType;
import org.kie.kogito.codegen.GeneratedFile;
import org.kie.kogito.rules.annotations.When;

public class AnnotatedClassPostProcessor {

    private final List<CompilationUnit> annotatedUnits;

    public static AnnotatedClassPostProcessor scan(Stream<Path> files) {
        List<CompilationUnit> annotatedUnits = files
                .peek(System.out::println)
                .map(p -> {
            try {
                return StaticJavaParser.parse(p);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).filter(cu -> cu.findFirst(AnnotationExpr.class, ann -> ann.getNameAsString().endsWith("When")).isPresent())
                .collect(Collectors.toList());

        return new AnnotatedClassPostProcessor(annotatedUnits);
    }

    public AnnotatedClassPostProcessor(List<CompilationUnit> annotatedUnits) {
        this.annotatedUnits = annotatedUnits;
    }

    public List<Resource> generate() {
        return annotatedUnits.stream().map(UnitGenerator::new).map(g -> {
            ByteArrayResource r = new ByteArrayResource(g.generate().getBytes(StandardCharsets.UTF_8));
            r.setSourcePath(g.unitClass.getPackageDeclaration().get().getNameAsString().replace('.', '/') + '/' + g.fileName());
            r.setResourceType(ResourceType.DRL);
            return r;
        }).collect(Collectors.toList());
    }



    static class UnitGenerator {

        private final CompilationUnit unitClass;

        public UnitGenerator(CompilationUnit unitClass) {
            this.unitClass = unitClass;
        }

        String packageName() {
            return unitClass.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse("");
        }

        String fileName() {
            return String.format("%s.drl", unitClass.getPrimaryTypeName().orElse(""));
        }

        String generate() {
            return String.format(

                    "package %s;\n" +
                            "unit %s;\n" +
                            "%s\n",

                    packageName(), // package
                    unitClass.getPrimaryTypeName().get(),
                    unitClass.getPrimaryType().get().getMethods().stream()
                            .filter(m -> m.getParameters().stream().flatMap(p -> p.getAnnotations().stream()).anyMatch(a -> a.getNameAsString().endsWith("When")))
                            .map(this::generateRule).collect(Collectors.joining()));

        }

        String generateRule(MethodDeclaration method) {
            String methodName = method.getName().asString();
            String patterns = method.getParameters().stream()
                    .map(this::formatPattern)
                    .collect(Collectors.joining());

            String methodArgs = method.getParameters().stream()
                    .map(NodeWithSimpleName::getNameAsString)
                    .collect(Collectors.joining(", "));


            return String.format(
                    "rule %s when\n" +
                            "%s" +
                            "then\n" +
                            "  unit.%s(%s);\n" +
                            "end\n",
                    methodName,
                    patterns,
                    methodName,
                    methodArgs);
        }

        private String formatPattern(Parameter el) {
            AnnotationExpr when = el.getAnnotationByName("When").get();
            return String.format(
                    "  %s : %s\n",
                    el.getNameAsString(),
                    when.asSingleMemberAnnotationExpr().getMemberValue().asStringLiteralExpr().getValue());
        }
    }

}
