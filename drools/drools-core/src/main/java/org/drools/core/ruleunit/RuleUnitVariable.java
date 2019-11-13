package org.drools.core.ruleunit;

public final class RuleUnitVariable {

    private final String name;
    private final Class<?> type;
    private final Class<?> dataSourceType;

    public RuleUnitVariable(String name, Class<?> type) {
        this.name = name;
        this.type = type;
        this.dataSourceType = null;
    }

    public RuleUnitVariable(String name, Class<?> type, Class<?> dataSourceType) {
        this.name = name;
        this.type = type;
        this.dataSourceType = dataSourceType;
    }

    public boolean isDataSource() {
        return dataSourceType != null;
    }

    public String getName() {
        return name;
    }

    public Class<?> getType() {
        return type;
    }

    public Class<?> getDataSourceType() {
        return dataSourceType;
    }
}