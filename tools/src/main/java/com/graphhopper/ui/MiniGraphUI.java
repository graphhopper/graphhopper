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
package com.graphhopper.ui;

import com.carrotsearch.hppc.IntIndexedContainer;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHTBitSet;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.config.TurnCostsConfig;
import com.graphhopper.routing.*;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.routing.lm.LMRoutingAlgorithmFactory;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters.Algorithms;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.BBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.List;
import java.util.Random;

/**
 * A rough graphical user interface for visualizing the OSM graph. Mainly for debugging algorithms
 * and spatial data structures. See e.g. this blog post:
 * https://graphhopper.com/blog/2016/01/19/alternative-roads-to-rome/
 * <p>
 * Use the web module for a better/faster/userfriendly/... alternative!
 * <p>
 *
 * @author Peter Karich
 */
public class MiniGraphUI {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final BaseGraph graph;
    private final NodeAccess na;
    private final MapLayer pathLayer;
    private final DecimalEncodedValue avSpeedEnc;
    private final BooleanEncodedValue accessEnc;
    private final boolean useCH;
    // for moving
    int currentPosX;
    int currentPosY;
    private LocationIndexTree index;
    private String latLon = "";
    private GraphicsWrapper mg;
    private JPanel infoPanel;
    private LayeredPanel mainPanel;
    private MapLayer roadsLayer;
    private boolean fastPaint = false;
    private boolean showQuadTree = false;
    private Snap fromRes;
    private Snap toRes;
    private QueryGraph qGraph;

    public static void main(String[] strs) {
        PMap args = PMap.read(strs);
        args.putObject("datareader.file", args.getString("datareader.file", "core/files/monaco.osm.gz"));
        args.putObject("graph.location", args.getString("graph.location", "tools/target/mini-graph-ui-gh"));
        GraphHopperConfig ghConfig = new GraphHopperConfig(args);
        ghConfig.setProfiles(List.of(TestProfiles.accessAndSpeed("profile", "car").setTurnCostsConfig(TurnCostsConfig.car()))).
                putObject("import.osm.ignored_highways", "");
        ghConfig.setCHProfiles(List.of(
                new CHProfile("profile")
        ));
        ghConfig.setLMProfiles(List.of(
                new LMProfile("profile")
        ));
        GraphHopper hopper = new GraphHopper().init(ghConfig).importOrLoad();
        boolean debug = args.getBool("minigraphui.debug", false);
        boolean useCH = args.getBool("minigraphui.useCH", false);
        new MiniGraphUI(hopper, debug, useCH).visualize();
    }

