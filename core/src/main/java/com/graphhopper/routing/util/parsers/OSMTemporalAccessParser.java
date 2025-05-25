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

package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.conditional.ConditionalValueParser;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;

import java.text.ParseException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * This parser fills the different XYTemporalAccess enums from the OSM conditional
 * restrictions based on the specified dateRangeParserDate. 'Temporal' means that both, temporary
 * and seasonal restrictions will be considered. Node tags will be ignored for now.
 */
public class OSMTemporalAccessParser implements TagParser {

    private final Collection<String> conditionals;
    private final Setter restrictionSetter;
    private final DateRangeParser parser;

    @FunctionalInterface
    public interface Setter {
        void setBoolean(int edgeId, EdgeIntAccess edgeIntAccess, boolean b);
    }

    public OSMTemporalAccessParser(Collection<String> conditionals, Setter restrictionSetter, String dateRangeParserDate) {
        this.conditionals = conditionals;
        this.restrictionSetter = restrictionSetter;
        if (dateRangeParserDate.isEmpty())
            dateRangeParserDate = Helper.createFormatter("yyyy-MM-dd").format(new Date().getTime());

        this.parser = DateRangeParser.createInstance(dateRangeParserDate);
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        // TODO for now the node tag overhead is not worth the effort due to very few data points
        // List<Map<String, Object>> nodeTags = way.getTag("node_tags", null);

        Boolean b = getTemporaryAccess(way.getTags());
        if (b != null)
            restrictionSetter.setBoolean(edgeId, edgeIntAccess, b);
    }

    public Boolean getTemporaryAccess(Map<String, Object> tags) {
        for (Map.Entry<String, Object> entry : tags.entrySet()) {
            if (!conditionals.contains(entry.getKey())) continue;

            String value = (String) entry.getValue();
            String[] strs = value.split("@");
            if (strs.length == 2) {
                Boolean inRange = isInRange(parser, strs[1].trim());
                if (inRange != null) {
                    if (strs[0].trim().equals("no")) return !inRange;
                    if (strs[0].trim().equals("yes")) return inRange;
                }
            }
        }
        return null;
    }

    private static Boolean isInRange(final DateRangeParser parser, final String value) {
        if (value.isEmpty())
            return null;

        if (value.contains(";"))
            return null;

        String conditionalValue = value.replace('(', ' ').replace(')', ' ').trim();
        try {
            ConditionalValueParser.ConditionState res = parser.checkCondition(conditionalValue);
            if (res.isValid())
                return res.isCheckPassed();
        } catch (ParseException ex) {
        }
        return null;
    }

    /**
     * This method checks the conditional restrictions starting from firstIndex and returns
     * true if the access value is in the "accepted" collection AND the conditional value describes
     * a time (e.g. date, time or interval).
     */
    public static boolean hasPermissiveTemporalRestriction(ReaderWay way, int firstIndex,
                                                           List<String> restrictionKeys, Collection<String> accepted) {
        for (int i = firstIndex; i >= 0; i--) {
            String value = way.getTag(restrictionKeys.get(i) + ":conditional");
            if (acceptedAndInRange(value, accepted)) return true;
        }
        return false;
    }

    private static boolean acceptedAndInRange(String value, Collection<String> accepted) {
        if (value == null) return false;
        String[] strs = value.split("@");
        if (strs.length == 2)
            try {
                String conditionalValue = strs[1].replace('(', ' ').replace(')', ' ').trim();
                return accepted.contains(strs[0].trim()) &&
                        (strs[1].contains(":") // time
                                || DateRangeParser.getRange(conditionalValue    ) != null // date
                        );
            } catch (ParseException ex) {
            }
        return false;
    }
}
