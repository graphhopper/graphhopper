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
package com.graphhopper.reader.osm;

import com.graphhopper.reader.OSMWay;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


/**
 * @author Robin Boldt
 */
public class ConditionalTagWayAcceptorTest extends CalendarBasedTest
{

    @Test
    public void testConditionalAccept()
    {
        Calendar cal = getCalendar(2014, Calendar.MARCH, 10);
        ConditionalTagWayAcceptor acceptor = new ConditionalTagWayAcceptor(cal, getSampleConditionalTags(), getSampleRestrictedValues(), false);
        OSMWay way = new OSMWay(1);
        way.setTag("vehicle:conditional", "no @ (Aug 10-Aug 14)");
        assertTrue(acceptor.accept(way));
    }

    @Test
    public void testConditionalAcceptNextYear()
    {
        Calendar cal = getCalendar(2014, Calendar.MARCH, 10);
        ConditionalTagWayAcceptor acceptor = new ConditionalTagWayAcceptor(cal, getSampleConditionalTags(), getSampleRestrictedValues(), false);
        OSMWay way = new OSMWay(1);
        way.setTag("vehicle:conditional", "no @ (2013 Mar 1-2013 Mar 31)");
        assertTrue(acceptor.accept(way));
    }

    @Test
    public void testConditionalReject()
    {
        Calendar cal = getCalendar(2014, Calendar.MARCH, 10);
        ConditionalTagWayAcceptor acceptor = new ConditionalTagWayAcceptor(cal, getSampleConditionalTags(), getSampleRestrictedValues(), false);
        OSMWay way = new OSMWay(1);
        way.setTag("vehicle:conditional", "no @ (Mar 10-Aug 14)");
        assertFalse(acceptor.accept(way));
    }

    @Test
    public void testConditionalAllowance()
    {
        Calendar cal = getCalendar(2014, Calendar.MARCH, 10);
        ConditionalTagWayAcceptor acceptor = new ConditionalTagWayAcceptor(cal, getSampleConditionalTags(), getSamplePermissiveValues(), true);
        OSMWay way = new OSMWay(1);
        way.setTag("vehicle:conditional", "yes @ (Mar 10-Aug 14)");
        assertTrue(acceptor.accept(way));
    }

    @Test
    public void testConditionalAllowanceReject()
    {
        Calendar cal = getCalendar(2014, Calendar.MARCH, 10);
        ConditionalTagWayAcceptor acceptor = new ConditionalTagWayAcceptor(cal, getSampleConditionalTags(), getSamplePermissiveValues(), true);
        OSMWay way = new OSMWay(1);
        way.setTag("vehicle:conditional", "no @ (Mar 10-Aug 14)");
        assertFalse(acceptor.accept(way));
    }

    @Test
    public void testConditionalSingleDay()
    {
        Calendar cal = getCalendar(2015, Calendar.DECEMBER, 27);
        ConditionalTagWayAcceptor acceptor = new ConditionalTagWayAcceptor(cal, getSampleConditionalTags(), getSampleRestrictedValues(), false);
        OSMWay way = new OSMWay(1);
        way.setTag("vehicle:conditional", "no @ (Su)");
        assertFalse(acceptor.accept(way));
    }

    @Test
    public void testConditionalAllowanceSingleDay()
    {
        Calendar cal = getCalendar(2015, Calendar.DECEMBER, 27);
        ConditionalTagWayAcceptor acceptor = new ConditionalTagWayAcceptor(cal, getSampleConditionalTags(), getSamplePermissiveValues(), true);
        OSMWay way = new OSMWay(1);
        way.setTag("vehicle:conditional", "yes @ (Su)");
        assertTrue(acceptor.accept(way));
    }

    private static Set<String> getSampleRestrictedValues(){
        Set<String> restrictedValues = new HashSet<String>();
        restrictedValues.add("private");
        restrictedValues.add("agricultural");
        restrictedValues.add("forestry");
        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("delivery");
        restrictedValues.add("military");
        restrictedValues.add("emergency");
        return restrictedValues;
    }

    private static Set<String> getSamplePermissiveValues(){
        Set<String> restrictedValues = new HashSet<String>();
        restrictedValues.add("yes");
        restrictedValues.add("permissive");
        return restrictedValues;
    }

    private static List<String> getSampleConditionalTags(){
        List<String> conditionalTags = new ArrayList<String>();
        conditionalTags.add("vehicle");
        conditionalTags.add("access");
        return conditionalTags;
    }



}
