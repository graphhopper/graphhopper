/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
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
 * @author Robin Boldt
 */
public class ConditionalParser
{

    private final Set<String> restrictedTags;
    private static final Logger logger = LoggerFactory.getLogger(ConditionalParser.class);
    private final boolean enabledLogs;

    public ConditionalParser( Set<String> restrictedTags )
    {
        this(restrictedTags, false);
    }

    public ConditionalParser( Set<String> restrictedTags, boolean enabledLogs )
    {
        this.restrictedTags = restrictedTags;
        this.enabledLogs = enabledLogs;
    }

    public DateRange getDateRange( String conditionalTag ) throws ParseException
    {

        if (conditionalTag == null || conditionalTag.isEmpty() || !conditionalTag.contains("@"))
            return null;

        if (conditionalTag.contains(";"))
        {
            // TODO #374
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

        return DateRangeParser.parseDateRange(conditional);
    }

}
