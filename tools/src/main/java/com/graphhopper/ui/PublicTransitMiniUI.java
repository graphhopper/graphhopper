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
package com.graphhopper.ui;

import com.graphhopper.GHPublicTransit;
import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHTBitSet;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.AlgorithmPreparation;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.PublicTransitFlagEncoder;
import com.graphhopper.routing.util.ShortestCalc;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationTime2IDIndex;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.TimeUtils;
import com.graphhopper.util.shapes.BBox;
import gnu.trove.list.TIntList;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.TimeZone;
import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * A rough graphical user interface for visualizing the public transit graph.
 * Mainly for debugging algorithms and spatial datastructures.
 *
 * Use the project at https://github.com/graphhopper/graphhopper-web for a
 * better/faster/userfriendly/... alternative!
 *
 * @author Thomas Buerli <tbuerli@student.ethz.ch>
 */
public class PublicTransitMiniUI {

    private static String GTFS = "/opt/cache/gtfs/gtfs.zip";
    private static String dir = "./target/tmp/";

    public static void main(String[] strs) throws Exception {
        GHPublicTransit gh = new GHPublicTransit().forDesktop();
        gh.setGraphHopperLocation(dir);
        gh.setGtfsFile(GTFS);
        gh.importOrLoad();
        
        boolean debug = false;

        new PublicTransitMiniUI(gh, debug).visualize();
    }
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Path path;
    private AlgorithmPreparation prepare;
    private final Graph graph;
    private LocationTime2IDIndex index;
    //private Id2NameIndex nameIndex;
    private String latLon = "";
    private GraphicsWrapper mg;
    private JPanel infoPanel;
    private LayeredPanel mainPanel;
    private MapLayer roadsLayer;
    private MapLayer pathLayer;
    private boolean fastPaint = false;
    private WeightCalculation wCalc = new ShortestCalc();
    private JSpinner timeSpinner;

    public PublicTransitMiniUI(GHPublicTransit gh, boolean debug) {
        this.graph = gh.graph();

        logger.info("locations:" + graph.getNodes() + ", debug:" + debug);
        mg = new GraphicsWrapper(graph);

        // prepare node quadtree to 'enter' the graph. create a 313*313 grid => <3km
//         this.index = new DebugLocation2IDQuadtree(roadGraph, mg);
        this.index = gh.index();
        //this.nameIndex = reader.getNameIndex();
        //        this.algo = new DebugDijkstraBidirection(graph, mg);
        // this.algo = new DijkstraBidirection(graph);
        //        this.algo = new DebugAStar(graph, mg);
        //        this.algo = new AStar(graph);
        //        this.algo = new DijkstraSimple(graph);
        //        this.algo = new DebugDijkstraSimple(graph, mg);
        infoPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Color.WHITE);
                Rectangle b = infoPanel.getBounds();
                g.fillRect(0, 0, b.width, b.height);

