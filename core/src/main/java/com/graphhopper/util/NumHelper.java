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
package com.graphhopper.util;

/**
 * @author Peter Karich
 */
public class NumHelper
{
    public static boolean equalsEps( double d1, double d2 )
    {
        return equalsEps(d1, d2, 1e-6);
    }

    public static boolean equalsEps( double d1, double d2, double epsilon )
    {
        return Math.abs(d1 - d2) < epsilon;
    }

    public static boolean equals( double d1, double d2 )
    {
        return Double.compare(d1, d2) == 0;
    }

    public static int compare( double d1, double d2 )
    {
        return Double.compare(d1, d2);
    }

    public static boolean equalsEps( float d1, float d2 )
    {
        return equalsEps(d1, d2, 1e-6f);
    }

    public static boolean equalsEps( float d1, float d2, float epsilon )
    {
        return Math.abs(d1 - d2) < epsilon;
    }
}
