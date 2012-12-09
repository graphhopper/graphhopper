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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * Implements some common methods for the subclasses. Warning/TODO: It does not close, flush or load
 * the underlying DataAccess objects (if one of the methods are called) as there could be
 * dependencies between the objects! It only does the necessary work for the directory itself.
 *
 * @author Peter Karich
 */
public abstract class AbstractDirectory implements Directory {

    protected Map<String, DataAccess> map = new LinkedHashMap<String, DataAccess>();
    protected Map<DataAccess, String> nameMap = new IdentityHashMap<DataAccess, String>();
    protected final String location;
    private int counter = 0;
    private boolean closed = false;

    public AbstractDirectory(String _location) {
        if (_location == null || _location.isEmpty())
            _location = new File("").getAbsolutePath();
        if (!_location.endsWith("/"))
            _location += "/";
        location = _location;
    }

    protected abstract DataAccess create(String id, String location);

    @Override
    public DataAccess findAttach(String id) {
        checkClosed();
        DataAccess da = map.get(id);
        if (da != null)
            return da;

        return attach(create(id));
    }

    @Override
    public DataAccess attach(DataAccess da) {
        if (map.put(da.getId(), da) != null)
            throw new IllegalStateException("Cannot attach multiple DataAccess objects for the same id:" + da.getId());
        return da;
    }

    @Override
    public DataAccess create(String id) {
        checkClosed();
        String name = id + counter;
        DataAccess da = create(id, location + name);
        counter++;
        nameMap.put(da, name);
        return da;
    }

    @Override
    public void delete(DataAccess da) {
        if (map.remove(da.getId()) == null)
            throw new IllegalStateException("Couldn't remove dataAccess object:" + da);
    }

    protected void mkdirs() {
        new File(location).mkdirs();
    }

    @Override
    public Collection<DataAccess> getAll() {
        return map.values();
    }

    @Override
    public String toString() {
        return getLocation();
    }

    @Override
    public String getLocation() {
        return location;
    }

    @Override
    public boolean loadExisting() {
        checkClosed();
        Properties properties = new Properties();
        try {
            Reader reader = createReader(location + "info");
            try {
                properties.load(reader);
                for (Entry<Object, Object> e : properties.entrySet()) {
                    counter++;
                    String id = e.getKey().toString();
                    if (id.startsWith("_"))
                        continue;
                    String name = e.getValue().toString();
                    attach(create(id, location + name));
                }
                return true;
            } finally {
                reader.close();
            }
        } catch (IOException ex) {
            return false;
        }
    }

    @Override
    public void flush() {
        checkClosed();
        Properties properties = new Properties();
        for (DataAccess da : getAll()) {
            String name = nameMap.get(da);
            properties.put(da.getId(), name);
        }
        properties.put("_version", Helper.VERSION);
        properties.put("_version_file", "" + Helper.VERSION_FILE);
        try {
            Writer writer = createWriter(location + "info");
            try {
                properties.store(writer, "");
            } finally {
                writer.close();
            }
        } catch (IOException ex) {
            throw new RuntimeException("Cannot flush properties to " + location, ex);
        }
    }

    protected Writer createWriter(String location) throws IOException {
        return new FileWriter(location);
    }

    protected Reader createReader(String location) throws FileNotFoundException {
        return new FileReader(location);
    }

    @Override
    public void close() {
        closed = true;
    }

    void checkClosed() {
        if (closed)
            throw new IllegalStateException("Cannot insert new DataAccess files as the directory is"
                    + " already closed:" + location);
    }

    @Override
    public long capacity() {
        long cap = 0;
        for (DataAccess da : getAll()) {
            cap += da.capacity();
        }
        return cap;
    }
}
