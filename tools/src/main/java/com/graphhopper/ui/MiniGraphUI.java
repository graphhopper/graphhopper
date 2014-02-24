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
package com.graphhopper.ui;

import com.graphhopper.GraphHopper;
import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHTBitSet;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.AlgorithmPreparation;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FastestWeighting;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.NoOpAlgorithmPreparation;
import com.graphhopper.routing.util.ShortestWeighting;
import com.graphhopper.routing.util.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import gnu.trove.list.TIntList;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;
import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A rough graphical user interface for visualizing the OSM graph. Mainly for debugging algorithms
 * and spatial datastructures.
 * <p/>
 * Use the project at https://github.com/graphhopper/graphhopper-web for a
 * better/faster/userfriendly/... alternative!
 * <p/>
 * @author Peter Karich
 */
public class MiniGraphUI
{

    public static void main( String[] strs ) throws Exception
    {
        CmdArgs args = CmdArgs.read(strs);
        GraphHopper hopper = new GraphHopper().init(args).importOrLoad();
        boolean debug = args.getBool("minigraphui.debug", false);
        new MiniGraphUI(hopper, debug).visualize();
    }
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Path path;
    private AlgorithmPreparation prepare;
    private final Graph graph;
    private LocationIndex index;
    private String latLon = "";
    private GraphicsWrapper mg;
    private JPanel infoPanel;
    private LayeredPanel mainPanel;
    private MapLayer roadsLayer;
    private final MapLayer pathLayer;
    private boolean fastPaint = false;
    private final Weighting weighting;

