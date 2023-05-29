/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.storage;

public class Demo {
    public static class DummyGraph {
        NewDataAccess da;

        public DummyGraph(NewDataAccess da) {
            this.da = da;
        }

        public static DummyGraph load(String path, NewDAType daType) {
            NewDataAccess da = NewDataAccess.load(path, daType);
            return new DummyGraph(da);
        }

        public void addNode(int node, int val) {
            da.ensureCapacity((node + 1) * 4L);
            da.setInt(node * 4L, val);
        }

        public int getVal(int node) {
            return da.getInt(node * 4L);
        }

        public void flush() {
            da.flush();
        }
    }

    public static void main(String[] args) {
        // for RAM using a file path is not so intuitive, but we want to flush the data to disk later anyway and this
        // way it's the same as we need for mmap
        NewDataAccess da = new NewRAMDataAccess.Builder().setPath("my_dummy_path").build();
        DummyGraph g = new DummyGraph(da);
        g.addNode(1, 16);
        g.flush();

        // let's load the graph again
        g = DummyGraph.load("my_dummy_path", NewDAType.MMAP);
        // todo: prints 0 not 16 currently, but this is just a little error somewhere
        System.out.println(g.getVal(1));

        // now do it the other way around
        da = new NewMMapDataAccess.Builder().setPath("my_dummy_path").build();
        g = new DummyGraph(da);
        g.addNode(1, 16);
        g.flush();

        // .. load
        g = DummyGraph.load("my_dummy_path", NewDAType.RAM);
        System.out.println(g.getVal(1));
    }
}
