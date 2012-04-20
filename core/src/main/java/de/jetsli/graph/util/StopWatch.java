/**
 * Copyright (C) 2010 Peter Karich info@jetsli.de
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.jetsli.graph.util;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class StopWatch {

    private long lastTime;
    private long time;
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
        lastTime = System.currentTimeMillis();
        return this;
    }

    public StopWatch stop() {
        if (lastTime < 0)
            return this;
        time += System.currentTimeMillis() - lastTime;
        lastTime = -1;
        return this;
    }

    /**
     * @return the delta time in milliseconds
     */
    public long getTime() {
        return time;
    }

    @Override
    public String toString() {
        String str = "";
        if(!name.isEmpty())
            str += name + " ";
        
        return str + "time:" + getSeconds();
    }

    public float getSeconds() {
        return time / 1000f;
    }
}
