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

package com.graphhopper.reader.gtfs;

import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class Request {
    private List<GHLocation> points;
    private Instant earliestDepartureTime;
    private int maxVisitedNodes = 1_000_000;
    private boolean profileQuery;
    private Boolean ignoreTransfers;
    private double betaTransfers = 0.0;
    private double betaWalkTime = 1.0;
    private Integer limitSolutions;
    private boolean arriveBy;
    private double walkSpeedKmH = 5.0;
    private int blockedRouteTypes;
    private Locale locale = Helper.getLocale("en");

    public Request(List<GHLocation> points, Instant departureTime) {
        this.points = points;
        this.earliestDepartureTime = departureTime;
    }

    public Request(double from_lat, double from_lon, double to_lat, double to_lon) {
        this.points = Arrays.asList(new GHPointLocation(new GHPoint(from_lat, from_lon)), new GHPointLocation(new GHPoint(to_lat, to_lon)));
    }

    public int getMaxVisitedNodes() {
        return maxVisitedNodes;
    }

    public void setMaxVisitedNodes(int maxVisitedNodes) {
        this.maxVisitedNodes = maxVisitedNodes;
    }

    public boolean isProfileQuery() {
        return profileQuery;
    }

    public void setProfileQuery(boolean profileQuery) {
        this.profileQuery = profileQuery;
    }

    public Boolean getIgnoreTransfers() {
        return ignoreTransfers;
    }

    public void setIgnoreTransfers(Boolean ignoreTransfers) {
        this.ignoreTransfers = ignoreTransfers;
    }

    public double getBetaTransfers() {
        return betaTransfers;
    }

    public void setBetaTransfers(double betaTransfers) {
        this.betaTransfers = betaTransfers;
    }

    public double getBetaWalkTime() {
        return betaWalkTime;
    }

    public void setBetaWalkTime(double betaWalkTime) {
        this.betaWalkTime = betaWalkTime;
    }

    public Integer getLimitSolutions() {
        return limitSolutions;
    }

    public void setLimitSolutions(Integer limitSolutions) {
        this.limitSolutions = limitSolutions;
    }

    public Instant getEarliestDepartureTime() {
        return earliestDepartureTime;
    }

    public void setEarliestDepartureTime(Instant earliestDepartureTime) {
        this.earliestDepartureTime = earliestDepartureTime;
    }

    public boolean isArriveBy() {
        return arriveBy;
    }

    public void setArriveBy(boolean arriveBy) {
        this.arriveBy = arriveBy;
    }

    public double getWalkSpeedKmH() {
        return walkSpeedKmH;
    }

    public void setWalkSpeedKmH(double walkSpeedKmH) {
        this.walkSpeedKmH = walkSpeedKmH;
    }

    public int getBlockedRouteTypes() {
        return blockedRouteTypes;
    }

    public void setBlockedRouteTypes(int blockedRouteTypes) {
        this.blockedRouteTypes = blockedRouteTypes;
    }

    public Locale getLocale() {
        return locale;
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    public List<GHLocation> getPoints() {
        return points;
    }

}
