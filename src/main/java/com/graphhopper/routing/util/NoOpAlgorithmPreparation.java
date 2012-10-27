/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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

import com.graphhopper.storage.Graph;

/**
 * @author Peter Karich
 */
public abstract class NoOpAlgorithmPreparation implements AlgorithmPreparation {

    protected Graph graph;

    public NoOpAlgorithmPreparation() {
    }

    @Override public AlgorithmPreparation setGraph(Graph g) {
        graph = g;
        return this;
    }

    @Override public NoOpAlgorithmPreparation doWork() {
        // no operation
        return this;
    }

    @Override public boolean isPrepared() {
        return true;
    }
}
