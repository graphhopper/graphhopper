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

public class LanesInfo {
    /**
     * Direction as string based on the OSM lane tags.
     */
    public final String direction;

    /**
     * Defines if the lane can be used for the next turn instruction.
     */
    public boolean active;

    public LanesInfo(String direction) {
        if (direction == null) throw new IllegalArgumentException("direction cannot be null");
        this.direction = direction;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getDirection() {
        return direction;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public String toString() {
        return "{direction:" + direction + ", " + "active:" + active + "}";
    }
}
