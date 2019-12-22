/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
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
package com.graphhopper.storage;

/**
 * Manages in-memory DataAccess objects.
 * <p>
 *
 * @author Peter Karich
 * @see RAMDataAccess
 * @see RAMIntDataAccess
 */
public class RAMDirectory extends GHDirectory {
    public RAMDirectory() {
        this("", false);
    }

    public RAMDirectory(String location) {
        this(location, false);
    }

    /**
     * @param store true if you want that the RAMDirectory can be loaded or saved on demand, false
     *              if it should be entirely in RAM
     */
    public RAMDirectory(String _location, boolean store) {
        super(_location, store ? DAType.RAM_STORE : DAType.RAM);
    }
}
