/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.drools.beliefs.bayes.integration;

import org.drools.beliefs.bayes.BayesInstance;
import org.drools.beliefs.bayes.example.GardenUnit;
import org.drools.beliefs.bayes.runtime.BayesRuntime;
import org.drools.beliefs.bayes.runtime.BayesRuntimeImpl;
import org.junit.jupiter.api.Test;
import org.kie.kogito.rules.DataHandle;
import org.kie.kogito.rules.InterpretedRuleUnit;
import org.kie.kogito.rules.RuleUnit;
import org.kie.kogito.rules.RuleUnitInstance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class BayesBeliefSystemTest {

    @Test
    public void testBayes() {
        BayesRuntime<GardenUnit, Garden> bayesRuntime = BayesRuntimeImpl.of(GardenUnit.class, Garden.class);
        BayesInstance<GardenUnit, Garden> instance = bayesRuntime.createInstance(new GardenUnit());
        assertNotNull(  instance );

        GardenRules instanceMemory = new GardenRules();
        RuleUnit<GardenRules> ru = InterpretedRuleUnit.of(GardenRules.class);
        RuleUnitInstance<GardenRules> rui = ru.createInstance(instanceMemory);

        assertTrue(instance.isDecided());
        instance.globalUpdate();
        Garden garden = instance.marginalize();
        assertTrue( garden.isWetGrass() );

        DataHandle fh = instanceMemory.getGardens().add(garden );
        DataHandle fh1 =instanceMemory.getStrings().add( "rule1" );
        rui.fire();
        assertTrue(instance.isDecided());
        instance.globalUpdate(); // rule1 has added evidence, update the bayes network
        garden = instance.marginalize();
        assertTrue(garden.isWetGrass());  // grass was wet before rule1 and continues to be wet


        DataHandle fh2 = instanceMemory.getStrings().add( "rule2" ); // applies 2 logical insertions
        rui.fire();
        assertTrue(instance.isDecided());
        instance.globalUpdate();
        garden = instance.marginalize();
        assertFalse(garden.isWetGrass() );  // new evidence means grass is no longer wet

        DataHandle fh3 = instanceMemory.getStrings().add( "rule3" ); // adds an additional support for the sprinkler, belief set of 2
        rui.fire();
        assertTrue(instance.isDecided());
        instance.globalUpdate();
        garden = instance.marginalize();
        assertFalse(garden.isWetGrass() ); // nothing has changed

        DataHandle fh4 = instanceMemory.getStrings().add( "rule4" ); // rule4 introduces a conflict, and the BayesFact becomes undecided
        rui.fire();

        assertFalse(instance.isDecided());
        try {
            instance.globalUpdate();
            fail( "The BayesFact is undecided, it should throw an exception, as it cannot be updated." );
        } catch ( Exception e ) {
            // this should fail
        }

        instanceMemory.getStrings().remove( fh4 ); // the conflict is resolved, so it should be decided again
        rui.fire();
        assertTrue(instance.isDecided());
        instance.globalUpdate();
        garden = instance.marginalize();
        assertFalse(garden.isWetGrass() );// back to grass is not wet


        instanceMemory.getStrings().remove( fh2 ); // takes the sprinkler belief set back to 1
        rui.fire();
        instance.globalUpdate();
        garden = instance.marginalize();
        assertFalse(garden.isWetGrass() ); // still grass is not wet

        instanceMemory.getStrings().remove( fh3 ); // no sprinkler support now
        rui.fire();
        instance.globalUpdate();
        garden = instance.marginalize();
        assertTrue(garden.isWetGrass()); // grass is wet again
    }

}
