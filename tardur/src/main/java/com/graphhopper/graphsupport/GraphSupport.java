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

package com.graphhopper.graphsupport;

import com.graphhopper.storage.Graph;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class GraphSupport {

    public static Stream<EdgeIteratorState> allEdges(Graph graph) {
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<EdgeIteratorState>(graph.getEdges(), 0) {
            EdgeIterator edgeIterator = graph.getAllEdges();

            @Override
            public boolean tryAdvance(Consumer<? super EdgeIteratorState> action) {
                if (edgeIterator.next()) {
                    action.accept(edgeIterator);
                    return true;
                } else {
                    return false;
                }
            }
        }, false);
    }

    public static Stream<TurnCostStorage.TurnRelationIterator> allTurnRelations(TurnCostStorage tcs) {
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<TurnCostStorage.TurnRelationIterator>(0, 0) {
            TurnCostStorage.TurnRelationIterator turnRelationIterator = tcs.getAllTurnRelations();

            @Override
            public boolean tryAdvance(Consumer<? super TurnCostStorage.TurnRelationIterator> action) {
                if (turnRelationIterator.next()) {
                    action.accept(turnRelationIterator);
                    return true;
                } else {
                    return false;
                }
            }
        }, false);
    }


}
