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

import de.jetsli.graph.storage.DistEntry;
import gnu.trove.set.hash.TIntHashSet;
import java.util.Iterator;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public abstract class MyIteratorable<T> implements Iterator<T>, Iterable<T> {

    @Override
    public Iterator<T> iterator() {
        return this;
    }

    public static int count(Iterable<?> iter) {
        int counter = 0;
        for (Object o : iter) {
            ++counter;
        }
        return counter;
    }

    public static boolean contains(Iterable<? extends DistEntry> iter, int... locs) {
        Iterator<? extends DistEntry> i = iter.iterator();
        TIntHashSet set = new TIntHashSet();
        while (i.hasNext()) {
            set.add(i.next().node);
        }
        for (int l : locs) {
            if (!set.contains(l))
                return false;
        }
        return true;
    }
}