    public MiniGraphUI(GraphHopper hopper, boolean debug, boolean useCH) {
        this.graph = hopper.getBaseGraph();
        this.na = graph.getNodeAccess();
        accessEnc = hopper.getEncodingManager().getBooleanEncodedValue(VehicleAccess.key("car"));
        avSpeedEnc = hopper.getEncodingManager().getDecimalEncodedValue(VehicleSpeed.key("car"));
        this.useCH = useCH;

        logger.info("locations:" + graph.getNodes() + ", debug:" + debug);
        mg = new GraphicsWrapper(graph);

        this.index = (LocationIndexTree) hopper.getLocationIndex();
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

        mainPanel = new LayeredPanel();

        // TODO make it correct with bitset-skipping too
        final GHBitSet bitset = new GHTBitSet(graph.getNodes());
        mainPanel.addLayer(roadsLayer = new DefaultMapLayer() {
            final Random rand = new Random();

            @Override
            public void paintComponent(final Graphics2D g2) {
                clearGraphics(g2);
                Rectangle d = getBounds();
                BBox b = mg.setBounds(0, d.width, 0, d.height);
                if (fastPaint) {
                    rand.setSeed(0);
                    bitset.clear();
                }

                g2.setColor(Color.black);

                Color[] speedColors = generateColors(15);
                AllEdgesIterator edge = graph.getAllEdges();
                while (edge.next()) {
                    if (fastPaint && rand.nextInt(30) > 1)
                        continue;

                    int nodeIndex = edge.getBaseNode();
                    double lat = na.getLat(nodeIndex);
                    double lon = na.getLon(nodeIndex);
                    int nodeId = edge.getAdjNode();
                    double lat2 = na.getLat(nodeId);
                    double lon2 = na.getLon(nodeId);

                    // mg.plotText(g2, lat, lon, "" + nodeIndex);
                    if (!b.contains(lat, lon) && !b.contains(lat2, lon2))
                        continue;

                    int sum = nodeIndex + nodeId;
                    if (fastPaint) {
                        if (bitset.contains(sum))
                            continue;

                        bitset.add(sum);
                    }

                    // mg.plotText(g2, lat * 0.9 + lat2 * 0.1, lon * 0.9 + lon2 * 0.1, iter.getName());
                    //mg.plotText(g2, lat * 0.9 + lat2 * 0.1, lon * 0.9 + lon2 * 0.1, "s:" + (int) encoder.getSpeed(iter.getFlags()));
                    double speed = edge.get(avSpeedEnc);
                    Color color;
                    if (speed >= 120) {
                        // red
                        color = speedColors[12];
                    } else if (speed >= 100) {
                        color = speedColors[10];
                    } else if (speed >= 80) {
                        color = speedColors[8];
                    } else if (speed >= 60) {
                        color = speedColors[6];
                    } else if (speed >= 50) {
                        color = speedColors[5];
                    } else if (speed >= 40) {
                        color = speedColors[4];
                    } else if (speed >= 30) {
                        color = Color.GRAY;
                    } else {
                        color = Color.LIGHT_GRAY;
                    }

                    g2.setColor(color);
                    boolean fwd = edge.get(accessEnc);
                    boolean bwd = edge.getReverse(accessEnc);
                    float width = speed > 90 ? 1f : 0.8f;
                    PointList pl = edge.fetchWayGeometry(FetchMode.ALL);
                    for (int i = 1; i < pl.size(); i++) {
                        if (fwd && !bwd) {
                            mg.plotDirectedEdge(g2, pl.getLat(i - 1), pl.getLon(i - 1), pl.getLat(i), pl.getLon(i), width);
                        } else {
                            mg.plotEdge(g2, pl.getLat(i - 1), pl.getLon(i - 1), pl.getLat(i), pl.getLon(i), width);
                        }
                    }
                }

                if (showQuadTree)
                    index.query(graph.getBounds(), new LocationIndexTree.Visitor() {
                        @Override
                        public boolean isTileInfo() {
                            return true;
                        }

                        @Override
                        public void onTile(BBox bbox, int depth) {
                            int width = Math.max(1, Math.min(4, 4 - depth));
                            g2.setColor(Color.GRAY);
                            mg.plotEdge(g2, bbox.minLat, bbox.minLon, bbox.minLat, bbox.maxLon, width);
                            mg.plotEdge(g2, bbox.minLat, bbox.maxLon, bbox.maxLat, bbox.maxLon, width);
                            mg.plotEdge(g2, bbox.maxLat, bbox.maxLon, bbox.maxLat, bbox.minLon, width);
                            mg.plotEdge(g2, bbox.maxLat, bbox.minLon, bbox.minLat, bbox.minLon, width);
                        }

                        @Override
                        public void onEdge(int edgeId) {
                            // mg.plotNode(g2, node, Color.BLUE);
                        }
                    });

                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, 1000, 20);
                for (int i = 4; i < speedColors.length; i++) {
                    g2.setColor(speedColors[i]);
                    g2.drawString("" + (i * 10), i * 30 - 100, 10);
                }

                g2.setColor(Color.BLACK);
            }
        });

