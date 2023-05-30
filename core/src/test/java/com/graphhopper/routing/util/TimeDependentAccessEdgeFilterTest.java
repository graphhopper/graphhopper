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

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.Test;

import java.time.Month;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Andrzej Oles
 */

public class TimeDependentAccessEdgeFilterTest {
    private final CarFlagEncoder encoder = new CarFlagEncoder();
    private final EncodingManager encodingManager = EncodingManager.create(encoder);
    private final GraphHopperStorage graph = new GraphBuilder(encodingManager).create();
    private final NodeAccess nodeAccess = graph.getNodeAccess();
    private final TimeDependentEdgeFilter filter;

    public TimeDependentAccessEdgeFilterTest() {
        nodeAccess.setNode(0, 52, 13);
        nodeAccess.setNode(1, 53, 14);
        filter = new TimeDependentAccessEdgeFilter(graph, encoder);
    }

    private EdgeIteratorState createConditionalEdge(boolean closed, String conditional) {
        ReaderWay way = new ReaderWay(0);
        way.setTag("highway", "primary");
        if (closed) way.setTag("access", "no");
        way.setTag("access:conditional", conditional);
        EncodingManager.AcceptWay acceptWay = new EncodingManager.AcceptWay();
        encodingManager.acceptWay(way, acceptWay);
        IntsRef flags = encodingManager.handleWayTags(way, acceptWay, IntsRef.EMPTY);
        EdgeIteratorState edge = graph.edge(0, 1).setFlags(flags);
        // store conditional
        List<EdgeIteratorState> createdEdges = new ArrayList<>();
        createdEdges.add(edge);
        graph.getConditionalAccess(encoder).addEdges(createdEdges, encoder.getConditionalTagInspector().getTagValue());
        return edge;
    }

    private EdgeIteratorState conditionallyOpenEdge(String conditional) {
        return createConditionalEdge(true, conditional);
    }

    private EdgeIteratorState conditionallyClosedEdge(String conditional) {
        return createConditionalEdge(false, conditional);
    }

    private long timeStamp(int year, Month month, int day, int hour, int minute) {
        ZonedDateTime zonedDateTime = ZonedDateTime.of(year, month.getValue(), day, hour, minute, 0, 0, ZoneId.of("Europe/Berlin"));
        return zonedDateTime.toInstant().toEpochMilli();
    }

    @Test
    public void SeasonalClosure() {
        EdgeIteratorState edge = conditionallyClosedEdge("no @ Dec-May");
        assertTrue(filter.accept(edge, timeStamp(2019, Month.NOVEMBER, 30, 23, 59)));
        assertFalse(filter.accept(edge, timeStamp(2019, Month.DECEMBER, 1, 0, 0)));
        assertFalse(filter.accept(edge, timeStamp(2019, Month.MAY, 31, 23, 59)));
        assertTrue(filter.accept(edge, timeStamp(2019, Month.JUNE, 1, 0, 0)));
    }

    @Test
    public void SeasonalOpening() {
        EdgeIteratorState edge = conditionallyOpenEdge("yes @ (Dec-Mar)");
        assertFalse(filter.accept(edge, timeStamp(2019, Month.NOVEMBER, 30, 23, 59)));
        assertTrue(filter.accept(edge, timeStamp(2019, Month.DECEMBER, 1, 0, 0)));
        assertTrue(filter.accept(edge, timeStamp(2019, Month.MARCH, 31, 23, 59)));
        assertFalse(filter.accept(edge, timeStamp(2019, Month.APRIL, 1, 0, 0)));
    }

    @Test
    public void SeasonalClosureWithDates() {
        EdgeIteratorState edge = conditionallyClosedEdge("no @ (Mar 15-Jun 15)");
        assertTrue(filter.accept(edge, timeStamp(2019, Month.MARCH, 14, 23, 59)));
        assertFalse(filter.accept(edge, timeStamp(2019, Month.MARCH, 15, 0, 0)));
        assertFalse(filter.accept(edge, timeStamp(2019, Month.JUNE, 15, 23, 59)));
        assertTrue(filter.accept(edge, timeStamp(2019, Month.JUNE, 16, 0, 0)));
    }

