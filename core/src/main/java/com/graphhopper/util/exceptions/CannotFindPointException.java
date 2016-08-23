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
package com.graphhopper.util.exceptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an instance of the "Cannot find Point" Exception,
 * whereas the Point that cannot be found is at pointIndex.
 *
 * @author Robin Boldt
 */
public class CannotFindPointException extends IllegalArgumentException implements GHException
{

    private final int pointIndex;

    public CannotFindPointException( String var1, int pointIndex )
    {
        super(var1);
        this.pointIndex = pointIndex;
    }

    public int getPointIndex()
    {
        return this.pointIndex;
    }

    @Override
    public Map<String, String> getDetails()
    {
        Map<String, String> deatils = new HashMap<>(1);
        deatils.put("not_found_point_index", String.valueOf(this.pointIndex));
        return deatils;
    }

}
