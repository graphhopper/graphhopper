/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.ui;

import de.jetsli.graph.coll.MyBitSet;
import de.jetsli.graph.coll.MyTBitSet;
import de.jetsli.graph.reader.OSMReader;
import de.jetsli.graph.routing.AStar;
import de.jetsli.graph.routing.Path;
import de.jetsli.graph.routing.RoutingAlgorithm;
import de.jetsli.graph.routing.util.FastestCalc;
import de.jetsli.graph.routing.util.ShortestCalc;
import de.jetsli.graph.routing.util.WeightCalculation;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.Location2IDQuadtree;
import de.jetsli.graph.trees.QuadTree;
import de.jetsli.graph.util.CmdArgs;
import de.jetsli.graph.util.CoordTrig;
import de.jetsli.graph.util.EdgeIterator;
import de.jetsli.graph.util.Helper;
import de.jetsli.graph.util.StopWatch;
import de.jetsli.graph.util.shapes.BBox;
import java.awt.*;
import java.awt.event.*;
import java.util.Collection;
import java.util.Random;
import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A rough graphical user interface for visualizing the OSM graph. Mainly for debugging algorithms
 * and spatial datastructures.
 *
 * @author Peter Karich
 */
public class MiniGraphUI {

    public static void main(String[] strs) throws Exception {
        CmdArgs args = Helper.readCmdArgs(strs);
        Graph g = OSMReader.osm2Graph(args);
        boolean debug = args.getBool("debug", false);
        new MiniGraphUI(g, debug).visualize();
    }
    private Logger logger = LoggerFactory.getLogger(getClass());
    private QuadTree<Long> quadTree;
    private Collection<CoordTrig<Long>> quadTreeNodes;
    private Path path;
    private RoutingAlgorithm algo;
    private final Graph graph;
    private Location2IDQuadtree index;
    private String latLon = "";
    private MyGraphics mg;
    private JPanel infoPanel;
    private MyLayerPanel mainPanel;
    private MapLayer roadsLayer;
    private MapLayer pathLayer;
    private boolean fastPaint = false;

    public MiniGraphUI(Graph roadGraph, boolean debug) {
        this.graph = roadGraph;
        logger.info("locations:" + roadGraph.getNodes() + ", debug:" + debug);
        mg = new MyGraphics(roadGraph);

        // prepare location quadtree to 'enter' the graph. create a 313*313 grid => <3km
//         this.index = new DebugLocation2IDQuadtree(roadGraph, mg);
        this.index = new Location2IDQuadtree(roadGraph);
        index.prepareIndex(90000);
//        this.algo = new DebugDijkstraBidirection(graph, mg);
        // this.algo = new DijkstraBidirection(graph);
//        this.algo = new DebugAStar(graph, mg);
        this.algo = new AStar(graph);
//        this.algo = new DijkstraSimple(graph);
//        this.algo = new DebugDijkstraSimple(graph, mg);
        infoPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
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

        mainPanel = new MyLayerPanel();

        // TODO make it correct with bitset-skipping too
        final MyBitSet bitset = new MyTBitSet(graph.getNodes());
        mainPanel.addLayer(roadsLayer = new DefaultMapLayer() {
            Random rand = new Random();

            @Override public void paintComponent(Graphics2D g2) {
                clearGraphics(g2);
                int locs = graph.getNodes();
                Rectangle d = getBounds();
                BBox b = mg.setBounds(0, d.width, 0, d.height);
                if (fastPaint) {
                    rand.setSeed(0);
                    bitset.clear();
                }

                for (int nodeIndex = 0; nodeIndex < locs; nodeIndex++) {
                    if (fastPaint && rand.nextInt(30) > 1)
                        continue;
                    double lat = graph.getLatitude(nodeIndex);
                    double lon = graph.getLongitude(nodeIndex);
                    if (lat < b.minLat || lat > b.maxLat || lon < b.minLon || lon > b.maxLon)
                        continue;

//                    int count = MyIteratorable.count(graph.getEdges(nodeIndex));
//                    mg.plotNode(g2, nodeIndex, Color.RED);                    

                    EdgeIterator iter = graph.getOutgoing(nodeIndex);
                    while (iter.next()) {

                        int nodeId = iter.node();
                        int sum = nodeIndex + nodeId;
                        if (fastPaint) {
                            if (bitset.contains(sum))
                                continue;
                            bitset.add(sum);
                        }
                        double lat2 = graph.getLatitude(nodeId);
                        double lon2 = graph.getLongitude(nodeId);
                        if (lat2 <= 0 || lon2 <= 0)
                            logger.info("ERROR " + nodeId + " " + iter.distance() + " " + lat2 + "," + lon2);
                        mg.plotEdge(g2, lat, lon, lat2, lon2);
                    }
                }

//                mg.plotNode(g2, 171651, Color.GREEN);
//                g2.setColor(Color.RED);
//                PriorityGraph clone = (PriorityGraph) graph.clone();
//                new PrepareRoutingShortcuts(clone).doWork();
//                DijkstraBidirectionRef dijkstraBi = new DebugDijkstraBidirection(clone, mg);
//                ((DebugAlgo) dijkstraBi).setGraphics2D(g2);
//                dijkstraBi.setEdgeFilter(new EdgePrioFilter((PriorityGraph) clone));
//                plotPath(dijkstraBi, g2, 10);
//                Path p1 = calcPath(dijkstraBi);

//                g2.setColor(Color.GREEN);
//                dijkstraBi = new DebugDijkstraBidirection(graph, mg);
//                ((DebugAlgo) dijkstraBi).setGraphics2D(g2);
//                plotPath(dijkstraBi, g2, 6);

//                Path p2 = calcPath(dijkstraBi);
//                Path.debugDifference(clone, p1, p2);

//                g2.setColor(Color.BLUE);
//                plotPath(new AStar(graph), g2, 4);
//                g2.setColor(Color.BLACK);

                if (quadTreeNodes != null) {
                    logger.info("found neighbors:" + quadTreeNodes.size());
                    for (CoordTrig<Long> coord : quadTreeNodes) {
                        mg.plot(g2, coord.lat, coord.lon, 1);
                    }
                }
            }
        });

        mainPanel.addLayer(pathLayer = new DefaultMapLayer() {
            // one time use the fastest path, the other time use the shortest (e.g. maximize window to switch)
            WeightCalculation wCalc = FastestCalc.DEFAULT;

            @Override public void paintComponent(Graphics2D g2) {
                if (dijkstraFromId < 0 || dijkstraToId < 0)
                    return;

                makeTransparent(g2);
                if (algo instanceof DebugAlgo)
                    ((DebugAlgo) algo).setGraphics2D(g2);

                StopWatch sw = new StopWatch().start();
                if (wCalc == FastestCalc.DEFAULT)
                    wCalc = ShortestCalc.DEFAULT;
                else
                    wCalc = FastestCalc.DEFAULT;

                logger.info("start searching from:" + dijkstraFromId + " to:" + dijkstraToId + " " + wCalc);
                path = algo.clear().setType(wCalc).calcPath(dijkstraFromId, dijkstraToId);
                sw.stop();

                // if directed edges
                if (path == null)
                    return;

                logger.info("found path in " + sw.getSeconds() + "s with " + path.locations() + " nodes: " + path);
                g2.setColor(Color.BLUE.brighter().brighter());
                int tmpLocs = path.locations();
                double prevLat = -1;
                double prevLon = -1;
                for (int i = 0; i < tmpLocs; i++) {
                    int id = path.location(i);
                    double lat = graph.getLatitude(id);
                    double lon = graph.getLongitude(id);
                    if (prevLat >= 0)
                        mg.plotEdge(g2, prevLat, prevLon, lat, lon, 3);

                    prevLat = lat;
                    prevLon = lon;
                }
            }
        });

        if (debug) {
            // disable double buffering for debugging drawing - nice! when do we need DebugGraphics then?
            RepaintManager repaintManager = RepaintManager.currentManager(mainPanel);
            repaintManager.setDoubleBufferingEnabled(false);
            mainPanel.setBuffering(false);
        }
    }

