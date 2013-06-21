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
package com.graphhopper.routing.util;

/**
 * Calculates the fastest route with the specified vehicle (VehicleEncoder).
 * <p/>
 * @author Peter Karich
 */
public class FastestCalc implements WeightCalculation
{
    private final FlagEncoder encoder;
    private final double maxSpeed;

    public FastestCalc( FlagEncoder encoder )
    {
        this.encoder = encoder;
        maxSpeed = encoder.getMaxSpeed();
    }

    @Override
    public double getMinWeight( double distance )
    {
        return distance / maxSpeed;
    }

    @Override
    public double getWeight( double distance, int flags )
    {
        return distance / encoder.getSpeed(flags);
    }

    @Override
    public double revertWeight( double weight, int flags )
    {
        return weight * encoder.getSpeed(flags);
    }

    @Override
    public String toString()
    {
        return "FASTEST|" + encoder;
    }
}
