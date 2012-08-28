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
 * Support for updating the distance or flags while iterating.
 *
 * Current usage
 * <pre>
 * // use a graph with iterator-update support like MemoryGraphSafe
 * EdgeUpdateIterator iter = (EdgeUpdateIterator) graph.getEdges(n);
 * while(iter.next()) {
 *   iter.distance(19.0);
 *   ...
 * }
 * </pre>
 *
 * @author Peter Karich
 */
public interface EdgeUpdateIterator extends EdgeIterator {

    void distance(double dist);

    void flags(int flags);
}
