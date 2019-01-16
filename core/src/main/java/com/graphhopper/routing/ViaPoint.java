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
package com.graphhopper.routing;

import com.graphhopper.storage.SPTEntry;

import java.util.ArrayList;

/**
 * This class is similar to ContactPoint but it can store multiple SPTEntries per node. It's needed for the long
 * algorithm to find contact points
 *
 * @author Maximilian Sturm
 */
class ViaPoint {
    private final int node;
    private ArrayList<SPTEntry> entriesFrom;
    private ArrayList<SPTEntry> entriesTo;

    protected ViaPoint(int node) {
        this.node = node;
        entriesFrom = new ArrayList<>();
        entriesTo = new ArrayList<>();
    }

    protected void addEntryFrom(SPTEntry entry) {
        entriesFrom.add(entry);
        if (entry.adjNode != node)
            throw new IllegalStateException("The SPTEntry's adjNode has to be this ViaPoints's node");
    }

    protected void addEntryTo(SPTEntry entry) {
        entriesTo.add(entry);
        if (entry.adjNode != node)
            throw new IllegalStateException("The SPTEntry's adjNode has to be this ViaPoints's node");
    }

    protected int getNode() {
        return node;
    }

    protected ArrayList<ContactPoint> createContactPoints() {
        ArrayList<ContactPoint> points = new ArrayList<>();
        for (SPTEntry from : entriesFrom)
            for (SPTEntry to : entriesTo)
                points.add(new ContactPoint(from, to));
        return points;
    }
}
