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

import com.graphhopper.coll.MapEntry;
import com.graphhopper.util.details.PathDetail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds a PathDetail list, from values and intervals of a Path.
 *
 * @author Robin Boldt
 */
public abstract class AbstractPathDetailsBuilder implements PathDetailsBuilder {

    private final String name;
    private boolean isOpen = false;
    private PathDetail currentDetail;
    private final List<PathDetail> pathDetails = new ArrayList<>();

    public AbstractPathDetailsBuilder(String name) {
        this.name = name;
    }


    /**
     * The value of the Path at this moment, that should be stored in the PathDetail when calling startInterval
     */
    protected abstract Object getCurrentValue();

    /**
     * It is only possible to open one interval at a time. Calling <code>startInterval</code> when
     * the interval is already open results in an Exception.
     *
     * @param firstIndex the index the PathDetail starts
     */
    public void startInterval(int firstIndex) {
        Object value = getCurrentValue();
        if (isOpen)
            throw new IllegalStateException("PathDetailsBuilder is already in an open state with value: " + currentDetail.getValue()
                    + " trying to open a new one with value: " + value);

        currentDetail = new PathDetail(value);
        currentDetail.setFirst(firstIndex);
        isOpen = true;
    }

    /**
     * Ending intervals multiple times is safe, we only write the interval if it was open and not empty.
     * Writes the interval to the pathDetails
     *
     * @param lastIndex the index the PathDetail ends
     */
    public void endInterval(int lastIndex) {
        if (isOpen) {
            currentDetail.setLast(lastIndex);
            pathDetails.add(currentDetail);
        }
        isOpen = false;
    }

    public Map.Entry<String, List<PathDetail>> build() {
        return new MapEntry(getName(), pathDetails);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }
}