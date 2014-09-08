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
 * If you need custom storages, like turn cost tables, or osmid tables for your graph you implement
 * this interface and put it in any graph storage you want.
 */
public interface ExtendedStorage
{
    /**
     * @return true, if and only if, if an additional field at the graphs node storage is required
     */
    boolean isRequireNodeField();

    /**
     * @return true, if and only if, if an additional field at the graphs edge storage is required
     */
    boolean isRequireEdgeField();

    /**
     * @return the default field value which will be set for default when creating nodes
     */
    int getDefaultNodeFieldValue();

    /**
     * @return the default field value which will be set for default when creating edges
     */
    int getDefaultEdgeFieldValue();

    /**
     * initializes the extended storage by giving the graph storage
     */
    void init( GraphStorage graph );

    /**
     * creates all additional data storages
     */
    void create( long initSize );

    /**
     * loads from existing data storages
     */
    boolean loadExisting();

    /**
     * sets the segment size in all additional data storages
     */
    void setSegmentSize( int bytes );

    /**
     * flushes all additional data storages
     */
    void flush();

    /**
     * closes all additional data storages
     */
    void close();

    /**
     * returns the sum of all additional data storages capacity
     */
    long getCapacity();

    /**
     * creates a copy of this extended storage
     */
    ExtendedStorage copyTo( ExtendedStorage extStorage );

    /**
     * default implementation defines no additional fields or any logic. there's like nothing , like
     * the default behavior.
     */
    public class NoExtendedStorage implements ExtendedStorage
    {

        @Override
        public boolean isRequireNodeField()
        {
            return false;
        }

        @Override
        public boolean isRequireEdgeField()
        {
            return false;
        }

        @Override
        public int getDefaultNodeFieldValue()
        {
            return 0;
        }

        @Override
        public int getDefaultEdgeFieldValue()
        {
            return 0;
        }

        @Override
        public void init( GraphStorage grap )
        {
            // noop
        }

        @Override
        public void create( long initSize )
        {
            // noop
        }

        @Override
        public boolean loadExisting()
        {
            // noop
            return true;
        }

        @Override
        public void setSegmentSize( int bytes )
        {
            // noop
        }

        @Override
        public void flush()
        {
            // noop
        }

        @Override
        public void close()
        {
            // noop
        }

        @Override
        public long getCapacity()
        {
            return 0;
        }

        @Override
        public ExtendedStorage copyTo( ExtendedStorage extStorage )
        {
            // noop
            return extStorage;
        }

        @Override
        public String toString()
        {
            return "NoExt";
        }       
    }
}