                g.setColor(Color.BLUE);
                g.drawString(latLon, 40, 20);
                g.drawString("scale:" + mg.getScaleX(), 40, 40);
                int w = mainPanel.getBounds().width;
                int h = mainPanel.getBounds().height;
                g.drawString(mg.setBounds(0, w, 0, h).toLessPrecisionString(), 40, 60);
            }
        };


        timeSpinner = new JSpinner(new SpinnerDateModel());
        JSpinner.DateEditor timeEditor = new JSpinner.DateEditor(timeSpinner);
        SimpleDateFormat format = timeEditor.getFormat();
        format.applyPattern("HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        timeSpinner.setEditor(timeEditor);
        timeSpinner.setValue(new Date(32400000));
        infoPanel.add(timeSpinner);

        mainPanel = new LayeredPanel();

        // TODO make it correct with bitset-skipping too
        final GHBitSet bitset = new GHTBitSet(graph.getNodes());
        mainPanel.addLayer(roadsLayer = new DefaultMapLayer() {
            Random rand = new Random();

            @Override
            public void paintComponent(Graphics2D g2) {
                clearGraphics(g2);
                int locs = graph.getNodes();
                Rectangle d = getBounds();
                BBox b = mg.setBounds(0, d.width, 0, d.height);
                if (fastPaint) {
                    rand.setSeed(0);
                    bitset.clear();
                }

//                int loc = index.findID(49.682000, 9.943000);
//                mg.plotNode(g2, loc, Color.PINK);
//                plotNode(g2, index.findID(49.682000, 9.943000));

//                g2.setColor(Color.RED.brighter().brighter());
//
//                path = calcPath(prepare.createAlgo());
//                System.out.println("now: " + path.toDetailsString());
//                plotPath(path, g2, 1);
//                g2.setColor(Color.black);

                for (int nodeIndex = 0; nodeIndex < locs; nodeIndex++) {
                    if (fastPaint && rand.nextInt(30) > 1) {
                        continue;
                    }
                    double lat = graph.getLatitude(nodeIndex);
                    double lon = graph.getLongitude(nodeIndex);
                    // mg.plotText(g2, lat, lon, "" + nodeIndex);
                    if (lat < b.minLat || lat > b.maxLat || lon < b.minLon || lon > b.maxLon) {
                        continue;
                    }

                    // accept all
                    EdgeIterator iter = graph.getEdges(nodeIndex, EdgeFilter.ALL_EDGES);
//                    {
//                        @Override public boolean accept(EdgeIterator iter) {
//                            int flags = iter.flags();
//                            return footEncoder.isForward(flags);
//                        }
//                    });
                    while (iter.next()) {
                        int nodeId = iter.getAdjNode();
                        int sum = nodeIndex + nodeId;
                        if (fastPaint) {
                            if (bitset.contains(sum)) {
                                continue;
                            }
                            bitset.add(sum);
                        }
                        double lat2 = graph.getLatitude(nodeId);
                        double lon2 = graph.getLongitude(nodeId);
                        mg.plotEdge(g2, lat, lon, lat2, lon2);
                    }
                }
            }
        });

        mainPanel.addLayer(pathLayer = new DefaultMapLayer() {
            @Override
            public void paintComponent(Graphics2D g2) {
                if (dijkstraFromId < 0 || dijkstraToId < 0) {
                    return;
                }

                makeTransparent(g2);
                PublicTransitFlagEncoder encoder = new PublicTransitFlagEncoder();
                RoutingAlgorithm algo = new DebugDijkstraSimple(graph, encoder, mg);
                if (algo instanceof DebugAlgo) {
                    ((DebugAlgo) algo).setGraphics2D(g2);
                }

                StopWatch sw = new StopWatch().start();
                logger.info("start searching from:" + dijkstraFromId + " to:" + dijkstraToId + " " + wCalc);
                path = algo.setType(wCalc).calcPath(dijkstraFromId, dijkstraToId);
//                mg.plotNode(g2, dijkstraFromId, Color.red);
//                mg.plotNode(g2, dijkstraToId, Color.BLUE);
                sw.stop();

                // if directed edges
                if (!path.isFound()) {
                    logger.warn("path not found! direction not valid?");
                    return;
                }

                logger.info("found path in " + sw.getSeconds() + "s with " + path.calcNodes().size() + " nodes: " + path);
                g2.setColor(Color.BLUE.brighter().brighter());
                plotPath(path, g2, 1);
            }
        });

        if (debug) {
            // disable double buffering for debugging drawing - nice! when do we need DebugGraphics then?
            RepaintManager repaintManager = RepaintManager.currentManager(mainPanel);
            repaintManager.setDoubleBufferingEnabled(false);
            mainPanel.setBuffering(false);
        }
    }

    void plotNode(Graphics2D g2, int node) {
        double lat = graph.getLatitude(node);
        double lon = graph.getLongitude(node);
        mg.plotText(g2, lat, lon, "" + node);
        // mg.plotText(g2, lat, lon, "" + nameIndex.findName(node));
    }

    private Path plotPath(Path tmpPath, final Graphics2D g2, int w) {
        if (!tmpPath.isFound()) {
            logger.info("nothing found " + w);
            return tmpPath;
        }
        // Get time of the start node
        final int startTime = index.getTime(tmpPath.calcNodes().get(0));

        Path.EdgeVisitor visitor = new Path.EdgeVisitor() {
            private final PublicTransitFlagEncoder encoder = new PublicTransitFlagEncoder();
            private boolean onTrain = false;
            private boolean justArrived = true;
            private double time = startTime;

            @Override
            public void next( EdgeIterator iter, int index ) {
                // Edge is in reversed direction
                int flags = iter.getFlags();
                int startNode = iter.getAdjNode();
                int endNode = iter.getBaseNode();
                time += iter.getDistance();
                if (!onTrain && encoder.isTransit(flags)) {
                    // Do Nothing
                } else if (!onTrain && encoder.isBoarding(flags)) {
                    // Boarding edge
                    if (justArrived) {
                        plotNode(g2, true, endNode, time);
                    } else {
                        plotNode(g2, false, endNode, time);
                    }
                    onTrain = true;
                    justArrived = true;
                } else if (onTrain && encoder.isAlight(flags)) {
                    // Alight edge
                    plotNode(g2, true, endNode, time);
                    onTrain = false;
                    justArrived = false;

                } else if (!onTrain && encoder.isExit(flags)) {
                    // Get off
                } else if (onTrain && encoder.isBackward(flags)) {
                    // Travling
                    if (justArrived) {
                        plotNode(g2, endNode);
                        justArrived = false;
                    } else {
                        justArrived = true;
                    }
                } else {
                    // Wrong edge
                }
            }

            private void plotNode(Graphics2D g2, boolean name, int node, double time) {
                double lat = graph.getLatitude(node);
                double lon = graph.getLongitude(node);
                if (name) {
                    // mg.plotText(g2, lat, lon, "" + nameIndex.findName(node));
                    mg.plotText(g2, lat, lon, "" + node);
                    mg.plotText(g2, lat, lon, TimeUtils.formatTime((int) time), 5, 20);
                } else {
                    mg.plotText(g2, lat, lon, TimeUtils.formatTime((int) time), 5, 40);
                }
            }

            private void plotNode(Graphics2D g2, int node) {
                double lat = graph.getLatitude(node);
                double lon = graph.getLongitude(node);
                // mg.plotText(g2, lat, lon, "" + nameIndex.findName(node));
                mg.plotText(g2, lat, lon, "" + node);
            }

        };
        path.forEveryEdge(visitor);

        double prevLat = Double.NaN;
        double prevLon = Double.NaN;
        boolean plotNodes = true;
        TIntList nodes = tmpPath.calcNodes();
//        if (plotNodes) {
//            for (int i = 0; i < nodes.size(); i++) {
//                plotNode(g2, nodes.get(i));
//            }
//        }
        PointList list = tmpPath.calcPoints();
        for (int i = 0; i < list.getSize(); i++) {
            double lat = list.getLatitude(i);
            double lon = list.getLongitude(i);
            if (!Double.isNaN(prevLat)) {
                mg.plotEdge(g2, prevLat, prevLon, lat, lon, w);
            } else {
                mg.plot(g2, lat, lon, w);
            }
            prevLat = lat;
            prevLon = lon;
        }
        logger.info("dist:" + tmpPath.getDistance() + ", path points:" + list + ", nodes:" + nodes);
        return tmpPath;
    }
    private int dijkstraFromId = -1;
    private int dijkstraToId = -1;

    public void visualize() {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    int frameHeight = 800;
                    int frameWidth = 1200;
                    JFrame frame = new JFrame("GraphHopper UI - Small&Ugly ;)");
                    frame.setLayout(new BorderLayout());
                    frame.add(mainPanel, BorderLayout.CENTER);
                    frame.add(infoPanel, BorderLayout.NORTH);

                    infoPanel.setPreferredSize(new Dimension(300, 100));

                    // scale
                    mainPanel.addMouseWheelListener(new MouseWheelListener() {
                        @Override
                        public void mouseWheelMoved(MouseWheelEvent e) {
                            mg.scale(e.getX(), e.getY(), e.getWheelRotation() < 0);
                            repaintRoads();
                        }
                    });

                    MouseAdapter ml = new MouseAdapter() {
                        // for routing:
                        double fromLat, fromLon;
                        int startTime = 0;
                        boolean fromDone = false;

                        @Override
                        public void mouseClicked(MouseEvent e) {
                            if (!fromDone) {


                                fromLat = mg.getLat(e.getY());
                                fromLon = mg.getLon(e.getX());
                            } else {

                                Date time = (Date) timeSpinner.getValue();

                                // Startime in secons after midnight
                                int startTime = (int) (time.getTime()) / 1000;

                                double toLat = mg.getLat(e.getY());
                                double toLon = mg.getLon(e.getX());
                                StopWatch sw = new StopWatch().start();
                                logger.info("start searching from " + fromLat + "," + fromLon
                                        + " to " + toLat + "," + toLon);
                                // get from and to node id
                                dijkstraFromId = index.findID(fromLat, fromLon, startTime);
                                dijkstraToId = index.findExitNode(toLat, toLon);
                                logger.info("Found ids " + dijkstraFromId + " -> " + dijkstraToId + " starting at " + startTime + "s  in " + sw.stop().getSeconds() + "s");

                                repaintPaths();
                            }

                            fromDone = !fromDone;
                        }
                        boolean dragging = false;

                        @Override
                        public void mouseDragged(MouseEvent e) {
                            dragging = true;
                            fastPaint = true;
                            update(e);
                            updateLatLon(e);
                        }

                        @Override
                        public void mouseReleased(MouseEvent e) {
                            if (dragging) {
                                // update only if mouse release comes from dragging! (at the moment equal to fastPaint)
                                dragging = false;
                                fastPaint = false;
                                update(e);
                            }
                        }

                        public void update(MouseEvent e) {
                            mg.setNewOffset(e.getX() - currentPosX, e.getY() - currentPosY);
                            repaintRoads();
                        }

                        @Override
                        public void mouseMoved(MouseEvent e) {
                            updateLatLon(e);
                        }

                        @Override
                        public void mousePressed(MouseEvent e) {
                            updateLatLon(e);
                        }
                    };
                    mainPanel.addMouseListener(ml);
                    mainPanel.addMouseMotionListener(ml);

                    // just for fun
//                    mainPanel.getInputMap().put(KeyStroke.getKeyStroke("DELETE"), "removedNodes");
//                    mainPanel.getActionMap().put("removedNodes", new AbstractAction() {
//                        @Override public void actionPerformed(ActionEvent e) {
//                            int counter = 0;
//                            for (CoordTrig<Long> coord : quadTreeNodes) {
//                                int ret = quadTree.remove(coord.lat, coord.lon);
//                                if (ret < 1) {
////                                    logger.info("cannot remove " + coord + " " + ret);
////                                    ret = quadTree.remove(coord.getLatitude(), coord.getLongitude());
//                                } else
//                                    counter += ret;
//                            }
//                            logger.info("Removed " + counter + " of " + quadTreeNodes.size() + " nodes");
//                        }
//                    });

                    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    frame.setSize(frameWidth + 10, frameHeight + 30);
                    frame.setVisible(true);
                }
            });
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    // for moving
    int currentPosX;
    int currentPosY;

    void updateLatLon(MouseEvent e) {
        latLon = mg.getLat(e.getY()) + "," + mg.getLon(e.getX());
        infoPanel.repaint();
        currentPosX = e.getX();
        currentPosY = e.getY();
    }

    void repaintPaths() {
        pathLayer.repaint();
        mainPanel.repaint();
    }

    void repaintRoads() {
        // avoid threading as there should be no updated to scale or offset while painting 
        // (would to lead to artifacts)
        StopWatch sw = new StopWatch().start();
        pathLayer.repaint();
        roadsLayer.repaint();
        mainPanel.repaint();
        logger.info("roads painting took " + sw.stop().getSeconds() + " sec");
    }
}
