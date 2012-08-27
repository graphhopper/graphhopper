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
package de.jetsli.graph.util;

/**
 * Iterates through all edges of one node. Avoids object creation in-between via direct access
 * methods. These methods can be implemented as lazy fetching but often this will be avoid to "fetch
 * the properties as a whole" (benefits for transactions, locks, etc)
 *
 * Usage:
 * <pre>
 * while(iter.next()) {
 *   // be sure to store in temporary variable as access could be expensive
 *   int nodeId = iter.nodeId();
 *   ...
 * }
 *
 * @author Peter Karich
 */
public interface EdgeIterator {

    boolean next();

    int node();

    double distance();

    int flags();
    public static final EdgeIterator EMPTY = new EdgeIterator() {
        @Override public boolean next() {
            return false;
        }

        @Override public int node() {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override public double distance() {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override public int flags() {
            throw new UnsupportedOperationException("Not supported.");
        }
    };
}
