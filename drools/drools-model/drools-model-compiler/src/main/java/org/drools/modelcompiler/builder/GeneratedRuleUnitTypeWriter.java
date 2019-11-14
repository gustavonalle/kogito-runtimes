/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 *
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.modelcompiler.builder;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import org.drools.core.ruleunit.GeneratedRuleUnitDescription;
import org.drools.core.ruleunit.RuleUnitVariable;
import org.kie.kogito.rules.RuleUnitData;

public class GeneratedRuleUnitTypeWriter {

    private final GeneratedRuleUnitDescription ruleUnitDescription;
    protected final PackageModel pkgModel;

    public GeneratedRuleUnitTypeWriter(GeneratedRuleUnitDescription ruleUnitDescription, PackageModel pkgModel) {
        this.ruleUnitDescription = ruleUnitDescription;
        this.pkgModel = pkgModel;
    }

    public String getSource() {
        return JavaParserCompiler.toPojoSource(
                pkgModel.getName(),
                pkgModel.getImports(),
                pkgModel.getStaticImports(),
                classOrInterfaceDeclaration());
    }

    private ClassOrInterfaceDeclaration classOrInterfaceDeclaration() {
        ClassOrInterfaceDeclaration c =
                new ClassOrInterfaceDeclaration()
                        .setPublic(true)
                        .addImplementedType(RuleUnitData.class.getCanonicalName())
                        .setName(ruleUnitDescription.getSimpleName());

        for (RuleUnitVariable v : ruleUnitDescription.getUnitVarDeclarations()) {
            ClassOrInterfaceType t = new ClassOrInterfaceType()
                    .setName(v.getType().getCanonicalName());
            if (v.isDataSource()) {
                t.setTypeArguments(
                        StaticJavaParser.parseType(
                                v.getDataSourceParameterType().getCanonicalName()));
            }
            FieldDeclaration f = new FieldDeclaration();
            f.getVariables().add(
                    new VariableDeclarator(t, v.getName())
                    .setInitializer("org.kie.kogito.rules.DataSource.createStore()")
            );
            c.addMember(f);
            f.createGetter();
        }

        return c;
    }

    public String getName() {
        return pkgModel.getPathName() + "/" + ruleUnitDescription.getSimpleName() + ".java";
    }

    public String getClassName() {
        return ruleUnitDescription.getRuleUnitName();
    }
}
