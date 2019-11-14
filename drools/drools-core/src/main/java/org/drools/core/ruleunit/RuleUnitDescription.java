package org.drools.core.ruleunit;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.drools.core.rule.EntryPointId;
import org.kie.kogito.rules.RuleUnitData;

public interface RuleUnitDescription {

    String getRuleUnitName();

    String getSimpleName();

    String getPackageName();

    Optional<EntryPointId> getEntryPointId(String name);

    Optional<Class<?>> getDatasourceType(String name);

    Optional<Class<?>> getVarType(String name);

    boolean hasVar(String name);

    Collection<String> getUnitVars();

    Collection<RuleUnitVariable> getUnitVarDeclarations();

    boolean hasDataSource(String name);

    Optional<GeneratedRuleUnitDescription> asGeneratedRuleUnitDescription();
}