    @Test
    public void SeasonalClosureWeeks() {
        EdgeIteratorState edge = conditionallyClosedEdge("no @ week 46-20");
        // 46th week of 2019 starts on Monday 11th Nov
        assertTrue(filter.accept(edge, timeStamp(2019, Month.NOVEMBER, 10, 23, 59)));
        assertFalse(filter.accept(edge, timeStamp(2019, Month.NOVEMBER, 11, 0, 0)));
        // 20th week of 2020 ends on Sunday 17th May
        assertFalse(filter.accept(edge, timeStamp(2020, Month.MAY, 17, 23, 59)));
        assertTrue(filter.accept(edge, timeStamp(2020, Month.MAY, 18, 0, 0)));
    }

    @Test
    public void ClosureWithinSameMonth() {
        EdgeIteratorState edge = conditionallyClosedEdge("no @ (Mar 13-Mar 19)");
        assertTrue(filter.accept(edge, timeStamp(2019, Month.MARCH, 12, 23, 59)));
        assertFalse(filter.accept(edge, timeStamp(2019, Month.MARCH, 13, 0, 0)));
        assertFalse(filter.accept(edge, timeStamp(2019, Month.MARCH, 19, 23, 59)));
        assertTrue(filter.accept(edge, timeStamp(2019, Month.MARCH, 20, 0, 0)));
    }

    @Test
    public void DayClosure() {
        EdgeIteratorState edge = conditionallyClosedEdge("no @ (10:00-18:00)");
        assertTrue(filter.accept(edge, timeStamp(2019, Month.JANUARY, 1, 9, 59)));
        assertFalse(filter.accept(edge, timeStamp(2019, Month.JANUARY, 1, 10, 00)));
        assertFalse(filter.accept(edge, timeStamp(2019, Month.JANUARY, 1, 17, 59)));
        assertTrue(filter.accept(edge, timeStamp(2019, Month.JANUARY, 1, 18, 1)));
    }

    @Test
    public void NightClosure() {
        EdgeIteratorState edge = conditionallyClosedEdge("no @ (18:00-10:00)");
        assertTrue(filter.accept(edge, timeStamp(2019, Month.JANUARY, 1, 17, 59)));
        assertFalse(filter.accept(edge, timeStamp(2019, Month.JANUARY, 1, 20, 0)));
        assertFalse(filter.accept(edge, timeStamp(2019, Month.JANUARY, 1, 18, 0)));
        assertFalse(filter.accept(edge, timeStamp(2019, Month.JANUARY, 1, 9, 59)));
        assertTrue(filter.accept(edge, timeStamp(2019, Month.JANUARY, 1, 10, 01)));
    }

    @Test
    public void seasonalVariationOfClosureTime() {
        EdgeIteratorState edge = conditionallyClosedEdge("no @ (Oct-Mar 18:00-08:00, Apr-Sep 21:00-07:00)");
        assertTrue(filter.accept(edge, timeStamp(2019, Month.SEPTEMBER, 30, 20, 00)));
        assertTrue(filter.accept(edge, timeStamp(2019, Month.OCTOBER, 1, 7, 30)));
        assertFalse(filter.accept(edge, timeStamp(2019, Month.OCTOBER, 1, 20, 00)));
        assertFalse(filter.accept(edge, timeStamp(2019, Month.APRIL, 1, 7, 30)));
    }

    @Test
    public void seasonalAndNightClosure() {
        EdgeIteratorState edge = conditionallyClosedEdge("no @ (Nov-Jun; Jun-Aug 00:00-07:00,19:00-24:00; Sep-Nov 00:00-08:00,18:00-24:00)");
        assertTrue(filter.accept(edge, timeStamp(2019, Month.OCTOBER, 31, 12, 00)));
        assertFalse(filter.accept(edge, timeStamp(2019, Month.NOVEMBER, 1, 12, 00)));
        assertTrue(filter.accept(edge, timeStamp(2019, Month.JULY, 1, 7, 30)));
        assertFalse(filter.accept(edge, timeStamp(2019, Month.SEPTEMBER, 1, 7, 30)));
    }