        mainPanel.addLayer(pathLayer = new DefaultMapLayer() {
            @Override
            public void paintComponent(final Graphics2D g2) {
                if (qGraph == null)
                    return;

                makeTransparent(g2);
                RoutingAlgorithm algo = createAlgo(hopper, qGraph);
                if (algo instanceof DebugAlgo) {
                    ((DebugAlgo) algo).setGraphics2D(g2);
                }

                StopWatch sw = new StopWatch().start();
                logger.info("start searching with " + algo + " from:" + fromRes + " to:" + toRes);

                Color red = Color.red.brighter();
                g2.setColor(red);
                mg.plotNode(g2, qGraph.getNodeAccess(), fromRes.getClosestNode(), red, 10, "");
                mg.plotNode(g2, qGraph.getNodeAccess(), toRes.getClosestNode(), red, 10, "");

                g2.setColor(Color.blue.brighter().brighter());
                java.util.List<Path> paths = algo.calcPaths(fromRes.getClosestNode(), toRes.getClosestNode());
                sw.stop();

                // if directed edges
                if (paths.isEmpty() || !paths.get(0).isFound()) {
                    logger.warn("path not found! direction not valid?");
                    return;
                }
                Path best = paths.get(0);
                logger.info("found path in " + sw.getSeconds() + "s with nodes:"
                        + best.calcNodes().size() + ", millis: " + best.getTime()
                        + ", visited nodes:" + algo.getVisitedNodes());
                g2.setColor(red);
                for (Path p : paths)
                    plotPath(p, g2, 3);
            }
        });

        if (debug) {
            // disable double buffering to see graphic changes while debugging. E.g. to set a break point in the
            // algorithm its updateBest method and see the shortest path tree increasing everytime the program continues.
            RepaintManager repaintManager = RepaintManager.currentManager(mainPanel);
            repaintManager.setDoubleBufferingEnabled(false);
            mainPanel.setBuffering(false);
        }
    }

    private RoutingAlgorithm createAlgo(GraphHopper hopper, QueryGraph qGraph) {
        Profile profile = hopper.getProfiles().iterator().next();
        if (useCH) {
            RoutingCHGraph chGraph = hopper.getCHGraphs().get(profile.getName());
            logger.info("CH algo, profile: " + profile.getName());
            QueryRoutingCHGraph queryRoutingCHGraph = new QueryRoutingCHGraph(chGraph, qGraph);
            return new CHDebugAlgo(queryRoutingCHGraph, mg);
        } else {
            LandmarkStorage landmarks = hopper.getLandmarks().get(profile.getName());
            RoutingAlgorithmFactory algoFactory = (g, w, opts) -> {
                RoutingAlgorithm algo = new LMRoutingAlgorithmFactory(landmarks).createAlgo(g, w, opts);
                if (algo instanceof AStarBidirection) {
                    return new DebugAStarBi(g, w, opts.getTraversalMode(), mg).
                            setApproximation(((AStarBidirection) algo).getApproximation());
                } else if (algo instanceof AStar) {
                    return new DebugAStar(g, w, opts.getTraversalMode(), mg);
                } else if (algo instanceof DijkstraBidirectionRef) {
                    return new DebugDijkstraBidirection(g, w, opts.getTraversalMode(), mg);
                } else if (algo instanceof Dijkstra) {
                    return new DebugDijkstraSimple(g, w, opts.getTraversalMode(), mg);
                } else
                    return algo;
            };
            AlgorithmOptions algoOpts = new AlgorithmOptions().setAlgorithm(Algorithms.ASTAR_BI).
                    setTraversalMode(TraversalMode.EDGE_BASED);
            return algoFactory.createAlgo(qGraph, hopper.createWeighting(profile, new PMap()), algoOpts);
        }
    }

    private static class CHDebugAlgo extends DijkstraBidirectionCH implements DebugAlgo {
        private final GraphicsWrapper mg;
        private Graphics2D g2;

        public CHDebugAlgo(RoutingCHGraph graph, GraphicsWrapper mg) {
            super(graph);
            this.mg = mg;
        }

        @Override
        public void setGraphics2D(Graphics2D g2) {
            this.g2 = g2;
        }

        @Override
        public void updateBestPath(double edgeWeight, SPTEntry entry, int origEdgeId, int traversalId, boolean reverse) {
            if (g2 != null)
                mg.plotNode(g2, traversalId, Color.YELLOW, 6);

            super.updateBestPath(edgeWeight, entry, origEdgeId, traversalId, reverse);
        }
    }

    public Color[] generateColors(int n) {
        Color[] cols = new Color[n];
        for (int i = 0; i < n; i++) {
            cols[i] = Color.getHSBColor((float) i / (float) n, 0.85f, 1.0f);
        }
        return cols;
    }

    void plotNodeName(Graphics2D g2, int node) {
        double lat = na.getLat(node);
        double lon = na.getLon(node);
        mg.plotText(g2, lat, lon, "" + node);
    }

    private Path plotPath(Path tmpPath, Graphics2D g2, int w) {
        if (!tmpPath.isFound()) {
            logger.info("cannot plot path as not found: " + tmpPath);
            return tmpPath;
        }

        double prevLat = Double.NaN;
        double prevLon = Double.NaN;
        boolean plotNodes = false;
        IntIndexedContainer nodes = tmpPath.calcNodes();
        if (plotNodes) {
            for (int i = 0; i < nodes.size(); i++) {
                plotNodeName(g2, nodes.get(i));
            }
        }
        PointList list = tmpPath.calcPoints();
        for (int i = 0; i < list.size(); i++) {
            double lat = list.getLat(i);
            double lon = list.getLon(i);
            if (!Double.isNaN(prevLat)) {
                mg.plotEdge(g2, prevLat, prevLon, lat, lon, w);
            } else {
                mg.plot(g2, lat, lon, w);
            }
            prevLat = lat;
            prevLon = lon;
        }
        logger.info("dist:" + tmpPath.getDistance() + ", path points(" + list.size() + ")");
        return tmpPath;
    }

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

                    // listener to investigate findID behavior
