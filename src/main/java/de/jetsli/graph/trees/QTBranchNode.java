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

import de.jetsli.graph.util.Helper;

/**
 * @author Peter Karich
 */
class QTBranchNode<V> implements QTNode<V> {

    QTNode node0;
    QTNode node1;
    QTNode node2;
    QTNode node3;

    public QTBranchNode() {
    }

    @Override
    public QTNode get(int num) {
        return (num & 2) == 0 ? (num == 0 ? node0 : node1) : ((num & 1) == 0 ? node2 : node3);
    }

    @Override
    public void set(int num, QTNode n) {
        if ((num & 2) == 0) {
            if (num == 0)
                node0 = n;
            else
                node1 = n;
        } else {
            if ((num & 1) == 0)
                node2 = n;
            else
                node3 = n;
        }
    }

    @Override
    public boolean hasData() {
        return false;
    }

    @Override
    public String toString() {
        return "B 0:" + node0.hasData() + " 1:" + node1.hasData() + " 2:" + node2.hasData() + " 3:" + node3.hasData();
    }

    @Override
    public long getMemoryUsageInBytes(int factor) {
        // recursivly fetch the results
        long all = 4 * Helper.sizeOfObjectRef(factor);
        if (node0 != null)
            all += node0.getMemoryUsageInBytes(factor);
        if (node1 != null)
            all += node1.getMemoryUsageInBytes(factor);
        if (node2 != null)
            all += node2.getMemoryUsageInBytes(factor);
        if (node3 != null)
            all += node3.getMemoryUsageInBytes(factor);
        return all;
    }
}