    @Test
    public void seasonalWeekdayDependentClosure() {
        EdgeIteratorState edge = conditionallyClosedEdge("no @ (Apr 01-Sep 30: Mo-Sa 00:00-06:00,20:00-24:00; Apr 01-Sep 30: Su,PH 00:00-24:00)");
        assertTrue(filter.accept(edge, timeStamp(2020, Month.JANUARY, 1, 6, 00)));
        assertTrue(filter.accept(edge, timeStamp(2020, Month.JANUARY, 1, 12, 00)));
        // 01.06.2020 is a Monday
        assertFalse(filter.accept(edge, timeStamp(2020, Month.JUNE, 1, 6, 00)));
        assertTrue(filter.accept(edge, timeStamp(2020, Month.JUNE, 1, 12, 00)));
        assertFalse(filter.accept(edge, timeStamp(2020, Month.JUNE, 7, 6, 00)));
        assertFalse(filter.accept(edge, timeStamp(2020, Month.JUNE, 7, 12, 00)));
    }
    
    @Test
    public void MultipleClosureTimes() {
        EdgeIteratorState edge = conditionallyClosedEdge("no @ (21:00-07:00,15:30-17:00)");
        assertFalse(filter.accept(edge, timeStamp(2019, Month.JANUARY, 1, 0, 00)));
        assertTrue(filter.accept(edge, timeStamp(2019, Month.JANUARY, 1, 12, 00)));
        assertFalse(filter.accept(edge, timeStamp(2019, Month.JANUARY, 1, 16, 00)));
    }

    @Test
    public void WorkdaysClosure() {
        EdgeIteratorState edge = conditionallyClosedEdge("no @ (Mo-Fr 07:00-17:00)");
        // 01.06.2020 is a Monday
        assertTrue(filter.accept(edge, timeStamp(2020, Month.JUNE, 1, 6, 00)));
        assertFalse(filter.accept(edge, timeStamp(2020, Month.JUNE, 1, 8, 00)));
        assertFalse(filter.accept(edge, timeStamp(2020, Month.JUNE, 2, 8, 00)));
        assertFalse(filter.accept(edge, timeStamp(2020, Month.JUNE, 3, 8, 00)));
        assertFalse(filter.accept(edge, timeStamp(2020, Month.JUNE, 4, 8, 00)));
        assertFalse(filter.accept(edge, timeStamp(2020, Month.JUNE, 5, 8, 00)));
        assertTrue(filter.accept(edge, timeStamp(2020, Month.JUNE, 6, 8, 00)));
        assertTrue(filter.accept(edge, timeStamp(2020, Month.JUNE, 7, 8, 00)));
    }

    @Test
    public void WorkdaysClosureMultipleTimes() {
        EdgeIteratorState edge = conditionallyClosedEdge("no @ (Mo,Fr 06:00-10:30, 15:00-24:00)");
        // 01.06.2020 is a Monday
        assertTrue(filter.accept(edge, timeStamp(2020, Month.JUNE, 1, 0, 00)));
        assertFalse(filter.accept(edge, timeStamp(2020, Month.JUNE, 1, 6, 00)));
        assertTrue(filter.accept(edge, timeStamp(2020, Month.JUNE, 1, 12, 00)));
        assertFalse(filter.accept(edge, timeStamp(2020, Month.JUNE, 1, 18, 00)));
        assertTrue(filter.accept(edge, timeStamp(2020, Month.JUNE, 6, 6, 00)));
    }

    @Test
    public void SundayAndPublicHolidayClosure() {
        EdgeIteratorState edge = conditionallyClosedEdge("no @ (Su,PH 11:00-18:00)");
        // 01.06.2020 is a Monday
        assertTrue(filter.accept(edge, timeStamp(2020, Month.JUNE, 1, 8, 00)));
        assertTrue(filter.accept(edge, timeStamp(2020, Month.JUNE, 1, 12, 00)));
        assertTrue(filter.accept(edge, timeStamp(2020, Month.JUNE, 7, 8, 00)));
        assertFalse(filter.accept(edge, timeStamp(2020, Month.JUNE, 7, 12, 00)));
    }

