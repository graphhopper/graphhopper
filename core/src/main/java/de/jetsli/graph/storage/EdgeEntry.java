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
 * @see DistEntry
 * @author Peter Karich, info@jetsli.de
 */
public class EdgeEntry extends Edge implements Cloneable {

    public EdgeEntry prevEntry;

    public EdgeEntry(int loc, double distance) {
        super(loc, distance);
    }

    @Override
    public EdgeEntry clone() {
        return new EdgeEntry(node, weight);
    }

    public EdgeEntry cloneFull() {
        EdgeEntry de = clone();
        EdgeEntry tmpPrev = prevEntry;
        EdgeEntry cl = de;
        while (tmpPrev != null) {
            cl.prevEntry = tmpPrev.clone();
            cl = cl.prevEntry;
            tmpPrev = tmpPrev.prevEntry;
        }
        return de;
    }
}
