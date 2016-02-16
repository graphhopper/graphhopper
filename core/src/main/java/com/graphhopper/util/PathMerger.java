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

import com.graphhopper.PathWrapper;
import com.graphhopper.routing.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * This class merges a list of points into one point recognizing the specified places.
 * <p>
 * @author Peter Karich
 * @author ratrun
 */
public class PathMerger
{
    private static final DouglasPeucker DP = new DouglasPeucker();
    private boolean enableInstructions = true;
    private boolean simplifyResponse = true;
    private DouglasPeucker douglasPeucker = DP;
    private boolean calcPoints = true;

    public PathMerger setCalcPoints( boolean calcPoints )
    {
        this.calcPoints = calcPoints;
        return this;
    }

    public PathMerger setDouglasPeucker( DouglasPeucker douglasPeucker )
    {
        this.douglasPeucker = douglasPeucker;
        return this;
    }

    public PathMerger setSimplifyResponse( boolean simplifyRes )
    {
        this.simplifyResponse = simplifyRes;
        return this;
    }

    public PathMerger setEnableInstructions( boolean enableInstructions )
    {
        this.enableInstructions = enableInstructions;
        return this;
    }

    public void doWork( PathWrapper altRsp, List<Path> paths, Translation tr )
    {
        int origPoints = 0;
        long fullTimeInMillis = 0;
        double fullWeight = 0;
        double fullDistance = 0;
        boolean allFound = true;

        InstructionList fullInstructions = new InstructionList(tr);
        PointList fullPoints = PointList.EMPTY;
        List<String> description = new ArrayList<String>();
        for (int pathIndex = 0; pathIndex < paths.size(); pathIndex++)
        {
            Path path = paths.get(pathIndex);
            description.addAll(path.getDescription());
            fullTimeInMillis += path.getTime();
            fullDistance += path.getDistance();
            fullWeight += path.getWeight();
            if (enableInstructions)
            {
                InstructionList il = path.calcInstructions(tr);

                if (!il.isEmpty())
                {
                    if (fullPoints.isEmpty())
                    {
                        PointList pl = il.get(0).getPoints();
                        // do a wild guess about the total number of points to avoid reallocation a bit
                        fullPoints = new PointList(il.size() * Math.min(10, pl.size()), pl.is3D());
                    }

                    for (Instruction i : il)
                    {
                        if (simplifyResponse)
                        {
                            origPoints += i.getPoints().size();
                            douglasPeucker.simplify(i.getPoints());
                        }
                        fullInstructions.add(i);
                        fullPoints.add(i.getPoints());
                    }

                    // if not yet reached finish replace with 'reached via'
                    if (pathIndex + 1 < paths.size())
                    {
                        ViaInstruction newInstr = new ViaInstruction(fullInstructions.get(fullInstructions.size() - 1));
                        newInstr.setViaCount(pathIndex + 1);
                        fullInstructions.replaceLast(newInstr);
                    }
                }

            } else if (calcPoints)
            {
                PointList tmpPoints = path.calcPoints();
                if (fullPoints.isEmpty())
                    fullPoints = new PointList(tmpPoints.size(), tmpPoints.is3D());

                if (simplifyResponse)
                {
                    origPoints = tmpPoints.getSize();
                    douglasPeucker.simplify(tmpPoints);
                }
                fullPoints.add(tmpPoints);
            }

            allFound = allFound && path.isFound();
        }

        if (!fullPoints.isEmpty())
        {
            String debug = altRsp.getDebugInfo() + ", simplify (" + origPoints + "->" + fullPoints.getSize() + ")";
            altRsp.addDebugInfo(debug);
            if (fullPoints.is3D)
                calcAscendDescend(altRsp, fullPoints);
        }

        if (enableInstructions)
            altRsp.setInstructions(fullInstructions);

        if (!allFound)
            altRsp.addError(new RuntimeException("Connection between locations not found"));

        altRsp.setDescription(description).
                setPoints(fullPoints).
                setRouteWeight(fullWeight).
                setDistance(fullDistance).
                setTime(fullTimeInMillis);
    }

    private void calcAscendDescend( final PathWrapper rsp, final PointList pointList )
    {
        double ascendMeters = 0;
        double descendMeters = 0;
        double lastEle = pointList.getElevation(0);
        for (int i = 1; i < pointList.size(); ++i)
        {
            double ele = pointList.getElevation(i);
            double diff = Math.abs(ele - lastEle);

            if (ele > lastEle)
                ascendMeters += diff;
            else
                descendMeters += diff;

            lastEle = ele;

        }
        rsp.setAscend(ascendMeters);
        rsp.setDescend(descendMeters);
    }
}