    @Test
    public void WorkdaysAndWeekendsNightClosure() {
        EdgeIteratorState edge = conditionallyClosedEdge("no @ (Mo-Fr 20:00-02:00;Sa 16:00-02:00;Su 11:00-02:00)");
        // 01.06.2020 is a Monday
        assertFalse(filter.accept(edge, timeStamp(2020, Month.JUNE, 1, 1, 00)));
        assertTrue(filter.accept(edge, timeStamp(2020, Month.JUNE, 1, 12, 00)));
        assertFalse(filter.accept(edge, timeStamp(2020, Month.JUNE, 1, 22, 00)));
        assertFalse(filter.accept(edge, timeStamp(2020, Month.JUNE, 6, 1, 00)));
        assertTrue(filter.accept(edge, timeStamp(2020, Month.JUNE, 6, 12, 00)));
        assertFalse(filter.accept(edge, timeStamp(2020, Month.JUNE, 6, 22, 00)));
        assertFalse(filter.accept(edge, timeStamp(2020, Month.JUNE, 7, 1, 00)));
        assertFalse(filter.accept(edge, timeStamp(2020, Month.JUNE, 7, 12, 00)));
        assertFalse(filter.accept(edge, timeStamp(2020, Month.JUNE, 7, 22, 00)));
    }

    @Test
    public void WorkdaysAndWeekendsNightOpening() {
        EdgeIteratorState edge = conditionallyOpenEdge("yes @ (Mo-Fr 17:30-06:30;Sa-Su 00:00-24:00)");
        // Monday
        assertFalse(filter.accept(edge, timeStamp(2020, Month.JUNE, 1, 6, 00)));
        assertFalse(filter.accept(edge, timeStamp(2020, Month.JUNE, 1, 12, 00)));
        assertTrue(filter.accept(edge, timeStamp(2020, Month.JUNE, 1, 18, 00)));
        // Tuesday
        assertTrue(filter.accept(edge, timeStamp(2020, Month.JUNE, 2, 6, 00)));
        assertFalse(filter.accept(edge, timeStamp(2020, Month.JUNE, 2, 12, 00)));
        assertTrue(filter.accept(edge, timeStamp(2020, Month.JUNE, 2, 18, 00)));
        // Saturday
        assertTrue(filter.accept(edge, timeStamp(2020, Month.JUNE, 6, 6, 00)));
        assertTrue(filter.accept(edge, timeStamp(2020, Month.JUNE, 6, 12, 00)));
        assertTrue(filter.accept(edge, timeStamp(2020, Month.JUNE, 6, 18, 00)));
    }

    @Test
    public void TwoConditions() {
        EdgeIteratorState edge = conditionallyClosedEdge("no @ (Nov-May); no @ (20:00-07:00)");
        // First Condition
        assertTrue(filter.accept(edge, timeStamp(2019, Month.OCTOBER, 31, 12, 00)));
        assertFalse(filter.accept(edge, timeStamp(2019, Month.NOVEMBER, 1, 12, 00)));
        assertFalse(filter.accept(edge, timeStamp(2020, Month.MAY, 31, 12, 00)));
        assertTrue(filter.accept(edge, timeStamp(2020, Month.JUNE, 1, 12, 00)));
        // Second Condition
        assertFalse(filter.accept(edge, timeStamp(2019, Month.OCTOBER, 31, 23, 59)));
        assertFalse(filter.accept(edge, timeStamp(2020, Month.JUNE, 1, 0, 01)));
    }

    @Test
    public void LastSundayOfMay() {
        EdgeIteratorState edge = conditionallyClosedEdge("no @ (May Su[-1] 09:30-18:00)");
        // Last Sunday of May
        assertFalse(filter.accept(edge, timeStamp(2020, Month.MAY, 31, 12, 00)));
        // First Sunday of June
        assertTrue(filter.accept(edge, timeStamp(2020, Month.JUNE, 7, 12, 00)));
    }

    @Test
    public void ClosedOnCertainDate() {
        EdgeIteratorState edge = conditionallyClosedEdge("no @ (2018 May 25 20:00-24:00, 2018 May 26 00:00-30:00)");
        assertTrue(filter.accept(edge, timeStamp(2018, Month.MAY, 25, 12, 00)));
        assertFalse(filter.accept(edge, timeStamp(2018, Month.MAY, 25, 22, 00)));
        assertFalse(filter.accept(edge, timeStamp(2018, Month.MAY, 26, 12, 00)));
        assertFalse(filter.accept(edge, timeStamp(2018, Month.MAY, 27, 6, 00)));
        assertTrue(filter.accept(edge, timeStamp(2018, Month.MAY, 27, 12, 00)));
    }

}