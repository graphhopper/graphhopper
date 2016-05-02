/*
 * Copyright 2015 peterk.
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
package com.graphhopper.routing.util;

import com.graphhopper.util.GHUtility;

/**
 *
 * @author Peter Karich
 */
public abstract class AbstractWeighting implements Weighting
{
    protected final FlagEncoder flagEncoder;

    public AbstractWeighting( FlagEncoder encoder )
    {
        this.flagEncoder = encoder;
        if (!flagEncoder.isRegistered())
            throw new IllegalStateException("Make sure you add the FlagEncoder " + flagEncoder + " to an EncodingManager before using it elsewhere");
        if (!isValidName(getName()))
            throw new IllegalStateException("Not a valid name for a Weighting: " + getName());
    }

    @Override
    public boolean matches( HintsMap reqMap )
    {
        return getName().equals(reqMap.getWeighting())
                && flagEncoder.toString().equals(reqMap.getVehicle());
    }

    @Override
    public FlagEncoder getFlagEncoder()
    {
        return flagEncoder;
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 71 * hash + toString().hashCode();
        return hash;
    }

    @Override
    public boolean equals( Object obj )
    {
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Weighting other = (Weighting) obj;
        return toString().equals(other.toString());
    }

    static final boolean isValidName( String name )
    {
        if (name == null || name.isEmpty())
            return false;

        return name.matches("[\\|_a-z]+");
    }

    /**
     * Replaces all characters which are not numbers, characters or underscores with underscores
     */
    public static String weightingToFileName( Weighting w )
    {
        return w.toString().toLowerCase().replaceAll("\\|", "_");
    }

    @Override
    public String toString()
    {
        return getName() + "|" + flagEncoder;
    }
}
