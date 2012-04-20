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
package de.jetsli.graph.storage;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class GeoLocationSimple implements GeoLocation {

    // do not let change id after construction!
    private final int id;
    private String name;
    private float lon;
    private float lat;

    public GeoLocationSimple(int id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override public int id() {
        return id;
    }

    @Override public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass())
            return false;

        return this.id == ((GeoLocationSimple) obj).id;
    }

    @Override public int hashCode() {
        return this.id;
    }

    @Override public String toString() {
        if (name == null)
            return Integer.toString(id);

        return name + " (" + id + ")";
    }

    @Override public void lon(float fl) {
        lon = fl;
    }

    @Override public void lat(float fl) {
        lat = fl;
    }

    @Override public float lon() {
        return lon;
    }

    @Override public float lat() {
        return lat;
    }
}
