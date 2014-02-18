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
package com.graphhopper.storage;

import com.graphhopper.util.Helper;
import java.io.File;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Implements some common methods for the subclasses.
 * <p/>
 * @author Peter Karich
 */
public class GHDirectory implements Directory
{
    protected Map<String, DataAccess> map = new HashMap<String, DataAccess>();
    protected Map<String, DAType> types = new HashMap<String, DAType>();
    protected final String location;
    private final DAType defaultType;
    private final ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;

    public GHDirectory( String _location, DAType defaultType )
    {
        this.defaultType = defaultType;
        if (Helper.isEmpty(_location))
            _location = new File("").getAbsolutePath();

        if (!_location.endsWith("/"))
            _location += "/";

        location = _location;
        File dir = new File(location);
        if (dir.exists() && !dir.isDirectory())
            throw new RuntimeException("file '" + dir + "' exists but is not a directory");

        // set default access to integer based
        // improves performance on server side, 10% faster for queries, 20% faster for preparation
        if (this.defaultType.isInMemory())
        {
            if (isStoring())
            {
                put("locationIndex", DAType.RAM_INT_STORE);
                put("edges", DAType.RAM_INT_STORE);
                put("nodes", DAType.RAM_INT_STORE);
            } else
            {
                put("locationIndex", DAType.RAM_INT);
                put("edges", DAType.RAM_INT);
                put("nodes", DAType.RAM_INT);
            }
        }
        mkdirs();
    }

    @Override
    public ByteOrder getByteOrder()
    {
        return byteOrder;
    }

    public Directory put( String name, DAType type )
    {
        types.put(name, type);
        return this;
    }

    @Override
    public DataAccess find( String name )
    {
        DAType type = types.get(name);
        if (type == null)
            type = defaultType;

        return find(name, type);
    }

    @Override
    public DataAccess find( String name, DAType type )
    {
        DataAccess da = map.get(name);
        if (da != null)
        {
            if (!type.equals(da.getType()))
                throw new IllegalStateException("Found existing DataAccess object '" + name
                        + "' but types did not match. Requested:" + type + ", was:" + da.getType());
            return da;
        }

        if (type.isInMemory())
        {
            if (type.isInteg())
            {
                if (type.isStoring())
                    da = new RAMIntDataAccess(name, location, true, byteOrder);
                else
                    da = new RAMIntDataAccess(name, location, false, byteOrder);
            } else
            {
                if (type.isStoring())
                    da = new RAMDataAccess(name, location, true, byteOrder);
                else
                    da = new RAMDataAccess(name, location, false, byteOrder);
            }
        } else if (type.isMMap())
        {
            da = new MMapDataAccess(name, location, byteOrder);
        } else
        {
            da = new UnsafeDataAccess(name, location, byteOrder);
        }

        if (type.isSynched())
            da = new SynchedDAWrapper(da);

        map.put(name, da);
        return da;
    }

    @Override
    public DataAccess rename( DataAccess da, String newName )
    {
        String oldName = da.getName();
        da.rename(newName);
        removeByName(oldName);
        map.put(newName, da);
        return da;
    }

    @Override
    public void remove( DataAccess da )
    {
        removeByName(da.getName());
    }

    void removeByName( String name )
    {
        DataAccess da = map.remove(name);
        if (da == null)
            throw new IllegalStateException("Couldn't remove dataAccess object:" + name);
        da.close();
        if (da.getType().isStoring())
            Helper.removeDir(new File(location + name));
    }

    @Override
    public DAType getDefaultType()
    {
        return defaultType;
    }

    public boolean isStoring()
    {
        return defaultType.isStoring();
    }

    protected void mkdirs()
    {
        if (isStoring())
        {
            new File(location).mkdirs();
        }
    }

    Collection<DataAccess> getAll()
    {
        return map.values();
    }

    @Override
    public String toString()
    {
        return getLocation();
    }

    @Override
    public String getLocation()
    {
        return location;
    }
}
