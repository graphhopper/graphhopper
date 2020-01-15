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
package com.graphhopper;

import com.graphhopper.util.PMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper containing path and error output of GraphHopper.
 * <p>
 *
 * @author Peter Karich
 */
public class GHResponse {
    private final List<Throwable> errors = new ArrayList<>(4);
    private final PMap hintsMap = new PMap();
    private final List<PathWrapper> pathWrappers = new ArrayList<>(5);
    private String debugInfo = "";

    public GHResponse() {
    }

    public void add(PathWrapper altResponse) {
        pathWrappers.add(altResponse);
    }

    /**
     * Returns the best path.
     */
    public PathWrapper getBest() {
        if (pathWrappers.isEmpty())
            throw new RuntimeException("Cannot fetch best response if list is empty");

        return pathWrappers.get(0);
    }

    /**
     * This method returns the best path as well as all alternatives.
     */
    public List<PathWrapper> getAll() {
        return pathWrappers;
    }

    /**
     * This method returns true if there are alternative paths available besides the best.
     */
    public boolean hasAlternatives() {
        return pathWrappers.size() > 1;
    }

    public void addDebugInfo(String debugInfo) {
        if (debugInfo == null)
            throw new IllegalStateException("Debug information has to be none null");

        if (!this.debugInfo.isEmpty())
            this.debugInfo += "; ";

        this.debugInfo += debugInfo;
    }

    public String getDebugInfo() {
        String str = debugInfo;
        for (PathWrapper ar : pathWrappers) {
            if (!str.isEmpty())
                str += "; ";

            str += ar.getDebugInfo();
        }
        return str;
    }

    /**
     * This method returns true if one of the paths has an error or if the response itself is
     * erroneous.
     */
    public boolean hasErrors() {
        if (!errors.isEmpty())
            return true;

        for (PathWrapper ar : pathWrappers) {
            if (ar.hasErrors())
                return true;
        }

        return false;
    }

    /**
     * This method returns all the explicitly added errors and the errors of all paths.
     */
    public List<Throwable> getErrors() {
        List<Throwable> list = new ArrayList<>();
        list.addAll(errors);
        for (PathWrapper ar : pathWrappers) {
            list.addAll(ar.getErrors());
        }
        return list;
    }

    public GHResponse addErrors(List<Throwable> errors) {
        this.errors.addAll(errors);
        return this;
    }

    public GHResponse addError(Throwable error) {
        this.errors.add(error);
        return this;
    }

    @Override
    public String toString() {
        String str = "";
        for (PathWrapper a : pathWrappers) {
            str += "; " + a.toString();
        }

        if (pathWrappers.isEmpty())
            str = "no paths";

        if (!errors.isEmpty())
            str += ", main errors: " + errors.toString();

        return str;
    }

    public PMap getHints() {
        return hintsMap;
    }
}
