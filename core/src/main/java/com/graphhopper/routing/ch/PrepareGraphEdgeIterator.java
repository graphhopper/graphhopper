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

package com.graphhopper.routing.ch;

public interface PrepareGraphEdgeIterator {
    boolean next();

    int getBaseNode();

    int getAdjNode();

    int getPrepareEdge();

    boolean isShortcut();

    int getOrigEdgeKeyFirst();

    int getOrigEdgeKeyLast();

    int getSkipped1();

    int getSkipped2();

    double getWeight();

    int getOrigEdgeCount();

    void setSkippedEdges(int skipped1, int skipped2);

    void setWeight(double weight);

    void setOrigEdgeCount(int origEdgeCount);
}
