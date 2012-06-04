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

import de.jetsli.graph.trees.QuadTree;
import de.jetsli.graph.trees.QuadTreeSimple;
import de.jetsli.graph.util.CoordTrig;
import java.util.Collection;

/**
 * @author Peter Karich
 */
public class ID2LocationSimpleQT implements ID2LocationIndex {

    final QuadTreeSimple<Long> qt = new QuadTreeSimple<Long>(2);

    public ID2LocationSimpleQT(Graph g) {
        QuadTree.Util.fill(qt, g);
    }

    @Override
    public ID2LocationIndex prepareIndex(int capacity) {
        return this;
    }

    @Override
    public int findID(double lat, double lon) {
        Collection<CoordTrig<Long>> coll = qt.getNodes(lat, lon, 0.001);
        if (coll.isEmpty())
            throw new IllegalStateException("cannot find node for " + lat + "," + lon);

        return ((Number) coll.iterator().next().getValue()).intValue();
    }
}
