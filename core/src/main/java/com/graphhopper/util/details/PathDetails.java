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
package com.graphhopper.util.details;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the details for a path
 *
 * @author Robin Boldt
 */
public class PathDetails {

    private final String name;
    private boolean isOpen = false;

    private int startRef;
    private Object value;

    //TODO is there a better data structure?
    private Map<Object, List<int[]>> pathDetails = new HashMap<>();

    public PathDetails(String name) {
        this.name = name;
    }

    /**
     * It is only possible to open one interval at a time.
     *
     * @param value
     * @param startRef
     */
    public void startInterval(Object value, int startRef) {
        if(isOpen){
            throw new IllegalStateException("Path details is already open with value: "+this.value+" and startRef: "+
                    this.startRef+ " trying to open a new one with value: "+value+" and startRef: "+startRef);
        }
        this.value = value;
        this.startRef = startRef;
        isOpen = true;
    }

    /**
     * Ending intervals multiple times is safe, we only write the interval if it was opened.
     *
     * Writes the interval to the pathDetails
     *
     * @param endRef The point ref of the end
     */
    public void endInterval(int endRef) {
        // We don't want enmpty interfals ,therefore the refs need to be different
        if (isOpen && startRef != endRef) {
            List<int[]> list;
            if(pathDetails.containsKey(value)){
                list = pathDetails.get(value);
            }else{
                list = new ArrayList<>();
            }
            list.add(new int[]{startRef, endRef});
            pathDetails.put(value, list);
        }
        isOpen = false;
    }

    public Map<Object, List<int[]>> getDetails(){
        return pathDetails;
    }

    public void merge(PathDetails pD){
        if(!this.name.equals(pD.getName())){
            throw new IllegalArgumentException("Only PathDetails with the same name can be merged");
        }
        Map<Object, List<int[]>> otherDetails = pD.getDetails();
        for (Object object: otherDetails.keySet()) {
            List<int[]> list;
            if(this.pathDetails.containsKey(object)){
                list = this.pathDetails.get(object);
                list.addAll(otherDetails.get(object));
            }else {
                list = otherDetails.get(object);
            }
            this.pathDetails.put(object, list);
        }
    }

    public String getName(){
        return this.name;
    }
}
