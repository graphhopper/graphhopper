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

package com.graphhopper.util;

// todonow: where should this class reside? currently its in graphhopper-api, because this way we can use it
// in GraphHopperConfigDeserializer
public class Profile {
    private String name;
    private String vehicle;
    private String weighting;
    private boolean turnCosts;
    private PMap hints;

    public Profile() {
        this.name = "car";
        this.vehicle = "car";
        this.weighting = "fastest";
        this.turnCosts = false;
        this.hints = new PMap();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVehicle() {
        return vehicle;
    }

    public void setVehicle(String vehicle) {
        this.vehicle = vehicle;
    }

    public String getWeighting() {
        return weighting;
    }

    public void setWeighting(String weighting) {
        this.weighting = weighting;
    }

    public boolean isTurnCosts() {
        return turnCosts;
    }

    public void setTurnCosts(boolean turnCosts) {
        this.turnCosts = turnCosts;
    }

    public PMap hints() {
        return hints;
    }

    public void putHint(String key, Object value) {
        this.hints.put(key, value);
    }

    @Override
    public String toString() {
        return "name=" + name + "|vehicle=" + vehicle + "|weighting=" + weighting + "|turnCosts=" + turnCosts + "|hints=" + hints;
    }
}
