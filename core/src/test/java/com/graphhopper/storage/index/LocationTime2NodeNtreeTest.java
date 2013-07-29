/*
 * Copyright 2013 Thomas Buerli <tbuerli@student.ethz.ch>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.storage.index;

import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.RAMDirectory;

/**
 *
 * @author Thomas Buerli <tbuerli@student.ethz.ch>
 */
public class LocationTime2NodeNtreeTest extends AbstractLocationTime2IDIndexTest{

    @Override
    public LocationTime2IDIndex createIndex(Graph g, int resolution) {
        return internalCreateIndex(g, 500000);
    }

    public LocationTime2NodeNtree internalCreateIndex(Graph g, int minMeter) {
        Directory dir = new RAMDirectory(location);
        LocationTime2NodeNtree idx = new LocationTime2NodeNtree(g, dir);
        idx.setResolution(minMeter).prepareIndex();
        return idx;
    }

  
}