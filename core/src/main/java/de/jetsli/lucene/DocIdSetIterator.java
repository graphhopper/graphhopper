/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jetsli.lucene;

import java.io.IOException;

/**
 * This abstract class defines methods to iterate over a set of non-decreasing doc ids. Note that
 * this class assumes it iterates on doc Ids, and therefore {@link #NO_MORE_DOCS} is set to
 * {@value #NO_MORE_DOCS} in order to be used as a sentinel object. Implementations of this class
 * are expected to consider {@link Integer#MAX_VALUE} as an invalid value.
 */
public abstract class DocIdSetIterator {

    /**
     * When returned by {@link #nextDoc()}, {@link #advance(int)} and {@link #docID()} it means
     * there are no more docs in the iterator.
     */
    public static final int NO_MORE_DOCS = Integer.MAX_VALUE;

    /**
     * Returns the following: <ul> <li>-1 or {@link #NO_MORE_DOCS} if {@link #nextDoc()} or
     * {@link #advance(int)} were not called yet. <li>{@link #NO_MORE_DOCS} if the iterator has
     * exhausted. <li>Otherwise it should return the doc ID it is currently on. </ul> <p>
     *
     * @since 2.9
     */
    public abstract int docID();

    /**
     * Advances to the next document in the set and returns the doc it is currently on, or
     * {@link #NO_MORE_DOCS} if there are no more docs in the set.<br>
     *
     * <b>NOTE:</b> after the iterator has exhausted you should not call this method, as it may
     * result in unpredicted behavior.
     *
     * @since 2.9
     */
    public abstract int nextDoc() throws IOException;

    /**
     * Advances to the first beyond (see NOTE below) the current whose document number is greater
     * than or equal to <i>target</i>. Returns the current document number or {@link #NO_MORE_DOCS}
     * if there are no more docs in the set. <p> Behaves as if written:
     *
     * <pre>
     * int advance(int target) {
     *   int doc;
     *   while ((doc = nextDoc()) &lt; target) {
     *   }
     *   return doc;
     * }
     * </pre>
     *
     * Some implementations are considerably more efficient than that. <p> <b>NOTE:</b> when
     * <code> target &le; current</code> implementations may opt not to advance beyond their current
     * {@link #docID()}. <p> <b>NOTE:</b> this method may be called with {@link #NO_MORE_DOCS} for
     * efficiency by some Scorers. If your implementation cannot efficiently determine that it
     * should exhaust, it is recommended that you check for that value in each call to this method.
     * <p> <b>NOTE:</b> after the iterator has exhausted you should not call this method, as it may
     * result in unpredicted behavior. <p>
     *
     * @since 2.9
     */
    public abstract int advance(int target) throws IOException;
}
