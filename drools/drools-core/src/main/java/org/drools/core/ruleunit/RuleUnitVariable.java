package org.drools.core.ruleunit;

import static org.drools.core.util.StringUtils.capitalize;
import static org.drools.reflective.util.ClassUtils.convertFromPrimitiveType;

public final class RuleUnitVariable {

    private final String name;
    private final Class<?> type;
    private final Class<?> dataSourceParameterType;
    private final Class<?> boxedVarType;
    private final String getter;

    public RuleUnitVariable(String name, Class<?> type, Class<?> dataSourceParameterType) {
        this.name = name;
        this.getter = "get" + capitalize(name);
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

    public String getter() {
        return getter;
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