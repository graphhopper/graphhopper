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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
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
    private final List<ConditionalValueParser> valueParsers = new ArrayList<>(5);
    private final boolean enabledLogs;

    public ConditionalParser(Set<String> restrictedTags) {
        this(restrictedTags, false);
    }

    public ConditionalParser(Set<String> restrictedTags, boolean enabledLogs) {
        // use map => key & type (date vs. double)
        this.restrictedTags = restrictedTags;
        this.enabledLogs = enabledLogs;
    }

    public static ConditionalValueParser createNumberParser(final String assertKey, final Number obj) {
        return new ConditionalValueParser() {
            @Override
            public ConditionState checkCondition(String conditionalValue) throws ParseException {
                int indexLT = conditionalValue.indexOf("<");
                if (indexLT > 0 && conditionalValue.length() > 2) {
                    final String key = conditionalValue.substring(0, indexLT).trim();
                    if (!assertKey.equals(key))
                        return ConditionState.INVALID;

                    if (conditionalValue.charAt(indexLT + 1) == '=')
                        indexLT++;
                    final double value = parseNumber(conditionalValue.substring(indexLT + 1));
                    if (obj.doubleValue() < value)
                        return ConditionState.TRUE;
                    else
                        return ConditionState.FALSE;
                }

                int indexGT = conditionalValue.indexOf(">");
                if (indexGT > 0 && conditionalValue.length() > 2) {
                    final String key = conditionalValue.substring(0, indexGT).trim();
                    if (!assertKey.equals(key))
                        return ConditionState.INVALID;

                    // for now just ignore equals sign
                    if (conditionalValue.charAt(indexGT + 1) == '=')
                        indexGT++;

                    final double value = parseNumber(conditionalValue.substring(indexGT + 1));
                    if (obj.doubleValue() > value)
                        return ConditionState.TRUE;
                    else
                        return ConditionState.FALSE;
                }

                return ConditionState.INVALID;
            }
        };
    }

    /**
     * This method adds a new value parser. The one added last has a higher priority.
     */
    public ConditionalParser addConditionalValueParser(ConditionalValueParser vp) {
        valueParsers.add(0, vp);
        return this;
    }

    public ConditionalParser setConditionalValueParser(ConditionalValueParser vp) {
        valueParsers.clear();
        valueParsers.add(vp);
        return this;
    }

    public boolean checkCondition(String conditionalTag) throws ParseException {
        if (conditionalTag == null || conditionalTag.isEmpty() || !conditionalTag.contains("@"))
            return false;

        if (conditionalTag.contains(";")) {
            if (enabledLogs)
                logger.warn("We do not support multiple conditions yet: " + conditionalTag);
            return false;
        }

        String[] conditionalArr = conditionalTag.split("@");

        if (conditionalArr.length != 2)
            throw new IllegalStateException("could not split this condition: " + conditionalTag);

        String restrictiveValue = conditionalArr[0].trim();
        if (!restrictedTags.contains(restrictiveValue))
            return false;

        String conditionalValue = conditionalArr[1];
        conditionalValue = conditionalValue.replace('(', ' ');
        conditionalValue = conditionalValue.replace(')', ' ');
        conditionalValue = conditionalValue.trim();

        for (ConditionalValueParser valueParser : valueParsers) {
            ConditionalValueParser.ConditionState c = valueParser.checkCondition(conditionalValue);
            if (c.isValid())
                return c.isCheckPassed();
        }
        return false;
    }

    protected static double parseNumber(String str) {
        int untilIndex = str.length() - 1;
        for (; untilIndex >= 0; untilIndex--) {
            if (Character.isDigit(str.charAt(untilIndex)))
                break;
        }
        return Double.parseDouble(str.substring(0, untilIndex + 1));
    }
}
