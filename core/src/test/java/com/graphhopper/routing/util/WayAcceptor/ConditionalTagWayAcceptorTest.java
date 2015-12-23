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
package com.graphhopper.routing.util.WayAcceptor;

import com.graphhopper.reader.OSMWay;
import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


/**
 * @author Robin boldt
 */
public class ConditionalTagWayAcceptorTest extends CalendarBasedTest
{

    ConditionalTagWayAcceptor acceptor;

    @Before
    public void setup(){
        Set<String> restrictedValues = new HashSet<String>();
        restrictedValues.add("private");
        restrictedValues.add("agricultural");
        restrictedValues.add("forestry");
        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("delivery");
        restrictedValues.add("military");
        restrictedValues.add("emergency");

        Calendar cal = getCalendar(2014, Calendar.MARCH, 10);

        Set<String> conditionalTags = new HashSet<String>();
        conditionalTags.add("vehicle:conditional");
        conditionalTags.add("access:conditional");

        acceptor = new ConditionalTagWayAcceptor(cal, conditionalTags, restrictedValues);
    }

    @Test
    public void testConditionalAccept()
    {
        OSMWay way = new OSMWay(1);
        way.setTag("vehicle:conditional", "no @ (Aug 10-Aug 14)");
        assertTrue(acceptor.accept(way));
    }

    @Test
    public void testConditionalReject()
    {
        OSMWay way = new OSMWay(1);
        way.setTag("vehicle:conditional", "no @ (Mar 10-Aug 14)");
        assertFalse(acceptor.accept(way));
    }
}
