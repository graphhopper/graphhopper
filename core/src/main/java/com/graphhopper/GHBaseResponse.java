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

import java.util.ArrayList;
import java.util.List;

/**
 * GraphHopper its base response class.
 * <p/>
 * @author Peter Karich
 */
public class GHBaseResponse<T>
{
    private String debugInfo = "";
    private final List<Throwable> errors = new ArrayList<Throwable>(4);

    public GHBaseResponse()
    {
    }

    public String getDebugInfo()
    {
        return debugInfo;
    }

    @SuppressWarnings("unchecked")
    public T setDebugInfo( String debugInfo )
    {
        if (debugInfo != null)
            this.debugInfo = debugInfo;
        return (T) this;
    }

    /**
     * @return true if one or more error found
     */
    public boolean hasErrors()
    {
        return !errors.isEmpty();
    }

    public List<Throwable> getErrors()
    {
        return errors;
    }

    @SuppressWarnings("unchecked")
    public T addError( Throwable error )
    {
        errors.add(error);
        return (T) this;
    }

    @Override
    public String toString()
    {
        return errors.toString();
    }
}
