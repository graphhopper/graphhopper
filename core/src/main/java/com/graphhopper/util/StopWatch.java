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

/**
 * Make simple speed measurements possible.
 * <p>
 *
 * @author Peter Karich
 */
public class StopWatch {
    private long lastTime;
    private long nanoTime;
    private String name = "";

    public StopWatch(String name) {
        this.name = name;
    }

    public StopWatch() {
    }

    public StopWatch setName(String name) {
        this.name = name;
        return this;
    }

    public StopWatch start() {
        lastTime = System.nanoTime();
        return this;
    }

    public StopWatch stop() {
        if (lastTime < 0)
            return this;

        nanoTime += System.nanoTime() - lastTime;
        lastTime = -1;
        return this;
    }

    /**
     * @return the time delta in milliseconds
     */
    public long getTime() {
        return nanoTime / 1000000;
    }

    public long getNanos() {
        return nanoTime;
    }

    @Override
    public String toString() {
        String str = "";
        if (!Helper.isEmpty(name)) {
            str += name + " ";
        }

        return str + "time:" + getSeconds();
    }

    public float getSeconds() {
        return nanoTime / 1e9f;
    }
}
