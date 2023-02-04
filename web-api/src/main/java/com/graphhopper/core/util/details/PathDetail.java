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
package com.graphhopper.core.util.details;

/**
 * A detail information of a Path
 *
 * @author Robin Boldt
 */
public class PathDetail {
    private final Object value;
    private int first;
    private int last;

    public PathDetail(Object value) {
        this.value = value;
    }

    /**
     * @return the value of this PathDetail. Can be null
     */
    public Object getValue() {
        return value;
    }

    public void setFirst(int first) {
        this.first = first;
    }

    public int getFirst() {
        return first;
    }

    public void setLast(int last) {
        this.last = last;
    }

    public int getLast() {
        return last;
    }

    public int getLength() {
        return last - first;
    }

    @Override
    public String toString() {
        return value + " [" + getFirst() + ", " + getLast() + "]";
    }
}
