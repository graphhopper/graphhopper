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
    /**
     * The DA object is hold entirely in-memory. Loading and flushing is a no-op. See RAMDataAccess.
     */
    public static final DAType RAM = new DAType(MemRef.HEAP, false, false);
    /**
     * Optimized RAM DA type for integer access. The set and getBytes methods cannot be used.
     */
    public static final DAType RAM_INT = new DAType(MemRef.HEAP, false, true);
    /**
     * The DA object is hold entirely in-memory. It will read load disc and flush to it if they
     * equivalent methods are called. See RAMDataAccess.
     */
    public static final DAType RAM_STORE = new DAType(MemRef.HEAP, true, false);
    /**
     * Optimized RAM_STORE DA type for integer access. The set and getBytes methods cannot be used.
     */
    public static final DAType RAM_INT_STORE = new DAType(MemRef.HEAP, true, true);
    /**
     * Memory mapped DA object. See MMapDataAccess. To make it read and write thread-safe you need
     * to use 'new DAType(MMAP, true)'
     */
    public static final DAType MMAP = new DAType(MemRef.MMAP, true, false);
    /**
     * Experimental API. Do not use yet.
     */
    public static final DAType UNSAFE_STORE = new DAType(MemRef.UNSAFE, true, false);

    public enum MemRef
    {
        HEAP, MMAP, UNSAFE /*, DIRECT */

    };
    private final MemRef memRef;
    private final boolean storing;
    private final boolean integ;
    private final boolean synched;

    public DAType( DAType type, boolean synched )
    {
        this(type.getMemRef(), type.isStoring(), type.isInteg(), synched);
        if (!synched)
            throw new IllegalStateException("constructor can only be used with synched=true");
        if (type.isSynched())
            throw new IllegalStateException("something went wrong as DataAccess object is already synched!?");
    }

    public DAType( MemRef memRef, boolean storing, boolean integ, boolean synched )
    {
        this.memRef = memRef;
        this.storing = storing;
        this.integ = integ;
        this.synched = synched;
    }

    public DAType( MemRef memRef, boolean store, boolean integ )
    {
        this(memRef, store, integ, false);
    }

    /**
     * Memory mapped or purely in memory? default is HEAP
     */
    MemRef getMemRef()
    {
        return memRef;
    }

    /**
     * @return true if data resides in the JVM heap.
     */
    public boolean isInMemory()
    {
        return memRef == MemRef.HEAP;
    }

    public boolean isMMap()
    {
        return memRef == MemRef.MMAP;
    }

    /**
     * Temporary data or store (with loading and storing)? default is false
     */
    public boolean isStoring()
    {
        return storing;
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
        String str;
        if (getMemRef() == MemRef.MMAP)
            str = "MMAP";
        else if (getMemRef() == MemRef.HEAP)
            str = "RAM";
        else
            str = "UNSAFE";

        if (isInteg())
            str += "_INT";
        if (isStoring())
            str += "_STORE";
        if (isSynched())
            str += "_SYNC";
        return str;
    }

    public static DAType fromString( String dataAccess )
    {
        dataAccess = dataAccess.toUpperCase();
        DAType type;
        if (dataAccess.contains("MMAP"))
            type = DAType.MMAP;
        else if (dataAccess.contains("UNSAFE"))
            type = DAType.UNSAFE_STORE;
        else
        {
            if (dataAccess.contains("RAM_STORE"))
                type = DAType.RAM_STORE;
            else
                type = DAType.RAM;
        }

        if (dataAccess.contains("SYNC"))
            type = new DAType(type, true);
        return type;
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 59 * hash + 37 * this.memRef.hashCode();
        hash = 59 * hash + (this.storing ? 1 : 0);
        hash = 59 * hash + (this.integ ? 1 : 0);
        hash = 59 * hash + (this.synched ? 1 : 0);
        return hash;
    }

    @Override
    public boolean equals( Object obj )
    {
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final DAType other = (DAType) obj;
        if (this.memRef != other.memRef)
            return false;
        if (this.storing != other.storing)
            return false;
        if (this.integ != other.integ)
            return false;
        if (this.synched != other.synched)
            return false;
        return true;
    }
}
