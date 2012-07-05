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
 * To avoid boxing and unboxing we can't use Iterable<Integer>, so we created this class similar to
 * Lucene's DocIdIterator.
 *
 * Usage:
 * <pre>
 * IdIterator iter = someData.iterator();
 * for(int id; (id = iter.nextId()) != IdIterator.END;) {
 *    // do something with id
 * }
 * </pre>
 *
 * @author Peter Karich
 */
public interface IdIterator {

    public static final int END = -1;

    /**
     * @return END if no more data is available or it returns the current id
     * @throws IllegalStateException if nextId wasn't called.
     */
    int id();

    /**
     * Jumps to the next id and returns it or returns END if no more data available.
     */
    int nextId();
}
