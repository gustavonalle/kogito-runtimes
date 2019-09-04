package org.kie.kogito.rules.tests;

import java.util.List;
import java.util.Map;

import org.kie.api.runtime.KieSession;
import org.kie.kogito.rules.RuleUnit;
import org.kie.kogito.rules.RuleUnitInstance;
import org.kie.kogito.rules.RuleUnitMemory;

public class ReflectiveRuleUnitInstance<T extends RuleUnitMemory> extends org.drools.core.ruleunit.impl.AbstractRuleUnitInstance<T> {


    ReflectiveRuleUnitInstance(RuleUnit<T> unit, T workingMemory,  KieSession ksession) {
        super(unit, workingMemory, ksession);
    }

}