    // for debugging
    private Path calcPath(RoutingAlgorithm algo) {
//        int from = index.findID(49.8020, 9.2470);
//        int to = index.findID(50.4940, 10.1970);
//        return algo.calcPath(from, to);
        return algo.calcPath(309721, 309742);
    }

    private Path plotPath(RoutingAlgorithm algo, Graphics2D g2, int w) {
        Path tmpPath = calcPath(algo);
        for (int jj = 0; jj < tmpPath.locations(); jj++) {
            int loc = tmpPath.location(jj);
            double lat = graph.getLatitude(loc);
            double lon = graph.getLongitude(loc);
            mg.plot(g2, lat, lon, w);
        }
        logger.info(tmpPath.toString());
        return tmpPath;
    }
    private int dijkstraFromId = -1;
    private int dijkstraToId = -1;

    public void visualize() {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override public void run() {
                    int frameHeight = 800;
                    int frameWidth = 1200;
                    JFrame frame = new JFrame("GraphHopper UI - Small&Ugly ;)");
                    frame.setLayout(new BorderLayout());
                    frame.add(mainPanel, BorderLayout.CENTER);
                    frame.add(infoPanel, BorderLayout.NORTH);

                    infoPanel.setPreferredSize(new Dimension(300, 100));

                    // scale
                    mainPanel.addMouseWheelListener(new MouseWheelListener() {
                        @Override public void mouseWheelMoved(MouseWheelEvent e) {
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

                        @Override public void mouseClicked(MouseEvent e) {
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
                                dijkstraFromId = index.findID(fromLat, fromLon);
                                dijkstraToId = index.findID(toLat, toLon);
                                logger.info("found ids " + dijkstraFromId + " -> " + dijkstraToId + " in " + sw.stop().getSeconds() + "s");

                                repaintPaths();
                            }

                            fromDone = !fromDone;
                        }
                        boolean dragging = false;

                        @Override public void mouseDragged(MouseEvent e) {
                            dragging = true;
                            fastPaint = true;
                            update(e);
                            updateLatLon(e);
                        }

                        @Override public void mouseReleased(MouseEvent e) {
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

                        @Override public void mouseMoved(MouseEvent e) {
                            updateLatLon(e);
                        }

                        @Override public void mousePressed(MouseEvent e) {
                            updateLatLon(e);
                        }
                    };
                    mainPanel.addMouseListener(ml);
                    mainPanel.addMouseMotionListener(ml);

                    // just for fun
                    mainPanel.getInputMap().put(KeyStroke.getKeyStroke("DELETE"), "deleteNodes");
                    mainPanel.getActionMap().put("deleteNodes", new AbstractAction() {
                        @Override public void actionPerformed(ActionEvent e) {
                            int counter = 0;
                            for (CoordTrig<Long> coord : quadTreeNodes) {
                                int ret = quadTree.remove(coord.lat, coord.lon);
                                if (ret < 1) {
//                                    logger.info("cannot remove " + coord + " " + ret);
//                                    ret = quadTree.remove(coord.getLatitude(), coord.getLongitude());
                                } else
                                    counter += ret;
                            }
                            logger.info("Deleted " + counter + " of " + quadTreeNodes.size() + " nodes");
                        }
                    });

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
