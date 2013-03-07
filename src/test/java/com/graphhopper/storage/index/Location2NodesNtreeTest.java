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
package com.graphhopper.storage.index;

import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.RAMDirectory;

/**
 *
 * @author Peter Karich
 */
public class Location2NodesNtreeTest extends AbstractLocation2IDIndexTester {

    @Override
    public Location2IDIndex createIndex(Graph g, int resolution) {
        Directory dir = new RAMDirectory(location);
        return new Location2NodesNtree(g, dir).prepareIndex(resolution);
    }

    @Override
    public void testSimpleGraph() {
        // TODO
    }

    @Override
    public void testSimpleGraph2() {
        // TODO
    }

    @Override
    public void testSinglePoints120() {
        // TODO
    }

    @Override
    public void testSinglePoints32() {
        // TODO
    }
}
