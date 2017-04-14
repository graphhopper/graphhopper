package com.graphhopper.routing;

import com.graphhopper.routing.util.DataFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;

public class InstructionsFromEdges implements Path.EdgeVisitor {
    private final Graph graph;
    private final Weighting weighting;
    private final FlagEncoder encoder;
    private final NodeAccess nodeAccess;

    private final Translation tr;
    private final InstructionList ways;
    /*
     * We need three points to make directions
     *
     *        (1)----(2)
     *        /
     *       /
     *    (0)
     *
     * 0 is the node visited at t-2, 1 is the node visited
     * at t-1 and 2 is the node being visited at instant t.
     * orientation is the angle of the vector(1->2) expressed
     * as atan2, while previousOrientation is the angle of the
     * vector(0->1)
     * Intuitively, if orientation is smaller than
     * previousOrientation, then we have to turn right, while
     * if it is greater we have to turn left. To make this
     * algorithm work, we need to make the comparison by
     * considering orientation belonging to the interval
     * [ - pi + previousOrientation , + pi + previousOrientation ]
     */
    private EdgeIteratorState prevEdge;
    private double prevLat;
    private double prevLon;
    private double doublePrevLat, doublePrevLon; // Lat and Lon of node t-2
    private int prevNode;
    private double prevOrientation;
    private Instruction prevInstruction;
    private boolean prevInRoundabout;
    private String prevName;
    private InstructionAnnotation prevAnnotation;
    private EdgeExplorer outEdgeExplorer;
    private EdgeExplorer crossingExplorer;

