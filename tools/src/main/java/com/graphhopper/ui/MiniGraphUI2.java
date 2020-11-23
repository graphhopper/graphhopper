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
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.*;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.lm.PrepareLandmarks;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.CHConfig;
import com.graphhopper.storage.Graph;
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
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

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
public class MiniGraphUI2 {
    private static final Logger logger = LoggerFactory.getLogger(MiniGraphUI.class);
    final AtomicBoolean repainting = new AtomicBoolean();
    private final Graph graph;
    private final NodeAccess na;
    private final MapLayer pathLayer;
//    private final FlagEncoder encoder;
    private final DecimalEncodedValue avSpeedEnc;
    private final BooleanEncodedValue accessEnc;
    private final boolean useCH;
    // for moving
    int currentPosX;
    int currentPosY;
    private Path path;
    private final LocationIndexTree index;
    private String latLon = "";
    private final GraphicsWrapper mg;
    private JPanel infoPanel;
    private LayeredPanel mainPanel;
    private MapLayer roadsLayer;
    private boolean fastPaint = false;
    private boolean showTiles = false;
    private Snap fromRes;
    private Snap toRes;

    public MiniGraphUI2(GraphHopper hopper, boolean debug, boolean useCH) {
        this.graph = hopper.getGraphHopperStorage();
        this.na = graph.getNodeAccess();

        FlagEncoder encoder = hopper.getEncodingManager().fetchEdgeEncoders().get(0);
        avSpeedEnc = encoder.getAverageSpeedEnc();
        accessEnc = encoder.getAccessEnc();

        this.useCH = useCH;

        logger.info("locations:{}, debug:{}", graph.getNodes(), debug);
        mg = new GraphicsWrapper(graph);

        // prepare node quadtree to 'enter' the graph. create a 313*313 grid => <3km
//         this.index = new DebugLocation2IDQuadtree(roadGraph, mg);
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
                final Rectangle bb = mainPanel.getBounds();
                g.drawString(mg.setBounds(0, bb.width, 0, bb.height).toLessPrecisionString(), 40, 60);
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
//                int locs = graph.getNodes();
                Rectangle d = getBounds();
                BBox b = mg.setBounds(0, d.width, 0, d.height);
                if (fastPaint) {
                    rand.setSeed(0);
                    bitset.clear();
                }

//                g2.setColor(Color.BLUE);
//                double fromLat = 42.56819, fromLon = 1.603231;
//                mg.plotText(g2, fromLat, fromLon, "from");
//                Snap from = index.findClosest(fromLat, fromLon, EdgeFilter.ALL_EDGES);
//                double toLat = 42.571034, toLon = 1.520662;
//                mg.plotText(g2, toLat, toLon, "to");
//                Snap to = index.findClosest(toLat, toLon, EdgeFilter.ALL_EDGES);
//
//                g2.setColor(Color.RED.brighter().brighter());
//                path = prepare.createAlgo().calcPath(from, to);
//                System.out.println("now: " + path.toFlagEncodersAsString());
//                plotPath(path, g2, 1);
                g2.setColor(Color.black);

                Color[] speedColors = generateColors(15);
                AllEdgesIterator edge = graph.getAllEdges();
                while (edge.next()) {
                    if (fastPaint && rand.nextInt(30) > 1)
                        continue;

                    int nodeIndex = edge.getBaseNode();
                    double lat = na.getLatitude(nodeIndex);
                    double lon = na.getLongitude(nodeIndex);
                    int nodeId = edge.getAdjNode();
                    double lat2 = na.getLatitude(nodeId);
                    double lon2 = na.getLongitude(nodeId);

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
                    final int n = pl.size();
                    g2.setStroke(new BasicStroke(width));
                    for (int i = 1; i < n; i++) {
                        if (fwd && !bwd) {
                            mg.plotDirectedEdge(g2, pl.getLatitude(i - 1), pl.getLongitude(i - 1), pl.getLatitude(i), pl.getLongitude(i), 1);
                        } else {
                            mg.plotEdge(g2, pl.getLatitude(i - 1), pl.getLongitude(i - 1), pl.getLatitude(i), pl.getLongitude(i), 1);
                        }
                    }
                }

                if (showTiles) {
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
                        public void onNode(int node) {
                            // mg.plotNode(g2, node, Color.BLUE);
                        }
                    });
//
                }

                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, 1000, 20);
                for (int i = 4; i < speedColors.length; i++) {
                    g2.setColor(speedColors[i]);
                    g2.drawString(String.valueOf(i * 10), i * 30 - 100, 10);
                }

                g2.setColor(Color.BLACK);
            }
        });

        mainPanel.addLayer(pathLayer = new DefaultMapLayer() {
            @Override
            public void paintComponent(final Graphics2D g2) {
                if (fromRes == null || toRes == null)
                    return;

                makeTransparent(g2);
                QueryGraph qGraph = QueryGraph.create(graph, fromRes, toRes);
                RoutingAlgorithm algo = createAlgo(hopper);
                if (algo instanceof DebugAlgo) {
                    ((DebugAlgo) algo).setGraphics2D(g2);
                }

                StopWatch sw = new StopWatch().start();
                logger.info("start searching with {} from:{} to:{}", algo, fromRes, toRes);

//                GHPoint qp = fromRes.getQueryPoint();
//                TIntHashSet set = index.findNetworkEntries(qp.lat, qp.lon, 1);
//                TIntIterator nodeIter = set.iterator();
//                DistanceCalc distCalc = new DistancePlaneProjection();
//                System.out.println("set:" + set.size());
//                while (nodeIter.hasNext())
//                {
//                    int nodeId = nodeIter.next();
//                    double lat = graph.getNodeAccess().getLat(nodeId);
//                    double lon = graph.getNodeAccess().getLon(nodeId);
//                    int dist = (int) Math.round(distCalc.calcDist(qp.lat, qp.lon, lat, lon));
//                    mg.plotText(g2, lat, lon, nodeId + ": " + dist);
//                    mg.plotNode(g2, nodeId, Color.red);
//                }
                Color red = Color.red.brighter();
                g2.setColor(red);
                mg.plotNode(g2, qGraph.getNodeAccess(), fromRes.getClosestNode(), red, 10, "");
                mg.plotNode(g2, qGraph.getNodeAccess(), toRes.getClosestNode(), red, 10, "");

                g2.setColor(Color.blue.brighter().brighter());
                path = algo.calcPath(fromRes.getClosestNode(), toRes.getClosestNode());
                sw.stop();

                // if directed edges
                if (!path.isFound()) {
                    logger.warn("path not found! direction not valid?");
                    return;
                }

                logger.info("found path in {}s with nodes:{}, millis: {}, visited nodes:{}", sw.getSeconds(), path.calcNodes().size(), path.getTime(), algo.getVisitedNodes());
                g2.setColor(red);
                plotPath(path, g2, 4);
            }
        });

        if (debug) {
            // disable double buffering for debugging drawing - nice! when do we need DebugGraphics then?
            RepaintManager repaintManager = RepaintManager.currentManager(mainPanel);
            repaintManager.setDoubleBufferingEnabled(false);
            mainPanel.setBuffering(false);
        }
    }

    public static void main(String[] strs) {
        PMap args = PMap.read(strs);
        args.putObject("datareader.file", args.getString("datareader.file", "core/files/monaco.osm.gz"));
        args.putObject("graph.location", args.getString("graph.location", "tools/target/mini-graph-ui-gh"));
        args.putObject("graph.flag_encoders", args.getString("graph.flag_encoders", "car"));
        GraphHopperConfig ghConfig = new GraphHopperConfig(args);
        ghConfig.setProfiles(Collections.singletonList(
                new Profile("profile")
                        .setVehicle("car")
                        .setWeighting("fastest")
        ));
        ghConfig.setCHProfiles(Collections.singletonList(
                new CHProfile("profile")
        ));
        ghConfig.setLMProfiles(Collections.singletonList(
                new LMProfile("profile")
        ));
        GraphHopper hopper = new GraphHopperOSM().init(ghConfig).importOrLoad();
        boolean debug = args.getBool("minigraphui.debug", false);
        boolean useCH = args.getBool("minigraphui.useCH", false);
        new MiniGraphUI2(hopper, debug, useCH).visualize();
    }

    private RoutingAlgorithm createAlgo(GraphHopper hopper) {
        Profile profile = hopper.getProfiles().iterator().next();
        if (useCH) {
            CHConfig chConfig = hopper.getCHPreparationHandler().getNodeBasedCHConfigs().get(0);
            Weighting weighting = chConfig.getWeighting();
            logger.info("CH algo, weighting: {}", weighting);
            return new CHDebugAlgo(
                new QueryRoutingCHGraph(
                    hopper.getGraphHopperStorage().getRoutingCHGraph(chConfig.getName()),
                    QueryGraph.create(hopper.getGraphHopperStorage(),
                    fromRes, toRes)), mg);
        } else {
            Weighting weighting = hopper.createWeighting(profile, new PMap());
            final PrepareLandmarks preparation = hopper.getLMPreparationHandler().getPreparation(profile.getName());
            RoutingAlgorithmFactory algoFactory = (g, opts) -> {
                RoutingAlgorithm algo = preparation.getRoutingAlgorithmFactory().createAlgo(g, opts);
                if (algo instanceof AStarBidirection) {
                    return new DebugAStarBi(g, opts.getWeighting(), opts.getTraversalMode(), mg).
                            setApproximation(((AStarBidirection) algo).getApproximation());
                } else if (algo instanceof AStar) {
                    return new DebugAStar(g, opts.getWeighting(), opts.getTraversalMode(), mg);
                } else if (algo instanceof DijkstraBidirectionRef) {
                    return new DebugDijkstraBidirection(g, opts.getWeighting(), opts.getTraversalMode(), mg);
                } else if (algo instanceof Dijkstra) {
                    return new DebugDijkstraSimple(g, opts.getWeighting(), opts.getTraversalMode(), mg);
                }
                return algo;
            };
            AlgorithmOptions algoOpts = new AlgorithmOptions(Algorithms.ASTAR_BI, weighting);
            logger.info("algoOpts:{}, weighting: {}", algoOpts, weighting);
            QueryGraph qGraph = QueryGraph.create(graph, fromRes, toRes);
            return algoFactory.createAlgo(qGraph, algoOpts);
        }
    }

    public static Color[] generateColors(int n) {
        Color[] cols = new Color[n];
        for (int i = 0; i < n; i++) {
            cols[i] = Color.getHSBColor( i / ((float) n), 0.85f, 1.0f);
        }
        return cols;
    }

    // for debugging
    private static Path calcPath(RoutingAlgorithm algo) {
//        int from = index.findID(50.042, 10.19);
//        int to = index.findID(50.049, 10.23);
//
////        System.out.println("path " + from + "->" + to);
//        return algo.calcPath(from, to);
        // System.out.println(GraphUtility.getNodeInfo(graph, 60139, DefaultEdgeFilter.allEdges(new CarFlagEncoder()).direction(false, true)));
        // System.out.println(((GraphStorage) graph).debug(202947, 10));
//        GraphUtility.printInfo(graph, 106511, 10);
        return algo.calcPath(162810, 35120);
    }

    void plotNodeName(Graphics2D g2, int node) {
        double lat = na.getLatitude(node);
        double lon = na.getLongitude(node);
        mg.plotText(g2, lat, lon, String.valueOf(node));
    }

    private Path plotPath(Path tmpPath, Graphics2D g2, int w) {
        if (!tmpPath.isFound()) {
            logger.info("nothing found {}", w);
            return tmpPath;
        }


        boolean plotNodes = false;
        IntIndexedContainer nodes = tmpPath.calcNodes();
        if (plotNodes) {
            final int n = nodes.size();
            for (int i = 0; i < n; i++) {
                plotNodeName(g2, nodes.get(i));
            }
        }
        PointList list = tmpPath.calcPoints();
        final int p = list.getSize();
        double prevLat = Double.NaN;
        double prevLon = Double.NaN;
        for (int i = 0; i < p; i++) {
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
        logger.info("dist:{}, path points({})", tmpPath.getDistance(), p);
        return tmpPath;
    }

    private void init() {
        int frameHeight = 800;
        int frameWidth = 1200;
        JFrame frame = new JFrame("GraphHopper UI - Small&Ugly ;)");

        //frame.setIgnoreRepaint(true);
        frame.setLayout(new BorderLayout());
        frame.add(mainPanel, BorderLayout.CENTER);
        frame.add(infoPanel, BorderLayout.NORTH);

        infoPanel.setPreferredSize(new Dimension(300, 100));

        // scale
        mainPanel.addMouseWheelListener(e -> {
            mg.scale(e.getX(), e.getY(), e.getWheelRotation() < 0);
            repaint(true, true);
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
                    logger.info("start searching from {},{} to {},{}", fromLat, fromLon, toLat, toLon);
                    // get from and to node id
                    fromRes = index.findClosest(fromLat, fromLon, EdgeFilter.ALL_EDGES);
                    toRes = index.findClosest(toLat, toLon, EdgeFilter.ALL_EDGES);
                    logger.info("found ids {} -> {} in {}s", fromRes, toRes, sw.stop().getSeconds());

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
                repaint(true, true);
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

    public void visualize() {
        try {
            SwingUtilities.invokeAndWait(this::init);
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
        repaint(false, true);
    }

    void repaint(boolean roads, boolean paths) {
        if (repainting.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(() -> {
                repainting.set(false);

                // avoid threading as there should be no updated to scale or offset while painting
                // (would to lead to artifacts)
                //final StopWatch sw = new StopWatch().start();

                if (paths)
                    pathLayer.repaint();
                if (roads)
                    roadsLayer.repaint();

                mainPanel.repaint();

                //logger.info("roads painting took " + sw.stop().getSeconds() + " sec");
            });
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
}
