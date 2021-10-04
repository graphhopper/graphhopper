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
package com.graphhopper.reader.osm.conditional;

import com.graphhopper.reader.ConditionalSpeedInspector;
import com.graphhopper.reader.ReaderWay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Inspects the conditional tags of an OSMWay according to the given conditional tags.
 * <p>
 *
 * @author Andrzej Oles
 */
public class ConditionalOSMSpeedInspector implements ConditionalSpeedInspector {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final List<String> tagsToCheck;
    private final ConditionalParser parser;
    // enabling by default makes noise but could improve OSM data
    private boolean enabledLogs = true;

    private String val;
    private boolean isLazyEvaluated;

    @Override
    public String getTagValue() {
        return val;
    }

    public ConditionalOSMSpeedInspector(List<String> tagsToCheck) {
        this(tagsToCheck, false);
    }

    public ConditionalOSMSpeedInspector(List<String> tagsToCheck, boolean enabledLogs) {
        this.tagsToCheck = new ArrayList<>(tagsToCheck.size());
        for (String tagToCheck : tagsToCheck) {
            this.tagsToCheck.add(tagToCheck + ":conditional");
        }

        this.enabledLogs = enabledLogs;
        parser = new ConditionalParser(null);
    }

    public void addValueParser(ConditionalValueParser vp) {
        parser.addConditionalValueParser(vp);
    }

    @Override
    public boolean hasConditionalSpeed(ReaderWay way) {
        for (int index = 0; index < tagsToCheck.size(); index++) {
            String tagToCheck = tagsToCheck.get(index);
            val = way.getTag(tagToCheck);
            if (val == null || val.isEmpty())
                continue;
            try {
                ConditionalParser.Result result = parser.checkCondition(val);
                isLazyEvaluated = result.isLazyEvaluated();
                val = result.getRestrictions();
                if (result.isCheckPassed() || isLazyEvaluated)
                    return true;
            } catch (Exception e) {
                if (enabledLogs) {
                    logger.warn("for way " + way.getId() + " could not parse the conditional value '" + val + "' of tag '" + tagToCheck + "'. Exception:" + e.getMessage());
                }
            }
        }
        return false;
    }

    @Override
    public boolean hasLazyEvaluatedConditions() {
        return isLazyEvaluated;
    }

}
