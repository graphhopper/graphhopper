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
package de.jetsli.graph.trees;

/**
 * Avoid ugly casting and use the same interface for data and branch nodes. although not really any
 * method in common
 *
 * @author Peter Karich
 */
interface QTNode<V> {

    QTNode<V> get(int num);

    void set(int num, QTNode<V> n);

    boolean hasData();

    /**
     * This methods returns the memory usage for PerfTest without the memory of the values.
     * I.e. you need to add sizeOf(V)*noOfNodes
     *
     * @param factor is 1 for 32 bit and 2 for 64 bit systems
     */
    long getMemoryUsageInBytes(int factor);
    
    int count();

    long getEmptyEntries(boolean onlyBranches);
}
