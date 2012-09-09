/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.routing.util;

import java.util.Map;

/**
 * @author Peter Karich
 */
public class AcceptStreet {

    private boolean car;
    private boolean publicTransport;
    private boolean bike;
    private boolean foot;

    public AcceptStreet(boolean car, boolean publicTransport, boolean bike, boolean foot) {
        this.car = car;
        this.publicTransport = publicTransport;
        this.bike = bike;
        this.foot = foot;
    }

    public boolean acceptsCar() {
        return car;
    }

    public boolean acceptsPublicTransport() {
        return publicTransport;
    }

    public boolean acceptsBike() {
        return bike;
    }

    public boolean acceptsFoot() {
        return foot;
    }

    /**
     * Collect way properties of different kind of streets
     */
    public boolean handleWay(Map<String, Object> speed, String val) {
        boolean handled = false;
        if (foot) {
            if ("footway".equals(val) || "path".equals(val) || "steps".equals(val)
                    || "pedestrian".equals(val)) {
                handled = true;
                speed.put("foot", 5);
            }
        }
        if (bike) {
            // add bike support later
            // http://wiki.openstreetmap.org/wiki/Cycleway
            // http://wiki.openstreetmap.org/wiki/Map_Features#Cycleway
            // https://github.com/Tristramg/osm4routing/blob/master/parameters.cc
            // + TODO toFlags
            // + some foot paths?
            if ("cycleway".equals(val) || "path".equals(val)) {
                handled = true;
                speed.put("bike", 10);
            }
        }

        if (publicTransport) {
        }

        if (car) {
            Integer integ = CarStreetType.SPEED.get(val);
            if (integ != null) {
                handled = true;
                speed.put("car", integ);
            }
        }
        return handled;
    }

    /**
     * Convert properties to 4 byte flags. The most significant byte is reserved for car, then
     * public transport, then bike and the last one for foot.
     *
     * Every byte contains the speed and the possible direction.
     */
    public int toFlags(Map<String, Object> properties) {
        int flags = 0;
        boolean bothways = !"yes".equals(properties.get("oneway"));
        Integer integ;
        if (car) {
            integ = (Integer) properties.get("car");
            if (integ != null) {
                integ *= CarStreetType.FACTOR;
                flags = CarStreetType.flags(integ, bothways);
            }
        }
        // TODO if(publicTransport)
        if (bike) {
            integ = (Integer) properties.get("bike");
            if (integ != null) {
                // TODO
                // flags |= BikeEdgeFlags.flags(integ, bothway);
            }
        }
        // TODO if(foot)
        return flags;
    }
}
