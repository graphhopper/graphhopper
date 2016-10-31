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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.Set;

/**
 * Parses a conditional tag according to
 * http://wiki.openstreetmap.org/wiki/Conditional_restrictions.
 * <p>
 *
 * @author Robin Boldt
 */
public class ConditionalParser {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Set<String> restrictedTags;
    private final boolean enabledLogs;

    public ConditionalParser(Set<String> restrictedTags) {
        this(restrictedTags, false);
    }

    public ConditionalParser(Set<String> restrictedTags, boolean enabledLogs) {
        // use map => key & type (date vs. double)
        this.restrictedTags = restrictedTags;
        this.enabledLogs = enabledLogs;
    }

    public ValueRange getRange(String conditionalTag) throws ParseException {
        if (conditionalTag == null || conditionalTag.isEmpty() || !conditionalTag.contains("@"))
            return null;

        if (conditionalTag.contains(";")) {
            if (enabledLogs)
                logger.warn("We do not support multiple conditions yet: " + conditionalTag);
            return null;
        }

        String[] conditionalArr = conditionalTag.split("@");

        if (conditionalArr.length != 2)
            throw new IllegalStateException("could not split this condition: " + conditionalTag);

        String restrictiveValue = conditionalArr[0].trim();
        if (!restrictedTags.contains(restrictiveValue))
            return null;

        String conditional = conditionalArr[1];
        conditional = conditional.replace('(', ' ');
        conditional = conditional.replace(')', ' ');
        conditional = conditional.trim();

        int index = conditional.indexOf(">");
        if (index > 0 && conditional.length() > 2) {
            final String key = conditional.substring(0, index).trim();
            // for now just ignore equals sign
            if (conditional.charAt(index + 1) == '=')
                index++;

            final double value = parseNumber(conditional.substring(index + 1));
            return new ValueRange<Number>() {
                @Override
                public boolean isInRange(Number obj) {
                    return obj.doubleValue() > value;
                }

                @Override
                public String getKey() {
                    return key;
                }
            };
        }

        index = conditional.indexOf("<");
        if (index > 0 && conditional.length() > 2) {
            final String key = conditional.substring(0, index).trim();
            if (conditional.charAt(index + 1) == '=')
                index++;

            final double value = parseNumber(conditional.substring(index + 1));
            return new ValueRange<Number>() {

                @Override
                public boolean isInRange(Number obj) {
                    return obj.doubleValue() < value;
                }

                @Override
                public String getKey() {
                    return key;
                }
            };
        }

        return DateRangeParser.parseDateRange(conditional);
    }

    protected double parseNumber(String str) {
        int untilIndex = str.length() - 1;
        for (; untilIndex >= 0; untilIndex--) {
            if (Character.isDigit(str.charAt(untilIndex)))
                break;
        }
        return Double.parseDouble(str.substring(0, untilIndex + 1));
    }
}
