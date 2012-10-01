/*
 *  Copyright 2012 Peter Karich 
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
 * @author Peter Karich,
 */
public class GraphStorageWrapper extends DefaultStorage {

    private GraphStorage tmp;

    public GraphStorageWrapper(Directory dir, int expectedNodes) {
        super(expectedNodes);
        g = tmp = new GraphStorage(dir);
//        g = tmp = new PriorityGraphStorage(dir);
    }

    @Override
    public void createNew() {
        tmp.createNew(osmIdToIndexMap.size());
    }

    @Override
    public boolean loadExisting() {
        return tmp.loadExisting();
    }

    @Override
    public void flush() {
        tmp.flush();
        super.flush();
    }

    @Override
    public String toString() {
        return tmp.getClass().getSimpleName() + "|" + tmp.getDirectory().getClass().getSimpleName();
    }
}
