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

import com.graphhopper.util.Helper;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Peter Karich
 */
public class RAMDirectory implements Directory {

    private Map<String, RAMDataAccess> map = new HashMap<String, RAMDataAccess>();
    private String id;
    private boolean store;

    public RAMDirectory() {
        this("", false);
    }

    public RAMDirectory(String id) {
        this(id, false);
    }

    /**
     * @param store true if you want that the RAMDirectory can be loaded or saved on demand, false
     * if it should be entirely in RAM
     */
    public RAMDirectory(String _id, boolean store) {
        if (_id == null)
            throw new IllegalStateException("id cannot be null! Use empty string for runtime directory");
        this.id = _id;
        this.store = store;
        if (id.isEmpty())
            id = new File("").getAbsolutePath();
        if (!id.endsWith("/"))
            id += "/";

        mkdirs();
    }

    private void mkdirs() {
        if (store) {
            new File(id).mkdirs();
        }
    }

    @Override
    public DataAccess createDataAccess(String name) {
        if (store)
            name = id + name;

        if (map.containsKey(name))
            throw new IllegalStateException("DataAccess " + name + " already exists");

        RAMDataAccess da = new RAMDataAccess(name, store);
        map.put(name, da);
        return da;
    }

//    @Override
//    public void clear() {
//        if (store) {
//            Helper.deleteDir(new File(id));
//            mkdirs();
//        }
//        map.clear();
//    }
    @Override
    public String getLocation() {
        return id;
    }
}
