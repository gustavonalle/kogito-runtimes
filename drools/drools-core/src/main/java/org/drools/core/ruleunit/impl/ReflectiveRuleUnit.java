package org.kie.kogito.rules.tests;

import java.io.InputStream;

import org.drools.beliefs.bayes.integration.GardenRules;
import org.drools.core.impl.InternalKnowledgeBase;
import org.drools.core.impl.KnowledgeBaseFactory;
import org.drools.core.io.impl.InputStreamResource;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.kogito.rules.RuleUnit;
import org.kie.kogito.rules.RuleUnitInstance;
import org.kie.kogito.rules.RuleUnitMemory;
import org.kie.kogito.rules.impl.AbstractRuleUnit;

public class ReflectiveRuleUnit<T extends RuleUnitMemory> extends AbstractRuleUnit<T> {

    public static <T extends RuleUnitMemory> RuleUnit<T> of(Class<T> gardenRulesClass) {
        return new ReflectiveRuleUnit<>();
    }

    private ReflectiveRuleUnit() {
        super(null);
    }

    @Override
    public RuleUnitInstance<T> createInstance(T workingMemory) {
        KnowledgeBuilder kBuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        Class<? extends RuleUnitMemory> wmClass = workingMemory.getClass();
        String canonicalName = wmClass.getCanonicalName();

        // transform foo.bar.Baz to /foo/bar/Baz.drl
        InputStream resourceAsStream = wmClass.getResourceAsStream(String.format("/%s.drl", canonicalName.replace('.', '/')));
        kBuilder.add(new InputStreamResource(resourceAsStream), ResourceType.DRL);

        InternalKnowledgeBase kBase = KnowledgeBaseFactory.newKnowledgeBase();
        kBase.addPackages( kBuilder.getKnowledgePackages() );
        KieSession kSession = kBase.newKieSession();



        return new ReflectiveRuleUnitInstance<>(this, workingMemory, kSession);
    }


}
