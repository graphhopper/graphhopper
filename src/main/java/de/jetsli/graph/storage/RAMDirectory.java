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
package de.jetsli.graph.storage;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Peter Karich
 */
public class RAMDirectory implements Directory {

    private Map<String, RAMDataAccess> map = new HashMap<String, RAMDataAccess>();

    @Override
    public DataAccess createDataAccess(String name) {
        if (map.containsKey(name))
            throw new IllegalStateException("DataAccess " + name + " already exists");

        RAMDataAccess da = new RAMDataAccess(name);
        map.put(name, da);
        return da;
    }
}
