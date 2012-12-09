/*
 *  Copyright 2012 Peter Karich 
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
package com.graphhopper.storage;

import java.util.Collection;

/**
 * Maintains a collection of DataAccess objects stored at the same location. E.g. for a GraphStorage
 * we need one DataAccess object for nodes, edges and location2id index.
 *
 * @author Peter Karich
 */
public interface Directory {

    String getLocation();

    /**
     * Tries to find the object with that id if not existent it creates one and associates a name
     * and location with it.
     */
    DataAccess findAttach(String id);

    /**
     * Creates a new DataAccess object with a unique location and the specified id without attaching
     * it.
     */
    DataAccess create(String id);

    void delete(DataAccess da);

    Collection<DataAccess> getAll();
}
