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

import com.graphhopper.storage.NodeAccess;

/**
 * @author Peter Karich
 */
public class FinishInstruction extends Instruction
{
    private int count = -1;

    public FinishInstruction( final double lat, final double lon, final double ele )
    {
        super(FINISH, "", InstructionAnnotation.EMPTY, new PointList(2, true)
        {
            {
                add(lat, lon, ele);
            }
        });
    }

    public FinishInstruction( NodeAccess nodeAccess, int node )
    {
        this(nodeAccess.getLatitude(node), nodeAccess.getLongitude(node),
                nodeAccess.is3D() ? nodeAccess.getElevation(node) : 0);
    }

    void setVia( int i )
    {
        sign = REACHED_VIA;
        count = i;
    }

    public int getViaPosition()
    {
        return count;
    }
}
