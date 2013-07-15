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

/**
 * Defines how a DataAccess object is created.
 * <p/>
 * @author Peter Karich
 */
public class DAType
{
    public static final DAType RAM_INT_STORE = new DAType(false, true, true);
    public static final DAType RAM_INT = new DAType(false, false, true);
    public static final DAType RAM_STORE = new DAType(false, true, false);
    public static final DAType RAM = new DAType(false, false, false);
    public static final DAType MMAP = new DAType(true, true, false);
    private final boolean mmap;
    private final boolean store;
    private final boolean integ;
    private final boolean synched;

    public DAType( DAType type, boolean synched )
    {
        this(type.isMmap(), type.isStore(), type.isInteg(), synched);
        if (!synched)
            throw new IllegalStateException("constructor can only be used with synched=true");
        if (type.isSynched())
            throw new IllegalStateException("something went wrong as DataAccess object is already synched!?");
    }

    public DAType( boolean mmap, boolean store, boolean integ, boolean synched )
    {
        this.mmap = mmap;
        this.store = store;
        this.integ = integ;
        this.synched = synched;
    }

    public DAType( boolean mmap, boolean store, boolean integ )
    {
        this(mmap, store, integ, false);
    }

    /**
     * Memory mapped or purely in memory? default is false
     */
    public boolean isMmap()
    {
        return mmap;
    }

    /**
     * Temporary data or store (with loading and storing)? default is false
     */
    public boolean isStore()
    {
        return store;
    }

    /**
     * Optimized for integer values? default is false
     */
    public boolean isInteg()
    {
        return integ;
    }

    /**
     * Synchronized access wrapper around DataAccess objects? default is false and so an in-memory
     * DataAccess object is only read-thread safe where a memory mapped one is not even
     * read-threadsafe!
     */
    public boolean isSynched()
    {
        return synched;
    }

    @Override
    public String toString()
    {
        String str = isMmap() ? "MMAP" : "RAM";
        if (isInteg())
            str += "_INT";
        if (isStore())
            str += "_STORE";
        if (isSynched())
            str += "_SYNC";
        return str;
    }
}
