/*
 * Copyright 2015 Peter Karich.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.util;

/**
 *
 * @author Peter Karich
 */
public class ViaInstruction extends Instruction
{
    private int viaPosition = -1;

    public ViaInstruction( String name, InstructionAnnotation ia, PointList pl )
    {
        super(REACHED_VIA, name, ia, pl);
    }

    public ViaInstruction( Instruction instr )
    {
        this(instr.getName(), instr.getAnnotation(), instr.getPoints());
        setDistance(instr.getDistance());
        setTime(instr.getTime());
    }

    public void setViaCount( int count )
    {
        this.viaPosition = count;
    }

    public int getViaCount()
    {
        if (viaPosition < 0)
            throw new IllegalStateException("Uninitialized via count in instruction " + getName());

        return viaPosition;
    }

    @Override
    public String getTurnDescription( Translation tr )
    {
        if (rawName)
            return getName();

        return tr.tr("stopover", viaPosition);
    }
}
