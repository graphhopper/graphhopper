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
package com.graphhopper;

import com.graphhopper.util.PMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper to simplify output of GraphHopper.
 * <p>
 * @author Peter Karich
 */
public class GHResponse
{
    private String debugInfo = "";
    private final List<Throwable> errors = new ArrayList<Throwable>(4);
    private final PMap hintsMap = new PMap();
    private final List<AltResponse> alternatives = new ArrayList<AltResponse>(5);

    public GHResponse()
    {
    }

    public void addAlternative( AltResponse altResponse )
    {
        alternatives.add(altResponse);
    }

    /**
     * Returns the first response.
     */
    public AltResponse getFirst()
    {
        if (alternatives.isEmpty())
            throw new RuntimeException("Cannot fetch first alternative if list is empty");

        return alternatives.get(0);
    }

    public List<AltResponse> getAlternatives()
    {
        return alternatives;
    }

    public boolean hasAlternatives()
    {
        return !alternatives.isEmpty();
    }

    public void addDebugInfo( String debugInfo )
    {
        if (debugInfo == null)
            throw new IllegalStateException("Debug information has to be none null");

        if (!this.debugInfo.isEmpty())
            this.debugInfo += ";";

        this.debugInfo += debugInfo;
    }

    public String getDebugInfo()
    {
        String str = debugInfo;
        for (AltResponse ar : alternatives)
        {
            if (!str.isEmpty())
                str += "; ";

            str += ar.getDebugInfo();
        }
        return str;
    }

    /**
     * This method returns true only if the response itself is errornous.
     * <p>
     * @see #hasErrors()
     */
    public boolean hasRawErrors()
    {
        return !errors.isEmpty();
    }

    /**
     * This method returns true if no alternative is available, if one of these has an error or if
     * the response itself is errornous.
     * <p>
     * @see #hasRawErrors()
     */
    public boolean hasErrors()
    {
        if (hasRawErrors() || alternatives.isEmpty())
            return true;

        for (AltResponse ar : alternatives)
        {
            if (ar.hasErrors())
                return true;
        }

        return false;
    }

    /**
     * This method returns all the explicitely added errors and the errors of all alternatives.
     */
    public List<Throwable> getErrors()
    {
        List<Throwable> list = new ArrayList<Throwable>();
        list.addAll(errors);
        if (alternatives.isEmpty())
            list.add(new IllegalStateException("No alternative existent"));
        else
            for (AltResponse ar : alternatives)
            {
                list.addAll(ar.getErrors());
            }
        return list;
    }

    public GHResponse addErrors( List<Throwable> errors )
    {
        this.errors.addAll(errors);
        return this;
    }

    public GHResponse addError( Throwable error )
    {
        this.errors.add(error);
        return this;
    }

    @Override
    public String toString()
    {
        String str = "";
        for (AltResponse a : alternatives)
        {
            str += "; " + a.toString();
        }

        if (alternatives.isEmpty())
            str = "no alternatives";

        if (!errors.isEmpty())
            str += ", main errors: " + errors.toString();

        return str;
    }

    public PMap getHints()
    {
        return hintsMap;
    }
}
