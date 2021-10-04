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

import ch.poole.conditionalrestrictionparser.*;
import ch.poole.openinghoursparser.OpeningHoursParser;
import ch.poole.openinghoursparser.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Parses a conditional tag according to
 * http://wiki.openstreetmap.org/wiki/Conditional_restrictions.
 * <p>
 *
 * @author Robin Boldt
 * @author Andrzej Oles
 */
public class ConditionalParser {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Set<String> restrictedTags;
    private final List<ConditionalValueParser> valueParsers = new ArrayList<>(5);
    private final boolean enabledLogs;
    // ORS-GH MOD START - additional field
    private final String simpleValue;
    // ORS-GH MOD END

    public ConditionalParser(Set<String> restrictedTags) {
        this(restrictedTags, false);
    }

    public ConditionalParser(Set<String> restrictedTags, boolean enabledLogs) {
        // use map => key & type (date vs. double)
        this.restrictedTags = restrictedTags;
        this.enabledLogs = enabledLogs;
        // ORS-GH MOD - fill additional field
        this.simpleValue = hasRestrictedValues() && restrictedTags.contains("yes") ? "yes" : "no";
    }

    public static ConditionalValueParser createNumberParser(final String assertKey, final Number obj) {
        return new ConditionalValueParser() {
            // ORS-GH MOD START - additional method
            @Override
            public ConditionState checkCondition(Condition condition) throws ParseException {
                if (condition.isExpression())
                    return checkCondition(condition.toString());
                else
                    return ConditionState.INVALID;
            }
            // ORS-GH MOD END

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

    // ORS-GH MOD START - additional method
    public static ConditionalValueParser createDateTimeParser() {
        return new ConditionalValueParser() {
            @Override
            public ConditionState checkCondition(String conditionString) {
                List<Rule> rules;
                try {
                    OpeningHoursParser parser = new OpeningHoursParser(new ByteArrayInputStream(conditionString.getBytes()));
                    rules = parser.rules(false);
                }
                catch (Exception e) {
                    return ConditionState.INVALID;
                }
                if (rules.isEmpty())
                    return ConditionState.INVALID;
                else {
                    String parsedConditionString = ch.poole.openinghoursparser.Util.rulesToOpeningHoursString(rules);
                    return ConditionState.UNEVALUATED.setCondition(new Condition(parsedConditionString, true));
                }
            }
            @Override
            public ConditionState checkCondition(Condition condition) {
                if (condition.isOpeningHours())
                    return checkCondition(condition.toString()); // attempt to properly parse the condition
                else
                    return ConditionState.INVALID;
            }
        };
    }
    // ORS-GH MOD END

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


    // ORS-GH MOD START - additional code
    // attempt to parse the value with any of the registered parsers
    private ParsedCondition checkAtomicCondition(Condition condition, ParsedCondition parsedCondition) throws ParseException {
        parsedCondition.reset();
        try {
            for (ConditionalValueParser valueParser : valueParsers) {
                ConditionalValueParser.ConditionState conditionState = valueParser.checkCondition(condition);
                if (conditionState.isValid()) {
                    parsedCondition.setValid(true);
                    if (conditionState.isEvaluated()) {
                        parsedCondition.setEvaluated(true);
                        parsedCondition.setCheckPassed(conditionState.isCheckPassed());
                        break;
                    } else { // condition could not be evaluated but might evaluate to true during query
                        parsedCondition.setLazyEvaluated(true);
                        parsedCondition.getLazyEvaluatedConditions().add(conditionState.getCondition());
                    }
                }
            }
        }
        catch (ParseException e) {
            throw e;
        }
        finally {
            return parsedCondition;
        }
    }

    class ParsedCondition {
        private boolean valid;
        private boolean evaluated;
        private boolean checkPassed;
        private boolean lazyEvaluated;
        private ArrayList<Condition> lazyEvaluatedConditions = new ArrayList<Condition>();

        void reset() {
            valid = evaluated = checkPassed = lazyEvaluated = false;
        }

        void setValid(boolean valid) {
            this.valid = valid;
        }

        void setEvaluated(boolean evaluated) {
            this.evaluated = evaluated;
        }

        void setCheckPassed(boolean checkPassed) {
            this.checkPassed = checkPassed;
        }

        void setLazyEvaluated(boolean lazyEvaluated) {
            this.lazyEvaluated = lazyEvaluated;
        }

        boolean isValid() {
            return valid;
        }

        boolean isEvaluated() {
            return evaluated;
        }

        boolean isCheckPassed() {
            return checkPassed;
        }

        boolean isLazyEvaluated() {
            return lazyEvaluated;
        }

        boolean isInvalidOrFalse() {
            return !valid || (!lazyEvaluated && evaluated && !checkPassed);
        }

        ArrayList<Condition> getLazyEvaluatedConditions() {
            return lazyEvaluatedConditions;
        }
    }

    // all of the combined conditions need to be met
    private ParsedCondition checkCombinedCondition(Restriction restriction) throws ParseException {
        ParsedCondition parsedCondition = new ParsedCondition();
        // combined conditions, must be all matched
        boolean checkPassed = true;
        boolean lazyEvaluated = false;
        for (Condition condition: restriction.getConditions()) {
            parsedCondition = checkAtomicCondition(condition, parsedCondition);
            checkPassed = checkPassed && parsedCondition.isCheckPassed();
            if (parsedCondition.isInvalidOrFalse()) {
                lazyEvaluated = false;
                break;
            }
            if (parsedCondition.isLazyEvaluated())
                lazyEvaluated = true;
        }
        parsedCondition.setLazyEvaluated(lazyEvaluated);
        parsedCondition.setCheckPassed(checkPassed);
        return parsedCondition;
    }
    // ORS-GH MOD END

// ORS-GH MOD START- replace method
//    public boolean checkCondition(String conditionalTag) throws ParseException {
//        if (conditionalTag == null || conditionalTag.isEmpty() || !conditionalTag.contains("@"))
//            return false;
//
//        if (conditionalTag.contains(";")) {
//            if (enabledLogs)
//                logger.warn("We do not support multiple conditions yet: " + conditionalTag);
//            return false;
//        }
//
//        String[] conditionalArr = conditionalTag.split("@");
//
//        if (conditionalArr.length != 2)
//            throw new IllegalStateException("could not split this condition: " + conditionalTag);
//
//        String restrictiveValue = conditionalArr[0].trim();
//        if (!restrictedTags.contains(restrictiveValue))
//            return false;
//
//        String conditionalValue = conditionalArr[1];
//        conditionalValue = conditionalValue.replace('(', ' ');
//        conditionalValue = conditionalValue.replace(')', ' ');
//        conditionalValue = conditionalValue.trim();
//
//        for (ConditionalValueParser valueParser : valueParsers) {
//            ConditionalValueParser.ConditionState c = valueParser.checkCondition(conditionalValue);
//            if (c.isValid())
//                return c.isCheckPassed();
//        }
//        return false;
//    }

    public Result checkCondition(String tagValue) throws ParseException {
        Result result = new Result();
        if (tagValue == null || tagValue.isEmpty() || !tagValue.contains("@"))
            return result;

        List<Restriction> parsedRestrictions = new ArrayList<>();

        try {
            ConditionalRestrictionParser parser = new ConditionalRestrictionParser(new ByteArrayInputStream(tagValue.getBytes()));

            List<Restriction> restrictions = parser.restrictions();

            // iterate over restrictions starting from the last one in order to match to the most specific one
            for (int i = restrictions.size() - 1 ; i >= 0; i--) {
                Restriction restriction = restrictions.get(i);

                String restrictionValue = restriction.getValue();

                if (hasRestrictedValues()) {
                    if (restrictedTags.contains(restrictionValue))
                        restrictionValue = simpleValue;
                    else
                        continue;
                }
                else {
                    result.setRestrictions(restrictionValue);
                }

                ParsedCondition parsedConditions = checkCombinedCondition(restriction);
                boolean checkPassed = parsedConditions.isCheckPassed();
                result.setCheckPassed(result.isCheckPassed() || checkPassed);

                // check for unevaluated conditions
                if (!parsedConditions.isLazyEvaluated()) {
                    if (checkPassed)
                        return result; // terminate once the first matching condition which can be fully evaluated is encountered
                }
                else {
                    parsedRestrictions.add(0, new Restriction(restrictionValue, new Conditions(parsedConditions.getLazyEvaluatedConditions(), restriction.inParen())));
                }
            }
        } catch (ch.poole.conditionalrestrictionparser.ParseException e) {
            if (enabledLogs)
                logger.warn("Parser exception for " + tagValue + " " + e.toString());
            return result;
        }

        if (!parsedRestrictions.isEmpty()) {
            result.setRestrictions(Util.restrictionsToString(parsedRestrictions));
            result.setLazyEvaluated(true);
        }

        return result;
    }
    // ORS-GH MOD END

    protected static double parseNumber(String str) {
        int untilIndex = str.length() - 1;
        for (; untilIndex >= 0; untilIndex--) {
            if (Character.isDigit(str.charAt(untilIndex)))
                break;
        }
        return Double.parseDouble(str.substring(0, untilIndex + 1));
    }

    // ORS-GH MOD START - additional method
    private boolean hasRestrictedValues() {
        return !( restrictedTags ==null || restrictedTags.isEmpty() );
    }
    // ORS-GH MOD END

    // ORS-GH MOD START - additional class
    class Result {
        private boolean checkPassed;
        private boolean lazyEvaluated;
        private String restrictions;

        boolean isCheckPassed() {
            return checkPassed;
        }

        void setCheckPassed(boolean checkPassed) {
            this.checkPassed = checkPassed;
        }

        boolean isLazyEvaluated() {
            return lazyEvaluated;
        }

        void setLazyEvaluated(boolean lazyEvaluated) {
            this.lazyEvaluated = lazyEvaluated;
        }

        String getRestrictions() {
            return restrictions;
        }

        void setRestrictions(String restrictions) {
            this.restrictions = restrictions;
        }
    }
    // ORS-GH MOD END
}
