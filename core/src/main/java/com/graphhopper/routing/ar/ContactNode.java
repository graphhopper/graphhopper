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
package com.graphhopper.routing.ar;

import com.graphhopper.storage.SPTEntry;

/**
 * This class stores a node where both search spaces meat each other. It's stored together with its two
 * corresponding SPTEntries out of which a path can be extracted
 *
 * @author Maximilian Sturm
 */
public class ContactNode {
    private final int node;
    private final SPTEntry entryFrom;
    private final SPTEntry entryTo;

    public ContactNode(SPTEntry entryFrom, SPTEntry entryTo) {
        node = entryFrom.adjNode;
        this.entryFrom = entryFrom;
        this.entryTo = entryTo;
        if (entryFrom.adjNode != entryTo.adjNode)
            throw new IllegalStateException("Both nodes have to be the same");
    }

    public int getNode() {
        return node;
    }

    public SPTEntry getEntryFrom() {
        return entryFrom;
    }

    public SPTEntry getEntryTo() {
        return entryTo;
    }
}
