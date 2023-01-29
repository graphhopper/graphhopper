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

package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.RestrictionTagParser;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.util.parsers.RelationTagParser;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class OSMParsers {
    private final List<String> ignoredHighways;
    private final List<TagParser> wayTagParsers;
    private final List<RelationTagParser> relationTagParsers;
    private final List<RestrictionTagParser> restrictionTagParsers;
    private final EncodedValue.InitializerConfig relConfig = new EncodedValue.InitializerConfig();

    public OSMParsers() {
        this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    public OSMParsers(List<String> ignoredHighways, List<TagParser> wayTagParsers,
                      List<RelationTagParser> relationTagParsers, List<RestrictionTagParser> restrictionTagParsers) {
        this.ignoredHighways = ignoredHighways;
        this.wayTagParsers = wayTagParsers;
        this.relationTagParsers = relationTagParsers;
        this.restrictionTagParsers = restrictionTagParsers;
    }

    public OSMParsers addIgnoredHighway(String highway) {
        ignoredHighways.add(highway);
        return this;
    }

    public OSMParsers addWayTagParser(TagParser tagParser) {
        wayTagParsers.add(tagParser);
        return this;
    }

    public OSMParsers addRelationTagParser(Function<EncodedValue.InitializerConfig, RelationTagParser> createRelationTagParser) {
        relationTagParsers.add(createRelationTagParser.apply(relConfig));
        return this;
    }

    public OSMParsers addRestrictionTagParser(RestrictionTagParser restrictionTagParser) {
        restrictionTagParsers.add(restrictionTagParser);
        return this;
    }

    public boolean acceptWay(ReaderWay way) {
        String highway = way.getTag("highway");
        if (highway != null)
            return !ignoredHighways.contains(highway);
        else if (way.getTag("route") != null)
            // we accept *all* ways with a 'route' tag and no 'highway' tag, because most of them are ferries
            // (route=ferry), which we want, and there aren't so many such ways we do not want
            // https://github.com/graphhopper/graphhopper/pull/2702#discussion_r1038093050
            return true;
        else if ("pier".equals(way.getTag("man_made")))
            return true;
        else if ("platform".equals(way.getTag("railway")))
            return true;
        else
            return false;
    }

    public IntsRef handleRelationTags(ReaderRelation relation, IntsRef relFlags) {
        for (RelationTagParser relParser : relationTagParsers) {
            relParser.handleRelationTags(relFlags, relation);
        }
        return relFlags;
    }

    public void handleWayTags(IntsRef edgeFlags, ReaderWay way, IntsRef relationFlags) {
        for (RelationTagParser relParser : relationTagParsers)
            relParser.handleWayTags(edgeFlags, way, relationFlags);
        for (TagParser parser : wayTagParsers)
            parser.handleWayTags(edgeFlags, way, relationFlags);
    }

    public IntsRef createRelationFlags() {
        int requiredInts = relConfig.getRequiredInts();
        if (requiredInts > 2)
            throw new IllegalStateException("More than two ints are needed for relation flags, but OSMReader does not allow this");
        return new IntsRef(2);
    }

    public List<String> getIgnoredHighways() {
        return ignoredHighways;
    }

    public List<TagParser> getWayTagParsers() {
        return wayTagParsers;
    }

    public List<RelationTagParser> getRelationTagParsers() {
        return relationTagParsers;
    }

    public List<RestrictionTagParser> getRestrictionTagParsers() {
        return restrictionTagParsers;
    }
}
