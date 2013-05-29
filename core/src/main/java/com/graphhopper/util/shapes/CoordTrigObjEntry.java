/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.util.shapes;

/**
 *
 * @author Peter Karich
 */
public class CoordTrigObjEntry<T> extends CoordTrig<T> {

    private T v;

    public CoordTrigObjEntry() {
    }

    public CoordTrigObjEntry(T o, double lat, double lon) {
        super(lat, lon);
        this.v = o;
    }

    @Override public void setValue(T t) {
        v = t;
    }

    @Override public T getValue() {
        return v;
    }

    @Override public String toString() {
        return super.toString() + " value:" + v;
    }
}
