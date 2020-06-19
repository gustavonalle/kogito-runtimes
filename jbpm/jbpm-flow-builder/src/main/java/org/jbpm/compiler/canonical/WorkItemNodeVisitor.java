/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.compiler.canonical;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.jbpm.process.core.ParameterDefinition;
import org.jbpm.process.core.Work;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.ruleflow.core.factory.WorkItemNodeFactory;
import org.jbpm.workflow.core.node.DataAssociation;
import org.jbpm.workflow.core.node.WorkItemNode;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;

import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import static java.util.stream.Collectors.joining;
import static org.jbpm.ruleflow.core.factory.WorkItemNodeFactory.METHOD_WORK_NAME;
import static org.jbpm.ruleflow.core.factory.WorkItemNodeFactory.METHOD_WORK_PARAMETER;

public class WorkItemNodeVisitor<T extends WorkItemNode> extends AbstractNodeVisitor<T> {

    private enum ParamType {
        BOOLEAN(Boolean.class.getSimpleName()),
        INTEGER(Integer.class.getSimpleName()),
        FLOAT(Float.class.getSimpleName());

        final String name;

        public String getName() {
            return name;
        }

        ParamType(String name) {
            this.name = name;
        }

        public static ParamType fromString(String name) {
            for(ParamType p : ParamType.values()) {
                if(Objects.equals(p.name, name)) {
                    return p;
                }
            }
            return null;
        }
    }

    private final ClassLoader contextClassLoader;

    public WorkItemNodeVisitor(ClassLoader contextClassLoader) {
        this.contextClassLoader = contextClassLoader;
    }

    @Override
    protected String getNodeKey() {
        return "workItemNode";
    }

    @Override
    public void visitNode(String factoryField, T node, BlockStmt body, VariableScope variableScope, ProcessMetaData metadata) {
        Work work = node.getWork();
        String workName = workItemName(node, metadata);
        body.addStatement(getAssignedFactoryMethod(factoryField, WorkItemNodeFactory.class, getNodeId(node), getNodeKey(), new LongLiteralExpr(node.getId())))
                .addStatement(getNameMethod(node, work.getName()))
                .addStatement(getFactoryMethod(getNodeId(node), METHOD_WORK_NAME, new StringLiteralExpr(workName)));

        addWorkItemParameters(work, body, getNodeId(node));
        addNodeMappings(node, body, getNodeId(node));

        body.addStatement(getDoneMethod(getNodeId(node)));

        visitMetaData(node.getMetaData(), body, getNodeId(node));

        metadata.getWorkItems().add(workName);
    }

    protected void addWorkItemParameters(Work work, BlockStmt body, String variableName) {
        for (Entry<String, Object> entry : work.getParameters().entrySet()) {
            if (entry.getValue() == null) {
                continue; // interfaceImplementationRef ?
            }
            String paramType = null;
            if(work.getParameterDefinition(entry.getKey()) != null) {
                paramType = work.getParameterDefinition(entry.getKey()).getType().getStringType();
            }
            body.addStatement(getFactoryMethod(variableName, METHOD_WORK_PARAMETER, new StringLiteralExpr(entry.getKey()), getParameterExpr(paramType, entry.getValue().toString())));
        }
    }

    private Expression getParameterExpr(String type, String value) {
        ParamType pType = ParamType.fromString(type);
        if (pType == null) {
            return new StringLiteralExpr(value);
        }
        switch (pType) {
            case BOOLEAN:
                return new BooleanLiteralExpr(Boolean.parseBoolean(value));
            case FLOAT:
                return new MethodCallExpr()
                        .setScope(new NameExpr(Float.class.getName()))
                        .setName("parseFloat")
                        .addArgument(new StringLiteralExpr(value));
            case INTEGER:
                return new IntegerLiteralExpr(Integer.parseInt(value));
            default:
                return new StringLiteralExpr(value);
        }
    }

    protected String workItemName(WorkItemNode workItemNode, ProcessMetaData metadata) {
        String workName = workItemNode.getWork().getName();

        if (workName.equals("Service Task")) {
            String interfaceName = (String) workItemNode.getWork().getParameter("Interface");
            String operationName = (String) workItemNode.getWork().getParameter("Operation");
            String type = (String) workItemNode.getWork().getParameter("ParameterType");

            NodeValidator.of(getNodeKey(), workItemNode.getName())
                    .notEmpty("interfaceName", interfaceName)
                    .notEmpty("operationName", operationName)
                    .validate();

            Map<String, String> parameters = null;
            if (type != null) {
                if (isDefaultParameterType(type)) {
                    type = inferParameterType(workItemNode.getName(), interfaceName, operationName, type);
                }

                parameters = Collections.singletonMap("Parameter", type);
            } else {
                parameters = new LinkedHashMap<>();

                for (ParameterDefinition def : workItemNode.getWork().getParameterDefinitions()) {
                    parameters.put(def.getName(), def.getType().getStringType());
                }
            }

            String uniqueHandlerName = mangledHandlerName(interfaceName, operationName, parameters);

            CompilationUnit handlerClass = generateHandlerClassForService(
                    uniqueHandlerName, interfaceName, operationName, parameters, workItemNode.getOutAssociations());

            metadata.getGeneratedHandlers().put(uniqueHandlerName, handlerClass);

            return uniqueHandlerName;
        }

        return workName;
    }