    public InstructionsFromEdges(int tmpNode, Graph graph, Weighting weighting, FlagEncoder encoder, NodeAccess nodeAccess, Translation tr, InstructionList ways) {
        this.graph = graph;
        this.weighting = weighting;
        this.encoder = encoder;
        this.nodeAccess = nodeAccess;
        this.tr = tr;
        this.ways = ways;
        prevLat = this.nodeAccess.getLatitude(tmpNode);
        prevLon = this.nodeAccess.getLongitude(tmpNode);
        prevNode = -1;
        prevInRoundabout = false;
        prevName = null;
        outEdgeExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(this.encoder, false, true));
        crossingExplorer = graph.createEdgeExplorer(new DefaultEdgeFilter(encoder, true, true));
    }


    @Override
    public void next(EdgeIteratorState edge, int index, int prevEdgeId) {
        // baseNode is the current node and adjNode is the next
        int adjNode = edge.getAdjNode();
        int baseNode = edge.getBaseNode();
        long flags = edge.getFlags();
        double adjLat = nodeAccess.getLatitude(adjNode);
        double adjLon = nodeAccess.getLongitude(adjNode);
        double latitude, longitude;

        PointList wayGeo = edge.fetchWayGeometry(3);
        boolean isRoundabout = encoder.isBool(flags, FlagEncoder.K_ROUNDABOUT);

        if (wayGeo.getSize() <= 2) {
            latitude = adjLat;
            longitude = adjLon;
        } else {
            latitude = wayGeo.getLatitude(1);
            longitude = wayGeo.getLongitude(1);
            assert Double.compare(prevLat, nodeAccess.getLatitude(baseNode)) == 0;
            assert Double.compare(prevLon, nodeAccess.getLongitude(baseNode)) == 0;
        }

        String name = edge.getName();
        InstructionAnnotation annotation = encoder.getAnnotation(flags, tr);

        if ((prevName == null) && (!isRoundabout)) // very first instruction (if not in Roundabout)
        {
            int sign = Instruction.CONTINUE_ON_STREET;
            prevInstruction = new Instruction(sign, name, annotation, new PointList(10, nodeAccess.is3D()));
            ways.add(prevInstruction);
            prevName = name;
            prevAnnotation = annotation;

        } else if (isRoundabout) {
            // remark: names and annotations within roundabout are ignored
            if (!prevInRoundabout) //just entered roundabout
            {
                int sign = Instruction.USE_ROUNDABOUT;
                RoundaboutInstruction roundaboutInstruction = new RoundaboutInstruction(sign, name,
                        annotation, new PointList(10, nodeAccess.is3D()));
                if (prevName != null) {
                    // check if there is an exit at the same node the roundabout was entered
                    EdgeIterator edgeIter = outEdgeExplorer.setBaseNode(baseNode);
                    while (edgeIter.next()) {
                        if ((edgeIter.getAdjNode() != prevNode)
                                && !encoder.isBool(edgeIter.getFlags(), FlagEncoder.K_ROUNDABOUT)) {
                            roundaboutInstruction.increaseExitNumber();
                            break;
                        }
                    }

                    // previous orientation is last orientation before entering roundabout
                    prevOrientation = Helper.ANGLE_CALC.calcOrientation(doublePrevLat, doublePrevLon, prevLat, prevLon);

                    // calculate direction of entrance turn to determine direction of rotation
                    // right turn == counterclockwise and vice versa
                    double orientation = Helper.ANGLE_CALC.calcOrientation(prevLat, prevLon, latitude, longitude);
                    orientation = Helper.ANGLE_CALC.alignOrientation(prevOrientation, orientation);
                    double delta = (orientation - prevOrientation);
                    roundaboutInstruction.setDirOfRotation(delta);

                } else // first instructions is roundabout instruction
                {
                    prevOrientation = Helper.ANGLE_CALC.calcOrientation(prevLat, prevLon, latitude, longitude);
                    prevName = name;
                    prevAnnotation = annotation;
                }
                prevInstruction = roundaboutInstruction;
                ways.add(prevInstruction);
            }

            // Add passed exits to instruction. A node is counted if there is at least one outgoing edge
            // out of the roundabout
            EdgeIterator edgeIter = outEdgeExplorer.setBaseNode(edge.getAdjNode());
            while (edgeIter.next()) {
                if (!encoder.isBool(edgeIter.getFlags(), FlagEncoder.K_ROUNDABOUT)) {
                    ((RoundaboutInstruction) prevInstruction).increaseExitNumber();
                    break;
                }
            }

        } else if (prevInRoundabout) //previously in roundabout but not anymore
        {

            prevInstruction.setName(name);

            // calc angle between roundabout entrance and exit
            double orientation = Helper.ANGLE_CALC.calcOrientation(prevLat, prevLon, latitude, longitude);
            orientation = Helper.ANGLE_CALC.alignOrientation(prevOrientation, orientation);
            double deltaInOut = (orientation - prevOrientation);

            // calculate direction of exit turn to determine direction of rotation
            // right turn == counterclockwise and vice versa
            double recentOrientation = Helper.ANGLE_CALC.calcOrientation(doublePrevLat, doublePrevLon, prevLat, prevLon);
            orientation = Helper.ANGLE_CALC.alignOrientation(recentOrientation, orientation);
            double deltaOut = (orientation - recentOrientation);

            prevInstruction = ((RoundaboutInstruction) prevInstruction)
                    .setRadian(deltaInOut)
                    .setDirOfRotation(deltaOut)
                    .setExited();

            prevName = name;
            prevAnnotation = annotation;

        } else {
            int sign = getTurn(edge, baseNode, prevNode, adjNode, annotation, name);

            if (sign != Instruction.IGNORE) {
                prevInstruction = new Instruction(sign, name, annotation, new PointList(10, nodeAccess.is3D()));
                ways.add(prevInstruction);
                prevAnnotation = annotation;
            }
            // Updated the prevName, since we don't always create an instruction on name changes the previous
            // name can be an old name. This leads to incorrect turn instructions due to name changes
            prevName = name;
        }

        updatePointsAndInstruction(edge, wayGeo);

        if (wayGeo.getSize() <= 2) {
            doublePrevLat = prevLat;
            doublePrevLon = prevLon;
        } else {
            int beforeLast = wayGeo.getSize() - 2;
            doublePrevLat = wayGeo.getLatitude(beforeLast);
            doublePrevLon = wayGeo.getLongitude(beforeLast);
        }

        prevInRoundabout = isRoundabout;
        prevNode = baseNode;
        prevLat = adjLat;
        prevLon = adjLon;
        prevEdge = edge;
    }

    @Override
    public void finish() {
        if (prevInRoundabout) {
            // calc angle between roundabout entrance and finish
            double orientation = Helper.ANGLE_CALC.calcOrientation(doublePrevLat, doublePrevLon, prevLat, prevLon);
            orientation = Helper.ANGLE_CALC.alignOrientation(prevOrientation, orientation);
            double delta = (orientation - prevOrientation);
            ((RoundaboutInstruction) prevInstruction).setRadian(delta);

        }
        ways.add(new FinishInstruction(nodeAccess, prevEdge.getAdjNode()));
    }

    private int getTurn(EdgeIteratorState edge, int baseNode, int prevNode, int adjNode, InstructionAnnotation annotation, String name) {
        GHPoint point = getPointForOrientationCalculation(edge);
        double lat = point.getLat();
        double lon = point.getLon();
        int sign = calculateSign(lat, lon);

        boolean forceInstruction = false;

        if (!annotation.equals(prevAnnotation) && !annotation.isEmpty()) {
            forceInstruction = true;
        }

        int nrOfPossibleTurns = nrOfPossibleTurns(baseNode, prevNode, adjNode);

        // there is no other turn possible
        if (nrOfPossibleTurns <= 1) {
            return returnForcedInstructionOrIgnore(forceInstruction, sign);
        }

        // Very certain, this is a turn
        if (Math.abs(sign) > 1) {
                        /*
                         * Don't show an instruction if the user is following a street, even though the street is
                         * bending. We should only do this, if following the street is the obvious choice.
                         */
            if (isNameSimilar(name, prevName) && surroundingStreetsAreSlowerByFactor(baseNode, prevNode, adjNode, 2)) {
                return returnForcedInstructionOrIgnore(forceInstruction, sign);
            }

            return sign;
        }

                /*
                The current state is a bit uncertain. So we are going more or less straight sign < 2
                So it really depends on the surrounding street if we need a turn instruction or not
                In most cases this will be a simple follow the current street and we don't necessarily
                need a turn instruction
                 */

        int prevEdge = -1;
        EdgeIterator flagIter = crossingExplorer.setBaseNode(baseNode);
        while (flagIter.next()) {
            if (flagIter.getAdjNode() == prevNode || flagIter.getBaseNode() == prevNode)
                prevEdge = flagIter.getEdge();

        }
        if (prevEdge == -1) {
            throw new IllegalStateException("Couldn't find the edges for " + prevNode + "-" + baseNode + "-" + adjNode);
        }

        long flag = edge.getFlags();
        long prevFlag = graph.getEdgeIteratorState(prevEdge, baseNode).getFlags();

        boolean surroundingStreetsAreSlower = surroundingStreetsAreSlowerByFactor(baseNode, prevNode, adjNode, 1);

        // There is at least one other possibility to turn, and we are almost going straight
        // Check the other turns if one of them is also going almost straight
        // If not, we don't need a turn instruction
        EdgeIteratorState otherContinue = getOtherContinue(baseNode, prevNode, adjNode);

        // Signs provide too less detail, so we use the delta for a precise comparision
        double delta = calculateOrientationDelta(lat, lon);

        // This state is bad! Two streets are going more or less straight
        // Happens a lot for trunk_links
        // For _links, comparing flags works quite good, as links usually have different speeds => different flags
        if (otherContinue != null) {
            //We are at a fork
            if (!isNameSimilar(name, prevName)
                    || isNameSimilar(otherContinue.getName(), prevName)
                    || prevFlag != flag
                    || prevFlag == otherContinue.getFlags()
                    || !surroundingStreetsAreSlower) {
                GHPoint tmpPoint = getPointForOrientationCalculation(otherContinue);
                double otherDelta = calculateOrientationDelta(tmpPoint.getLat(), tmpPoint.getLon());

                if (Math.abs(delta) < .1 && Math.abs(otherDelta) > .15 && isNameSimilar(name, prevName)) {
                    return Instruction.CONTINUE_ON_STREET;
                }

                if (otherDelta < delta) {
                    // TODO Use keeps once we have a robust client
                    //return Instruction.KEEP_LEFT;
                    return Instruction.TURN_SLIGHT_LEFT;
                } else {
                    // TODO Use keeps once we have a robust client
                    //return Instruction.KEEP_RIGHT;
                    return Instruction.TURN_SLIGHT_RIGHT;
                }


            }
        }

        if (!surroundingStreetsAreSlower) {
            if (Math.abs(delta) > .4
                    || isLeavingCurrentStreet(flag, prevFlag, baseNode, prevNode, adjNode, name)) {
                // Leave the current road -> create instruction
                return sign;

            }
        }

        return returnForcedInstructionOrIgnore(forceInstruction, sign);
    }

    private GHPoint getPointForOrientationCalculation(EdgeIteratorState edgeIteratorState) {
        double tmpLat;
        double tmpLon;
        PointList tmpWayGeo = edgeIteratorState.fetchWayGeometry(3);
        if (tmpWayGeo.getSize() <= 2) {
            tmpLat = nodeAccess.getLatitude(edgeIteratorState.getAdjNode());
            tmpLon = nodeAccess.getLongitude(edgeIteratorState.getAdjNode());
        } else {
            tmpLat = tmpWayGeo.getLatitude(1);
            tmpLon = tmpWayGeo.getLongitude(1);
        }
        return new GHPoint(tmpLat, tmpLon);
    }

    private int returnForcedInstructionOrIgnore(boolean forceInstruction, int sign) {
        if (forceInstruction)
            return sign;
        return Instruction.IGNORE;
    }

    /**
     * If the name and prevName changes this method checks if either the current street is continued on a
     * different edge or if the edge we are turning onto is continued on a different edge.
     * If either of these properties is true, we can be quite certain that a turn instruction should be provided.
     */
    private boolean isLeavingCurrentStreet(long flag, long prevFlag, int baseNode, int prevNode, int adjNode, String name) {
        if (isNameSimilar(name, prevName)) {
            return false;
        }

        // If flags are changing, there might be a chance we find these flags on a different edge
        boolean checkFlag = flag != prevFlag;

        EdgeIterator edgeIter = outEdgeExplorer.setBaseNode(baseNode);
        while (edgeIter.next()) {
            if (edgeIter.getAdjNode() != prevNode && edgeIter.getAdjNode() != adjNode) {
                String edgeName = edgeIter.getName();
                long edgeFlag = edgeIter.getFlags();
                // leave the current street || enter a different street
                if (isTheSameStreet(prevName, prevFlag, edgeName, edgeFlag, checkFlag)
                        || isTheSameStreet(name, flag, edgeName, edgeFlag, checkFlag)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isTheSameStreet(String name1, long flags1, String name2, long flags2, boolean checkFlag) {
        if (isNameSimilar(name1, name2)) {
            if (!checkFlag || flags1 == flags2) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks the other crossings for a street that is going more or less straight
     */
    private EdgeIteratorState getOtherContinue(int baseNode, int prevNode, int adjNode) {
        EdgeIterator edgeIter = outEdgeExplorer.setBaseNode(baseNode);
        int tmpSign;
        while (edgeIter.next()) {
            if (edgeIter.getAdjNode() != prevNode && edgeIter.getAdjNode() != adjNode) {
                GHPoint point = getPointForOrientationCalculation(edgeIter);
                tmpSign = calculateSign(point.getLat(), point.getLon());
                if (Math.abs(tmpSign) <= 1) {
                    return edgeIter;
                }
            }
        }
        return null;
    }

    /**
     * Checks if the surrounding streets are slower. If they are, this indicates, that we are staying
     * on the prominent street that one would follow anyway.
     */
    private boolean surroundingStreetsAreSlowerByFactor(int baseNode, int prevNode, int adjNode, double factor) {
        EdgeIterator edgeIter = crossingExplorer.setBaseNode(baseNode);
        double pathSpeed = -1;
        double maxSurroundingSpeed = -1;
        double tmpSpeed;
        while (edgeIter.next()) {
            if (encoder instanceof DataFlagEncoder) {
                tmpSpeed = ((DataFlagEncoder) encoder).getMaxspeed(edgeIter, 0, false);
                // If one of the edges has no SpeedLimit set, we are not sure
                // TODO we should calculate the true speed limits here
                if (tmpSpeed < 1)
                    return false;
            } else {
                tmpSpeed = encoder.getSpeed(edgeIter.getFlags());
            }

            if (edgeIter.getAdjNode() != prevNode && edgeIter.getAdjNode() != adjNode) {
                if (tmpSpeed > maxSurroundingSpeed)
                    maxSurroundingSpeed = tmpSpeed;
            } else {
                if (pathSpeed < 0) {
                    pathSpeed = tmpSpeed;
                } else {
                    // Speed-Change on the path indicates, that we change road types, show instruction
                    if (tmpSpeed != pathSpeed) {
                        return false;
                    }
                }
            }
        }

        // Surrounding streets need to be slower by a factor
        return maxSurroundingSpeed * factor < pathSpeed;
    }

    /**
     * This method calculates the number of possible turns.
     * The edge we are comming from and the edge we are going to is ignored.
     */
    private int nrOfPossibleTurns(int baseNode, int prevNode, int adjNode) {
        EdgeIterator edgeIter = outEdgeExplorer.setBaseNode(baseNode);
        int count = 1;
        while (edgeIter.next()) {
            if (edgeIter.getAdjNode() != prevNode && edgeIter.getAdjNode() != adjNode)
                count++;
        }
        return count;
    }

    private boolean isNameSimilar(String name1, String name2) {
        // We don't want two empty names to be similar
        // The idea is, if there are only a random tracks, they usually don't have names
        if (name1.isEmpty() && name2.isEmpty()) {
            return false;
        }
        if (name1.equals(name2)) {
            return true;
        }
        return false;
    }


    private double calculateOrientationDelta(double latitude, double longitude) {
        prevOrientation = Helper.ANGLE_CALC.calcOrientation(doublePrevLat, doublePrevLon, prevLat, prevLon);
        double orientation = Helper.ANGLE_CALC.calcOrientation(prevLat, prevLon, latitude, longitude);
        orientation = Helper.ANGLE_CALC.alignOrientation(prevOrientation, orientation);
        return orientation - prevOrientation;
    }

    private int calculateSign(double latitude, double longitude) {
        double delta = calculateOrientationDelta(latitude, longitude);
        double absDelta = Math.abs(delta);

        // TODO not only calculate the mathematical orientation, but also compare to other streets
        // TODO If there is one street turning slight right and one right, but no straight street
        // TODO We can assume the slight right street would be a continue
        if (absDelta < 0.2) {
            // 0.2 ~= 11°
            return Instruction.CONTINUE_ON_STREET;

        } else if (absDelta < 0.8) {
            // 0.8 ~= 40°
            if (delta > 0)
                return Instruction.TURN_SLIGHT_LEFT;
            else
                return Instruction.TURN_SLIGHT_RIGHT;

        } else if (absDelta < 1.8) {
            // 1.8 ~= 103°
            if (delta > 0)
                return Instruction.TURN_LEFT;
            else
                return Instruction.TURN_RIGHT;

        } else if (delta > 0)
            return Instruction.TURN_SHARP_LEFT;
        else
            return Instruction.TURN_SHARP_RIGHT;
    }

    private void updatePointsAndInstruction(EdgeIteratorState edge, PointList pl) {
        // skip adjNode
        int len = pl.size() - 1;
        for (int i = 0; i < len; i++) {
            prevInstruction.getPoints().add(pl, i);
        }
        double newDist = edge.getDistance();
        prevInstruction.setDistance(newDist + prevInstruction.getDistance());
        prevInstruction.setTime(weighting.calcMillis(edge, false, EdgeIterator.NO_EDGE)
                + prevInstruction.getTime());
    }
}