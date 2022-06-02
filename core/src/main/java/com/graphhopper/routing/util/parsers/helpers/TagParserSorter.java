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
package com.graphhopper.routing.util.parsers.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.graphhopper.routing.util.parsers.TagParser;

/**
 * Applies a topological sort to a list of {@link TagParser TagParsers} to satisfy their
 * EncodedValue dependencies.
 * 
 * @author otbutz
 */
public class TagParserSorter {
    
    private final List<TagParser> parsers;
    
    private List<MarkableTagParser> nodes;
    private List<MarkableTagParser> sortedNodes;
    
    public TagParserSorter(List<TagParser> parsers) {
        this.parsers = parsers;
    }

    public void sort() {
        this.nodes = new ArrayList<>();
        for (TagParser parser : parsers) {
            nodes.add(new MarkableTagParser(parser));
        }
        this.sortedNodes = new ArrayList<>();
        
        MarkableTagParser unmarked = getUnmarked();
        while (unmarked != null) {
            visit(unmarked);
            unmarked = getUnmarked();
        }
        
        parsers.clear();
        for (MarkableTagParser node : sortedNodes) {
            parsers.add(node.getParser());
        }
    }
    
    private void visit(MarkableTagParser node) {
        if (node.isPermanent()) {
            return;
        }
        
        if (node.isTemporary()) {
            throw new IllegalStateException("Dependency cycle for parser " + node.getParser().getClass().getSimpleName());
        }
        
        node.setTemporary(true);
        
        List<String> required = node.getParser().getRequiredEncodedValues();
        Set<String> unresolved = new HashSet<>(required);
        for (MarkableTagParser otherNode : nodes) {
            List<String> provided = otherNode.getParser().getProvidedEncodedValues();
            if (Collections.disjoint(required, provided)) {
                continue;
            }
            visit(otherNode);
            unresolved.removeAll(provided);
        }
        
        if (!unresolved.isEmpty()) {
            throw new IllegalStateException("Unable to satisfy dependency for " + unresolved
                            + " of parser" + node.getParser().getClass().getSimpleName());
        }
        
        node.setTemporary(false);
        node.setPermanent(true);
        sortedNodes.add(node);
    }
    
    private MarkableTagParser getUnmarked() {
        for (MarkableTagParser node : nodes) {
            if (!node.isPermanent()) {
                return node;
            }
        }
        return null;
    }
    
    private static class MarkableTagParser {
        private final TagParser parser;
        private boolean temporary;
        private boolean permanent;
        
        public MarkableTagParser(TagParser parser) {
            this.parser = parser;
        }

        public TagParser getParser() {
            return parser;
        }

        public boolean isTemporary() {
            return temporary;
        }

        public void setTemporary(boolean temporary) {
            this.temporary = temporary;
        }

        public boolean isPermanent() {
            return permanent;
        }

        public void setPermanent(boolean permanent) {
            this.permanent = permanent;
        }
    }
}
