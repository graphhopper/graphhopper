package com.graphhopper.routing;

import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.TimeDependentWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.*;

public class InstructionsFromEdges implements Path.EdgeVisitor {
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
    private double doublePrevLat, doublePrevLong; // Lat and Lon of node t-2
    private int prevNode;
    private double prevOrientation;
    private Instruction prevInstruction;
    private boolean prevInRoundabout;
    private String prevName;
    private InstructionAnnotation prevAnnotation;
    private EdgeExplorer outEdgeExplorer;
    private long time;

    public InstructionsFromEdges(int tmpNode, Graph graph, Weighting weighting, FlagEncoder encoder, NodeAccess nodeAccess, Translation tr, InstructionList ways) {
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
        time = 0;
    }

    @Override
    public void next(EdgeIteratorState edge, int index, int prevEdgeId) {
        // baseNode is the current node and adjNode is the next
        int baseNode = edge.getBaseNode();
        long flags = edge.getFlags();
        double adjLat = nodeAccess.getLatitude(edge.getAdjNode());
        double adjLon = nodeAccess.getLongitude(edge.getAdjNode());
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
                    prevOrientation = Helper.ANGLE_CALC.calcOrientation(doublePrevLat, doublePrevLong, prevLat, prevLon);

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
            double recentOrientation = Helper.ANGLE_CALC.calcOrientation(doublePrevLat, doublePrevLong, prevLat, prevLon);
            orientation = Helper.ANGLE_CALC.alignOrientation(recentOrientation, orientation);
            double deltaOut = (orientation - recentOrientation);

            prevInstruction = ((RoundaboutInstruction) prevInstruction)
                    .setRadian(deltaInOut)
                    .setDirOfRotation(deltaOut)
                    .setExited();

            prevName = name;
            prevAnnotation = annotation;

        } else if ((!name.equals(prevName)) || (!annotation.equals(prevAnnotation))) {
            prevOrientation = Helper.ANGLE_CALC.calcOrientation(doublePrevLat, doublePrevLong, prevLat, prevLon);
            double orientation = Helper.ANGLE_CALC.calcOrientation(prevLat, prevLon, latitude, longitude);
            orientation = Helper.ANGLE_CALC.alignOrientation(prevOrientation, orientation);
            double delta = orientation - prevOrientation;
            double absDelta = Math.abs(delta);
            int sign;

            if (absDelta < 0.2) {
                // 0.2 ~= 11°
                sign = Instruction.CONTINUE_ON_STREET;

            } else if (absDelta < 0.8) {
                // 0.8 ~= 40°
                if (delta > 0)
                    sign = Instruction.TURN_SLIGHT_LEFT;
                else
                    sign = Instruction.TURN_SLIGHT_RIGHT;

            } else if (absDelta < 1.8) {
                // 1.8 ~= 103°
                if (delta > 0)
                    sign = Instruction.TURN_LEFT;
                else
                    sign = Instruction.TURN_RIGHT;

            } else if (delta > 0)
                sign = Instruction.TURN_SHARP_LEFT;
            else
                sign = Instruction.TURN_SHARP_RIGHT;
            prevInstruction = new Instruction(sign, name, annotation, new PointList(10, nodeAccess.is3D()));
            ways.add(prevInstruction);
            prevName = name;
            prevAnnotation = annotation;
        }

        updatePointsAndInstruction(edge, wayGeo, prevEdgeId);

        if (wayGeo.getSize() <= 2) {
            doublePrevLat = prevLat;
            doublePrevLong = prevLon;
        } else {
            int beforeLast = wayGeo.getSize() - 2;
            doublePrevLat = wayGeo.getLatitude(beforeLast);
            doublePrevLong = wayGeo.getLongitude(beforeLast);
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
            double orientation = Helper.ANGLE_CALC.calcOrientation(doublePrevLat, doublePrevLong, prevLat, prevLon);
            orientation = Helper.ANGLE_CALC.alignOrientation(prevOrientation, orientation);
            double delta = (orientation - prevOrientation);
            ((RoundaboutInstruction) prevInstruction).setRadian(delta);

        }
        ways.add(new FinishInstruction(nodeAccess, prevEdge.getAdjNode()));
    }

    private void updatePointsAndInstruction(EdgeIteratorState edge, PointList pl, int prevEdgeId) {
        // skip adjNode
        int len = pl.size() - 1;
        for (int i = 0; i < len; i++) {
            prevInstruction.getPoints().add(pl, i);
        }
        double newDist = edge.getDistance();
        prevInstruction.setDistance(newDist + prevInstruction.getDistance());
        if (weighting != null && weighting instanceof TimeDependentWeighting) {
            // TODO This should already be in the SPT, we shouldn't need to calculate it again here.
            double edgeTime = ((TimeDependentWeighting) weighting).calcTravelTimeSeconds(edge, time / 1000) * 1000.0;
            time += edgeTime;
            prevInstruction.setTime((long) edgeTime + prevInstruction.getTime());
        } else {
            long edgeTime = weighting.calcMillis(edge, false, prevEdgeId);
            prevInstruction.setTime(edgeTime + prevInstruction.getTime());
            time += edgeTime;
        }
    }
}
