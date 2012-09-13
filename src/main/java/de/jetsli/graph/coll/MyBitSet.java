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
package de.jetsli.graph.coll;

/**
 * Wrapper interface for different implementations like OpenBitset, BitSet, ...
 *
 * Supports only integer value indices.
 *
 * @author Peter Karich, info@jetsli.de
 */
public interface MyBitSet {

    boolean contains(int index);

    void add(int index);

    int getCardinality();

    void clear();

    void ensureCapacity(int size);

    int next(int index);
}
