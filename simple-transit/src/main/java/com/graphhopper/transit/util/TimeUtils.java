/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
 
package com.graphhopper.transit.util;

/**
 *
 * @author Thomas Buerli <tbuerli@student.ethz.ch>
 */
public class TimeUtils {

    public static String niceSeconds(int secondsCount) {
        int seconds = (int) (secondsCount) % 60;
        int minutes = (int) ((secondsCount / (60)) % 60);
        int hours = (int) ((secondsCount / (60 * 60)) % 24);

        String output = new String();
        if (hours > 0) {
            output += String.format("%dh ", hours);
        }
        if (minutes > 0) {
            output += String.format("%dmin ", minutes);
        }
        if (output.isEmpty() || seconds > 0) {
            output += String.format("%ds", seconds);
        }
        return output;
    }

    public static String formatTime(int secondsCount) {
        int seconds = (int) (secondsCount) % 60;
        int minutes = (int) ((secondsCount / (60)) % 60);
        int hours = (int) ((secondsCount / (60 * 60)) % 24);

        String output = new String();
        output += String.format("%d:", hours);
        output += String.format("%d:", minutes);
        output += String.format("%d", seconds);
        return output;
    }
}
