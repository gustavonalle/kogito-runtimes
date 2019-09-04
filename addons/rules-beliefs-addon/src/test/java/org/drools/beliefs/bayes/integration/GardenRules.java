package org.drools.beliefs.bayes.integration;

import org.drools.beliefs.bayes.BayesModeFactory;
import org.drools.beliefs.bayes.BayesModeFactoryImpl;
import org.kie.kogito.rules.DataSource;
import org.kie.kogito.rules.DataStore;
import org.kie.kogito.rules.RuleUnitMemory;

public class GardenRules implements RuleUnitMemory {

    private final DataStore<String> strings = DataSource.createStore();
    private final DataStore<Garden> gardens = DataSource.createStore();
    private final BayesModeFactory bsFactory = new BayesModeFactoryImpl();

    public BayesModeFactory getBsFactory() {
        return bsFactory;
    }

    public DataStore<String> getStrings() {
        return strings;
    }

    public DataStore<Garden> getGardens() {
        return gardens;
    }
}
