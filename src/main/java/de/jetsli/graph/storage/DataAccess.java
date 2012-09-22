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

/**
 * Abstraction of the underlying datastructure. Current implementations are RAM and memory mapped
 * kind. After construction and before usage you'll have to call alloc or a successfully
 * loadExisting
 *
 * @author Peter Karich
 */
public interface DataAccess {

    void setInt(long index, int value);

    int getInt(long index);

    boolean loadExisting();

    DataAccess flush();

    /**
     * Do not access this object after calling this method. It would result in undefined behaviour
     */
    DataAccess close();

    void ensureCapacity(long bytes);

    int capacity();

    void setNoValue(int noValue);
}