    public MiniGraphUI( GraphHopper hopper, boolean debug )
    {
        this.graph = hopper.getGraph();
        prepare = hopper.getPreparation();
        FlagEncoder encoder = hopper.getEncodingManager().getSingle();
        weighting = new FastestWeighting(encoder);
        if (prepare == null)
            prepare = NoOpAlgorithmPreparation.createAlgoPrepare(graph, "dijkstrabi", encoder, weighting);

        logger.info("locations:" + graph.getNodes() + ", debug:" + debug + ", algo:" + prepare.createAlgo().getName());
        mg = new GraphicsWrapper(graph);

        // prepare node quadtree to 'enter' the graph. create a 313*313 grid => <3km
//         this.index = new DebugLocation2IDQuadtree(roadGraph, mg);
        this.index = hopper.getLocationIndex();
//        this.algo = new DebugDijkstraBidirection(graph, mg);
        // this.algo = new DijkstraBidirection(graph);
//        this.algo = new DebugAStar(graph, mg);
//        this.algo = new AStar(graph);
//        this.algo = new DijkstraSimple(graph);
//        this.algo = new DebugDijkstraSimple(graph, mg);
        infoPanel = new JPanel()
        {
            @Override
            protected void paintComponent( Graphics g )
            {
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
        mainPanel.addLayer(roadsLayer = new DefaultMapLayer()
        {
            Random rand = new Random();

            @Override
            public void paintComponent( Graphics2D g2 )
            {
                clearGraphics(g2);
                int locs = graph.getNodes();
                Rectangle d = getBounds();
                BBox b = mg.setBounds(0, d.width, 0, d.height);
                if (fastPaint)
                {
                    rand.setSeed(0);
                    bitset.clear();
                }

//                g2.setColor(Color.BLUE);
//                double fromLat = 42.56819, fromLon = 1.603231;
//                mg.plotText(g2, fromLat, fromLon, "from");
//                QueryResult from = index.findClosest(fromLat, fromLon, EdgeFilter.ALL_EDGES);
//                double toLat = 42.571034, toLon = 1.520662;
//                mg.plotText(g2, toLat, toLon, "to");
//                QueryResult to = index.findClosest(toLat, toLon, EdgeFilter.ALL_EDGES);
//
//                g2.setColor(Color.RED.brighter().brighter());
//                path = prepare.createAlgo().calcPath(from, to);
//                System.out.println("now: " + path.toDetailsString());
//                plotPath(path, g2, 1);
                g2.setColor(Color.black);

                EdgeExplorer explorer = graph.createEdgeExplorer(EdgeFilter.ALL_EDGES);
                for (int nodeIndex = 0; nodeIndex < locs; nodeIndex++)
                {
                    if (fastPaint && rand.nextInt(30) > 1)
                        continue;

                    double lat = graph.getLatitude(nodeIndex);
                    double lon = graph.getLongitude(nodeIndex);
                    // mg.plotText(g2, lat, lon, "" + nodeIndex);
                    if (lat < b.minLat || lat > b.maxLat || lon < b.minLon || lon > b.maxLon)
                        continue;

                    EdgeIterator iter = explorer.setBaseNode(nodeIndex);
                    while (iter.next())
                    {
                        int nodeId = iter.getAdjNode();
                        int sum = nodeIndex + nodeId;
                        if (fastPaint)
                        {
                            if (bitset.contains(sum))
                                continue;

                            bitset.add(sum);
                        }
                        double lat2 = graph.getLatitude(nodeId);
                        double lon2 = graph.getLongitude(nodeId);
                        mg.plotEdge(g2, lat, lon, lat2, lon2);
                    }
                }
            }
        });

        mainPanel.addLayer(pathLayer = new DefaultMapLayer()
        {
            @Override
            public void paintComponent( Graphics2D g2 )
            {
                if (fromRes == null || toRes == null)
                    return;

                makeTransparent(g2);
                RoutingAlgorithm algo = prepare.createAlgo();
                if (algo instanceof DebugAlgo)
                {
                    ((DebugAlgo) algo).setGraphics2D(g2);
                }

                StopWatch sw = new StopWatch().start();
                fromRes = index.findClosest(48.983787742949886, 12.14355546976484, EdgeFilter.ALL_EDGES);
                toRes = index.findClosest(48.98291180219765, 12.13610228968002, EdgeFilter.ALL_EDGES);

                logger.info("start searching from:" + fromRes + " to:" + toRes + " " + weighting);
                path = algo.calcPath(fromRes, toRes);
//                mg.plotNode(g2, dijkstraFromId, Color.red);
//                mg.plotNode(g2, dijkstraToId, Color.BLUE);
                sw.stop();

                // if directed edges
                if (!path.isFound())
                {
                    logger.warn("path not found! direction not valid?");
                    return;
                }

                logger.info("found path in " + sw.getSeconds() + "s with " + path.calcNodes().size() + " nodes: " + path);
                g2.setColor(Color.BLUE.brighter().brighter());
                plotPath(path, g2, 1);
            }
        });

        if (debug)
        {
            // disable double buffering for debugging drawing - nice! when do we need DebugGraphics then?
            RepaintManager repaintManager = RepaintManager.currentManager(mainPanel);
            repaintManager.setDoubleBufferingEnabled(false);
            mainPanel.setBuffering(false);
        }
    }

    // for debugging
    private Path calcPath( RoutingAlgorithm algo )
    {
//        int from = index.findID(50.042, 10.19);
//        int to = index.findID(50.049, 10.23);
//
////        System.out.println("path " + from + "->" + to);
//        return algo.calcPath(from, to);
        // System.out.println(GraphUtility.getNodeInfo(graph, 60139, new DefaultEdgeFilter(new CarFlagEncoder()).direction(false, true)));
        // System.out.println(((GraphStorage) graph).debug(202947, 10));
//        GraphUtility.printInfo(graph, 106511, 10);
        return algo.calcPath(162810, 35120);
    }

    void plotNodeName( Graphics2D g2, int node )
    {
        double lat = graph.getLatitude(node);
        double lon = graph.getLongitude(node);
        mg.plotText(g2, lat, lon, "" + node);
    }

    private Path plotPath( Path tmpPath, Graphics2D g2, int w )
    {
        if (!tmpPath.isFound())
        {
            logger.info("nothing found " + w);
            return tmpPath;
        }

        double prevLat = Double.NaN;
        double prevLon = Double.NaN;
        boolean plotNodes = true;
        TIntList nodes = tmpPath.calcNodes();
        if (plotNodes)
        {
            for (int i = 0; i < nodes.size(); i++)
            {
                plotNodeName(g2, nodes.get(i));
            }
        }
        PointList list = tmpPath.calcPoints();
        for (int i = 0; i < list.getSize(); i++)
        {
            double lat = list.getLatitude(i);
            double lon = list.getLongitude(i);
            if (!Double.isNaN(prevLat))
            {
                mg.plotEdge(g2, prevLat, prevLon, lat, lon, w);
            } else
            {
                mg.plot(g2, lat, lon, w);
            }
            prevLat = lat;
            prevLon = lon;
        }
        logger.info("dist:" + tmpPath.getDistance() + ", path points(" + list.getSize() + "):" + list + ", nodes:" + nodes);
        return tmpPath;
    }
    private QueryResult fromRes;
    private QueryResult toRes;

    public void visualize()
    {
        try
        {
            SwingUtilities.invokeAndWait(new Runnable()
            {
                @Override
                public void run()
                {
                    int frameHeight = 800;
                    int frameWidth = 1200;
                    JFrame frame = new JFrame("GraphHopper UI - Small&Ugly ;)");
                    frame.setLayout(new BorderLayout());
                    frame.add(mainPanel, BorderLayout.CENTER);
                    frame.add(infoPanel, BorderLayout.NORTH);

                    infoPanel.setPreferredSize(new Dimension(300, 100));

                    // scale
                    mainPanel.addMouseWheelListener(new MouseWheelListener()
                    {
                        @Override
                        public void mouseWheelMoved( MouseWheelEvent e )
                        {
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
                    MouseAdapter ml = new MouseAdapter()
                    {
                        // for routing:
                        double fromLat, fromLon;
                        boolean fromDone = false;

                        @Override
                        public void mouseClicked( MouseEvent e )
                        {
                            if (!fromDone)
                            {
                                fromLat = mg.getLat(e.getY());
                                fromLon = mg.getLon(e.getX());
                            } else
                            {
                                double toLat = mg.getLat(e.getY());
                                double toLon = mg.getLon(e.getX());
                                StopWatch sw = new StopWatch().start();
                                logger.info("start searching from " + fromLat + "," + fromLon
                                        + " to " + toLat + "," + toLon);
                                // get from and to node id
                                fromRes = index.findClosest(fromLat, fromLon, EdgeFilter.ALL_EDGES);
                                toRes = index.findClosest(toLat, toLon, EdgeFilter.ALL_EDGES);
                                logger.info("found ids " + fromRes + " -> " + toRes + " in " + sw.stop().getSeconds() + "s");

                                repaintPaths();
                            }

                            fromDone = !fromDone;
                        }
                        boolean dragging = false;

                        @Override
                        public void mouseDragged( MouseEvent e )
                        {
                            dragging = true;
                            fastPaint = true;
                            update(e);
                            updateLatLon(e);
                        }

                        @Override
                        public void mouseReleased( MouseEvent e )
                        {
                            if (dragging)
                            {
                                // update only if mouse release comes from dragging! (at the moment equal to fastPaint)
                                dragging = false;
                                fastPaint = false;
                                update(e);
                            }
                        }

                        public void update( MouseEvent e )
                        {
                            mg.setNewOffset(e.getX() - currentPosX, e.getY() - currentPosY);
                            repaintRoads();
                        }

                        @Override
                        public void mouseMoved( MouseEvent e )
                        {
                            updateLatLon(e);
                        }

                        @Override
                        public void mousePressed( MouseEvent e )
                        {
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
        } catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }
    // for moving
    int currentPosX;
    int currentPosY;

    void updateLatLon( MouseEvent e )
    {
        latLon = mg.getLat(e.getY()) + "," + mg.getLon(e.getX());
        infoPanel.repaint();
        currentPosX = e.getX();
        currentPosY = e.getY();
    }

    void repaintPaths()
    {
        pathLayer.repaint();
        mainPanel.repaint();
    }

    void repaintRoads()
    {
        // avoid threading as there should be no updated to scale or offset while painting 
        // (would to lead to artifacts)
        StopWatch sw = new StopWatch().start();
        pathLayer.repaint();
        roadsLayer.repaint();
        mainPanel.repaint();
        logger.info("roads painting took " + sw.stop().getSeconds() + " sec");
    }
}
