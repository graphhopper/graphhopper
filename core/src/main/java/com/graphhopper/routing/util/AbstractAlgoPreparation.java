/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
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
public abstract class AbstractAlgoPreparation<T extends AlgorithmPreparation> implements AlgorithmPreparation
{
    protected Graph _graph;
    private boolean prepared = false;

    @Override
    public AlgorithmPreparation setGraph( Graph g )
    {
        _graph = g;
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T doWork()
    {
        if (prepared)
            throw new IllegalStateException("Call doWork only once!");

        prepared = true;
        // no operation        
        return (T) this;
    }

    @Override
    public boolean isPrepared()
    {
        return prepared;
    }
}