    private String mangledHandlerName(String interfaceName, String operationName, Map<String, String> parameters) {
        String simpleName = interfaceName.substring(interfaceName.lastIndexOf(".") + 1);

        // mangle dotted identifiers foo.bar.Baz into foo$bar$Baz
        // then concatenate the collection with $$
        // e.g. List.of("foo.bar.Baz", "qux.Quux") -> "foo$bar$Baz$$qux$Quux"
        String mangledParameterTypes =
                parameters.values().stream().map(s -> s.replace('.', '$'))
                        .collect(joining("$$"));

        return String.format("%s_%s_%s_Handler", simpleName, operationName, mangledParameterTypes);
    }

    // assume 1 single arg as above
    private String inferParameterType(String nodeName, String interfaceName, String operationName, String defaultType) {
        try {
            Class<?> i = contextClassLoader.loadClass(interfaceName);
            for (Method m : i.getMethods()) {
                if (m.getName().equals(operationName) && m.getParameterCount() == 1) {
                    return m.getParameterTypes()[0].getCanonicalName();
                }
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(MessageFormat.format("Invalid work item \"{0}\": class not found for interfaceName \"{1}\"", nodeName, interfaceName));
        }
        throw new IllegalArgumentException(MessageFormat.format("Invalid work item \"{0}\": could not find a method called \"{1}\" in class \"{2}\"", nodeName, operationName, interfaceName));
    }

    private boolean isDefaultParameterType(String type) {
        return type.equals("java.lang.Object") || type.equals("Object");
    }

    protected CompilationUnit generateHandlerClassForService(String mangledHandlerName, String interfaceName, String operation, Map<String, String> parameters, List<DataAssociation> outAssociations) {
        CompilationUnit compilationUnit = new CompilationUnit("org.kie.kogito.handlers");

        compilationUnit.getTypes().add(classDeclaration(mangledHandlerName, interfaceName, operation, parameters, outAssociations));

        return compilationUnit;
    }

    public ClassOrInterfaceDeclaration classDeclaration(String mangledHandlerName, String interfaceName, String operation, Map<String, String> parameters, List<DataAssociation> outAssociations) {
        ClassOrInterfaceDeclaration cls = new ClassOrInterfaceDeclaration()
                .setName(mangledHandlerName)
                .setModifiers(Modifier.Keyword.PUBLIC)
                .addImplementedType(WorkItemHandler.class.getCanonicalName());
        ClassOrInterfaceType serviceType = new ClassOrInterfaceType(null, interfaceName);
        FieldDeclaration serviceField = new FieldDeclaration()
                .addVariable(new VariableDeclarator(serviceType, "service"));
        cls.addMember(serviceField);

        // executeWorkItem method
        BlockStmt executeWorkItemBody = new BlockStmt();
        MethodDeclaration executeWorkItem = new MethodDeclaration()
                .setModifiers(Modifier.Keyword.PUBLIC)
                .setType(void.class)
                .setName("executeWorkItem")
                .setBody(executeWorkItemBody)
                .addParameter(WorkItem.class.getCanonicalName(), "workItem")
                .addParameter(WorkItemManager.class.getCanonicalName(), "workItemManager");

        MethodCallExpr callService = new MethodCallExpr(new NameExpr("service"), operation);

        for (Entry<String, String> paramEntry : parameters.entrySet()) {
            MethodCallExpr getParamMethod = new MethodCallExpr(new NameExpr("workItem"), "getParameter").addArgument(new StringLiteralExpr(paramEntry.getKey()));
            callService.addArgument(new CastExpr(new ClassOrInterfaceType(null, paramEntry.getValue()), getParamMethod));
        }
        Expression results = null;
        if (outAssociations.isEmpty()) {

            executeWorkItemBody.addStatement(callService);
            results = new NullLiteralExpr();
        } else {
            VariableDeclarationExpr resultField = new VariableDeclarationExpr()
                    .addVariable(new VariableDeclarator(new ClassOrInterfaceType(null, Object.class.getCanonicalName()), "result", callService));

            executeWorkItemBody.addStatement(resultField);

            results = new MethodCallExpr(new NameExpr("java.util.Collections"), "singletonMap")
                    .addArgument(new StringLiteralExpr(outAssociations.get(0).getSources().get(0)))
                    .addArgument(new NameExpr("result"));
        }

        MethodCallExpr completeWorkItem = new MethodCallExpr(new NameExpr("workItemManager"), "completeWorkItem")
                .addArgument(new MethodCallExpr(new NameExpr("workItem"), "getId"))
                .addArgument(results);

        executeWorkItemBody.addStatement(completeWorkItem);

        // abortWorkItem method
        BlockStmt abortWorkItemBody = new BlockStmt();
        MethodDeclaration abortWorkItem = new MethodDeclaration()
                .setModifiers(Modifier.Keyword.PUBLIC)
                .setType(void.class)
                .setName("abortWorkItem")
                .setBody(abortWorkItemBody)
                .addParameter(WorkItem.class.getCanonicalName(), "workItem")
                .addParameter(WorkItemManager.class.getCanonicalName(), "workItemManager");

        // getName method
        MethodDeclaration getName = new MethodDeclaration()
                .setModifiers(Modifier.Keyword.PUBLIC)
                .setType(String.class)
                .setName("getName")
                .setBody(new BlockStmt().addStatement(new ReturnStmt(new StringLiteralExpr(interfaceName + "." + operation))));
        cls.addMember(executeWorkItem)
                .addMember(abortWorkItem)
                .addMember(getName);

        return cls;
    }
}
