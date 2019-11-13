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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.drools.core.definitions.InternalKnowledgePackage;
import org.drools.core.rule.EntryPointId;
import org.kie.kogito.rules.DataSource;
import org.kie.kogito.rules.RuleUnit;
import org.kie.kogito.rules.RuleUnitData;

import static org.drools.reflective.util.ClassUtils.getter2property;

public class AbstractRuleUnitDescription implements RuleUnitDescription {

    private final Class<? extends RuleUnitData> ruleUnitClass;

    private final Map<String, RuleUnitVariable> varDeclarations = new HashMap<>();


    public AbstractRuleUnitDescription(InternalKnowledgePackage pkg, Class<? extends RuleUnitData> ruleUnitClass) {
        this.ruleUnitClass = ruleUnitClass;
        indexUnitVars();
    }

    @Override
    public Class<? extends RuleUnitData> getRuleUnitClass() {
        return ruleUnitClass;
    }

    @Override
    public String getRuleUnitName() {
        return ruleUnitClass.getName();
    }

    @Override
    public Optional<EntryPointId> getEntryPointId(String name) {
        return Optional.ofNullable(varDeclarations.get(name))
                .filter(RuleUnitVariable::isDataSource)
                .map(ds -> new EntryPointId(name));
    }

    @Override
    public Optional<Class<?>> getDatasourceType(String name) {
        return Optional.ofNullable(varDeclarations.get(name))
                .filter(RuleUnitVariable::isDataSource)
                .map(RuleUnitVariable::getDataSourceType);
    }

    @Override
    public Optional<Class<?>> getVarType(String name) {
        return Optional.ofNullable(varDeclarations.get(name)).map(RuleUnitVariable::getType);
    }

    @Override
    public boolean hasVar(String name) {
        return varDeclarations.containsKey(name);
    }

    @Override
    public Collection<String> getUnitVars() {
        return varDeclarations.keySet();
    }

    @Override
    public Collection<RuleUnitVariable> getUnitVarDeclarations() {
        return varDeclarations.values();
    }

    @Override
    public boolean hasDataSource(String name) {
        RuleUnitVariable ruleUnitVariable = varDeclarations.get(name);
        return ruleUnitVariable != null && ruleUnitVariable.isDataSource();
    }

    private void indexUnitVars() {
        for (Method m : ruleUnitClass.getMethods()) {
            if (m.getDeclaringClass() != RuleUnit.class && m.getParameterCount() == 0) {
                String id = getter2property(m.getName());
                if (id != null && !id.equals("class")) {
                    Class<?> unitVarType = getUnitVarType(m);
                    varDeclarations.put(
                            id,
                            new RuleUnitVariable(m.getName(), m.getReturnType(), unitVarType));
                }
            }
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
