/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
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

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TLongArrayList;

/**
 * @author Peter Karich
 */
public class FinishInstruction extends Instruction
{   
    private final static TDoubleArrayList DISTANCES = new TDoubleArrayList(1);
    private final static TLongArrayList TIMES = new TLongArrayList(1);
    static {
        DISTANCES.add(0);
        TIMES.add(0);
    }

    public FinishInstruction( final double lat, final double lon )
    {
        super(FINISH, "", DISTANCES, TIMES, new PointList()
        {   
            {
                add(lat, lon);
            }
        });
    }
}
