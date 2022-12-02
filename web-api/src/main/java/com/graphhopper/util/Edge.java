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

public class Edge {
    protected PointList points;
    protected String name;
    protected double distance;
    protected int grade;
    protected long time;
    protected double weight;
    protected boolean reversed;

    /**
     * The points, distances and times have exactly the same count. The last point of this
     * instruction is not duplicated here and should be in the next one.
     */
    public Edge(String name, double distance, boolean reversed, long time, double weight) {
        this.name = name;
        this.distance = distance;
        this.reversed = reversed;
        this.time = time;
        this.weight = weight;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Distance in meter until new edge
     */
    public double getDistance() {
        return distance;
    }

    public Edge setDistance(double distance) {
        this.distance = distance;
        return this;
    }

    /**
     * Whether the edge is reverse
     */
    public boolean getReversed() {
        return reversed;
    }

    public Edge setReversed(boolean isReversed) {
        this.reversed = isReversed;
        return this;
    }

    /**
     * Duration until the next edge, in milliseconds
     */
    public long getTime() {
        return time;
    }

    public Edge setTime(long time) {
        this.time = time;
        return this;
    }

    /**
     * Duration until the next edge, in milliseconds
     */
    public double getWeight() {
        return weight;
    }

    public Edge setWeight(double weight) {
        this.weight = weight;
        return this;
    }

    /* This method returns the points associated to this edge. Please note that it will not include the last point,
     * i.e. the first point of the next edge object.
     */
    public PointList getPoints() {
        return points;
    }

    public void setPoints(PointList points) {
        this.points = points;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        sb.append(name).append(',');
        sb.append(distance).append(',');
        sb.append(grade).append(',');
        sb.append(time).append(',');
        sb.append(weight);
        sb.append(')');
        return sb.toString();
    }

    /**
     * This method returns the length of an Instruction. The length of an instruction is defined by [the
     * index of the first point of the next instruction] - [the index of the first point of this instruction].
     * <p>
     * In general this will just resolve to the size of the PointList, except for {@link ViaInstruction} and
     * {@link FinishInstruction}, which are only virtual instructions, in a sense that they don't provide
     * a turn instruction, but only an info ("reached via point or destination").
     * <p>
     * See #1216 and #1138
     */
    public int getLength() {
        return points.size();
    }

}
