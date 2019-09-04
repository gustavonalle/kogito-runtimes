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

package org.drools.beliefs.bayes.runtime;

import java.io.InputStream;
import java.util.ArrayList;

import org.drools.beliefs.bayes.BayesInstance;
import org.drools.beliefs.bayes.BayesNetwork;
import org.drools.beliefs.bayes.JunctionTree;
import org.drools.beliefs.bayes.JunctionTreeBuilder;
import org.drools.beliefs.bayes.model.Bif;
import org.drools.beliefs.bayes.model.XmlBifParser;
import org.kie.internal.builder.KnowledgeBuilderError;

public class BayesRuntimeImpl<T> implements BayesRuntime<T> {

    private final JunctionTree junctionTree;

    public static <T> BayesRuntimeImpl<T> of(Class<T> type) {
        // transform foo.bar.Baz to /foo/bar/Baz.xmlbif
        // this currently only works for single files
        InputStream resourceAsStream = type.getResourceAsStream(
                String.format("/%s.xmlbif", type.getCanonicalName().replace('.', '/')));
        return of(resourceAsStream);
    }

    public static <T> BayesRuntimeImpl<T> of(InputStream is) {
        BayesNetwork network;
        JunctionTreeBuilder builder;
        ArrayList<KnowledgeBuilderError> errors = new ArrayList<>();

        Bif bif = XmlBifParser.loadBif(is);
        network = XmlBifParser.buildBayesNetwork(bif);

        builder = new JunctionTreeBuilder(network);

        JunctionTree jtree = builder.build(null,
                                           network.getPackageName(),
                                           network.getName());

        return new BayesRuntimeImpl<T>(jtree);
    }

    public BayesRuntimeImpl(JunctionTree junctionTree) {
        this.junctionTree = junctionTree;
    }

    public BayesInstance<T> createInstance(T data) {
        return new BayesInstance<>(junctionTree, (Class<T>) data.getClass());
    }
}
