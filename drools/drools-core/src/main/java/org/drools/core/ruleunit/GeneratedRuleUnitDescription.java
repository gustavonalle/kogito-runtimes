/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.core.ruleunit;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.drools.core.addon.TypeResolver;
import org.kie.kogito.rules.DataSource;
import org.kie.kogito.rules.RuleUnitData;

public class GeneratedRuleUnitDescription extends AbstractRuleUnitDescription {

    private final TypeResolver typeResolver;
    private final String name;
    private final String packageName;
    private final String simpleName;

    public GeneratedRuleUnitDescription(String name, TypeResolver typeResolver) {
        this.typeResolver = typeResolver;
        this.name = name;
        this.simpleName = name.substring(name.lastIndexOf('.') + 1);
        this.packageName = name.substring(0, name.lastIndexOf('.'));
    }

    @Override
    public String getSimpleName() {
        return simpleName;
    }

    @Override
    public String getPackageName() {
        return packageName;
    }

    @Override
    public Optional<GeneratedRuleUnitDescription> asGeneratedRuleUnitDescription() {
        return Optional.of(this);
    }

    @Override
    public String getRuleUnitName() {
        return name;
    }

    public void putSimpleVar(String name, String varTypeFQCN) {
        Class<?> varType = uncheckedLoadClass(varTypeFQCN);
        putSimpleVar(name, varType);
    }

    public void putDatasourceVar(String name, String datasourceTypeFQCN, String datasourceParameterTypeFQCN) {
        putDatasourceVar(
                name,
                uncheckedLoadClass(datasourceTypeFQCN),
                uncheckedLoadClass(datasourceParameterTypeFQCN));
    }

    public void putSimpleVar(String name, Class<?> varType) {
        putRuleUnitVariable(new RuleUnitVariable(name, varType));
    }

    public void putDatasourceVar(String name, Class<?> datasourceType, Class<?> datasourceParameterType) {
        putRuleUnitVariable(new RuleUnitVariable(name, datasourceType, datasourceParameterType));
    }

    private Class<?> uncheckedLoadClass(String fqcn) {
        try {
            return typeResolver.resolveType(fqcn);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private Class<?> getUnitVarType(Method m) {
        Class<?> returnClass = m.getReturnType();
        if (returnClass.isArray()) {
            return returnClass.getComponentType();
        }
        if (DataSource.class.isAssignableFrom(returnClass)) {
            return getParametricType(m);
        }
        if (Iterable.class.isAssignableFrom(returnClass)) {
            return getParametricType(m);
        }
        return returnClass;
    }

    private Class<?> getParametricType(Method m) {
        Type returnType = m.getGenericReturnType();
        return returnType instanceof ParameterizedType ?
                (Class<?>) ((ParameterizedType) returnType).getActualTypeArguments()[0] :
                Object.class;
    }
}
