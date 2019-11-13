package org.drools.core.ruleunit;

import static org.drools.reflective.util.ClassUtils.convertFromPrimitiveType;

public final class RuleUnitVariable {

    private final String name;
    private final Class<?> type;
    private final Class<?> dataSourceParameterType;
    private final Class<?> boxedVarType;

    public RuleUnitVariable(String name, Class<?> type, Class<?> dataSourceParameterType) {
        this.name = name;
        this.type = type;
        this.dataSourceParameterType = dataSourceParameterType;
        this.boxedVarType = convertFromPrimitiveType(type);
    }

    public RuleUnitVariable(String name, Class<?> type) {
        this(name, type, null);
    }

    public boolean isDataSource() {
        return dataSourceParameterType != null;
    }

    public String getName() {
        return name;
    }

    public Class<?> getType() {
        return type;
    }

    public Class<?> getDataSourceParameterType() {
        return dataSourceParameterType;
    }

    public Class<?> getBoxedVarType() {
        return boxedVarType;
    }
}