//                    MouseAdapter ml = new MouseAdapter() {
//
//                        @Override public void mouseClicked(MouseEvent e) {
//                            findIDLat = mg.getLat(e.getY());
//                            findIDLon = mg.getLon(e.getX());
//                            findIdLayer.repaint();
//                            mainPanel.repaint();
//                        }
//
//                        @Override public void mouseMoved(MouseEvent e) {
//                            updateLatLon(e);
//                        }
//
//                        @Override public void mousePressed(MouseEvent e) {
//                            updateLatLon(e);
//                        }
//                    };
                    MouseAdapter ml = new MouseAdapter() {
                        // for routing:
                        double fromLat, fromLon;
                        boolean fromDone = false;
                        boolean dragging = false;

                        @Override
                        public void mouseClicked(MouseEvent e) {
                            if (!fromDone) {
                                fromLat = mg.getLat(e.getY());
                                fromLon = mg.getLon(e.getX());
                            } else {
                                double toLat = mg.getLat(e.getY());
                                double toLon = mg.getLon(e.getX());
                                StopWatch sw = new StopWatch().start();
                                logger.info("start searching from " + fromLat + "," + fromLon
                                        + " to " + toLat + "," + toLon);
                                // get from and to node id
                                fromRes = index.findClosest(fromLat, fromLon, EdgeFilter.ALL_EDGES);
                                toRes = index.findClosest(toLat, toLon, EdgeFilter.ALL_EDGES);
                                if (fromRes.isValid() && toRes.isValid()) {
                                    qGraph = QueryGraph.create(graph, fromRes, toRes);
                                    mg.setNodeAccess(qGraph);
                                    logger.info("found ids " + fromRes + " -> " + toRes + " in " + sw.stop().getSeconds() + "s");
                                }

                                repaintPaths();
                            }

                            fromDone = !fromDone;
                        }

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

                    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    frame.setSize(frameWidth + 10, frameHeight + 30);
                    frame.setVisible(true);
                }
            });
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

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
