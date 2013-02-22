/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.util;

import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.Helper;

/**
 * @author Peter Karich
 */
public abstract class NoOpAlgorithmPreparation extends AbstractAlgoPreparation<NoOpAlgorithmPreparation> {

    public NoOpAlgorithmPreparation() {
    }

    /**
     * Creates a preparation wrapper for the specified algorithm. Warning/TODO:
     * set the _graph for the instance otherwise you'll get NPE when calling
     * createAlgo. Possible values for algorithmStr: astar (A* algorithm),
     * astarbi (bidirectional A*) dijkstra (Dijkstra), dijkstrabi and
     * dijkstraNative (a bit faster bidirectional Dijkstra).
     */
    public static AlgorithmPreparation createAlgoPrepare(Graph g, final String algorithmStr) {
        return p(g, Helper.getAlgoFromString(algorithmStr));
    }

    public static AlgorithmPreparation createAlgoPrepare(final String algorithmStr) {
        return p(Helper.getAlgoFromString(algorithmStr));
    }

    public static AlgorithmPreparation p(Graph g, final Class<? extends RoutingAlgorithm> a) {
        return p(a).graph(g);
    }

    public static AlgorithmPreparation p(final Class<? extends RoutingAlgorithm> a) {
        return new NoOpAlgorithmPreparation() {
            @Override public RoutingAlgorithm createAlgo() {
                try {
                    return a.getConstructor(Graph.class).newInstance(_graph);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        };
    }
}
