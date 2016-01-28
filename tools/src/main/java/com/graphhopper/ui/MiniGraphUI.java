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
import com.graphhopper.routing.*;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import gnu.trove.list.TIntList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.procedure.TIntObjectProcedure;
import java.awt.*;

import java.awt.event.*;
import java.util.List;
import java.util.Random;
import javax.swing.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A rough graphical user interface for visualizing the OSM graph. Mainly for debugging algorithms
 * and spatial datastructures. Use the 'web' module for a more userfriendly UI as shown at
 * graphhopper.com/maps
 * <p>
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
    private RoutingAlgorithmFactory algoFactory;
    private final Graph graph;
    private final NodeAccess na;
    private LocationIndexTree index;
    private String latLon = "";
    private GraphicsWrapper mg;
    private JPanel infoPanel;
    private LayeredPanel mainPanel;
    private MapLayer roadsLayer;
    private final MapLayer pathLayer;
    private boolean fastPaint = false;
    private final Weighting weighting;
    private final FlagEncoder encoder;
    private AlgorithmOptions algoOpts;

    public MiniGraphUI( GraphHopper hopper, boolean debug )
    {
        this.graph = hopper.getGraphHopperStorage();

        encoder = hopper.getEncodingManager().getEncoder("car");
        weighting = hopper.createWeighting(new WeightingMap("fastest"), encoder);
        algoFactory = hopper.getAlgorithmFactory(weighting);
        algoOpts = new AlgorithmOptions(AlgorithmOptions.DIJKSTRA_BI, encoder, weighting);
        this.index = (LocationIndexTree) hopper.getLocationIndex();

        logger.info("locations:" + graph.getNodes() + ", debug:" + debug + ", algoOpts:" + algoOpts);

//        final double fromLat = 49.576995, fromLon = 10.937233;
//        final double toLat = 49.298263, toLon = 11.289139;
        final double fromLat = 51.718521, fromLon = 6.531372;
        final double toLat = 51.138001, toLon = 6.536865;

        QueryResult tmpFromRes = index.findClosest(fromLat, fromLon, EdgeFilter.ALL_EDGES);
        QueryResult tmpToRes = index.findClosest(toLat, toLon, EdgeFilter.ALL_EDGES);
        final QueryGraph qGraph = new QueryGraph(graph);

        this.na = qGraph.getNodeAccess();
        mg = new GraphicsWrapper(qGraph);

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

        qGraph.lookup(tmpFromRes, tmpToRes);

        int max = qGraph.getNodes();
        final GHBitSet bitset = new GHTBitSet(max);

        final int refCountFrom[] = new int[max];
        final int refCountTo[] = new int[max];

//        final MyBiDi bidi = new MyBiDi(qGraph, encoder, weighting, TraversalMode.NODE_BASED);
//        bidi.calcPath(tmpFromRes.getClosestNode(), tmpToRes.getClosestNode());
//        while (bidi.fillEdgesFrom())
//        {
////            if(bidi.getCurrentFromWeight() > 1000)
////                break;
//        }
        final AlternativeRoute.AlternativeBidirSearch altRoute = new AlternativeRoute.AlternativeBidirSearch(
                qGraph, encoder, weighting, TraversalMode.NODE_BASED, 2);
        List<Path> alts = altRoute.calcPaths(tmpFromRes.getClosestNode(), tmpToRes.getClosestNode());

        logger.info("alts:" + alts.size() 
                + ", from:" + altRoute.getBestWeightMapFrom().size() + ", to:" + altRoute.getBestWeightMapTo().size() + ", visited:" + altRoute.getVisitedNodes());
        for (int nodeIdx = 0; nodeIdx < max; nodeIdx++)
        {
            SPTEntry ee = altRoute.getBestWeightMapFrom().get(nodeIdx);
            if (ee != null)
                while (ee.parent != null)
                {
                    refCountFrom[ee.parent.adjNode]++;
                    ee = ee.parent;
                }

            ee = altRoute.getBestWeightMapTo().get(nodeIdx);
            if (ee != null)
                while (ee.parent != null)
                {
                    refCountTo[ee.parent.adjNode]++;
                    ee = ee.parent;
                }
        }

        final Color[] fromColors = new Color[]
        {
            new Color(0f, 0f, 1f, 0.3f)
        };
        final Color[] toColors = new Color[]
        {
            new Color(1f, 0.5f, 0f, 0.3f)
        };

        mainPanel.addLayer(roadsLayer = new DefaultMapLayer()
        {
            Random rand = new Random();

            @Override
            public void paintComponent( final Graphics2D g2 )
            {
                clearGraphics(g2);
                Rectangle d = getBounds();
                final BBox bbox = mg.setBounds(0, d.width, 0, d.height);
                if (fastPaint)
                {
                    rand.setSeed(0);
                    bitset.clear();
                }

                g2.setColor(Color.black);

                final EdgeExplorer explorer = qGraph.createEdgeExplorer(EdgeFilter.ALL_EDGES);

                altRoute.getBestWeightMapFrom().forEachEntry(new TIntObjectProcedure<SPTEntry>()
                {
                    @Override
                    public boolean execute( int tid, SPTEntry fromEE )
                    {
                        if (fromEE.edge >= 0)
                            plot(qGraph.getEdgeIteratorState(fromEE.edge, fromEE.adjNode),
                                    fromEE.weight, g2, bbox, explorer,
                                    fromColors, refCountFrom[tid]);

                        return true;
                    }
                });

                altRoute.getBestWeightMapTo().forEachEntry(new TIntObjectProcedure<SPTEntry>()
                {
                    @Override
                    public boolean execute( int tid, SPTEntry toEE )
                    {
                        if (toEE.edge >= 0)
                            plot(qGraph.getEdgeIteratorState(toEE.edge, toEE.adjNode),
                                    toEE.weight, g2, bbox, explorer,
                                    toColors, refCountTo[tid]);

                        return true;
                    }
                });

                g2.setColor(Color.RED);
                mg.plotText(g2, fromLat, fromLon, "from");
                mg.plotText(g2, toLat, toLon, "to");

//                g2.setColor(Color.WHITE);
//                g2.fillRect(0, 0, 1000, 20);
//                for (int i = 4; i < speedColors.length; i++)
//                {
//                    g2.setColor(speedColors[i]);
//                    g2.drawString("" + (i * 10), i * 30 - 100, 10);
//                }
                g2.setColor(Color.BLACK);
            }

            void plot( EdgeIteratorState fromEdge,
                       double fromSPTWeight,
                       Graphics2D g2, BBox bbox,
                       EdgeExplorer explorer, Color[] speedColors, int count )
            {
                if (count < 5)
                    return;

                if (fastPaint && rand.nextInt(30) > 1)
                    return;

                int baseNode = fromEdge.getBaseNode();
                double lat = na.getLatitude(baseNode);
                double lon = na.getLongitude(baseNode);

                if (lat < bbox.minLat || lat > bbox.maxLat || lon < bbox.minLon || lon > bbox.maxLon)
                    return;

                int sum = fromEdge.getEdge();
                if (fastPaint)
                {
                    if (bitset.contains(sum))
                        return;

                    bitset.add(sum);
                }

                int adjNode = fromEdge.getAdjNode();
                double lat2 = na.getLatitude(adjNode);
                double lon2 = na.getLongitude(adjNode);

//                double weight = fromSPTWeight; // encoder.getSpeed(edge.getFlags());
//                Color color;
//                if (weight >= 1500)
//                {
//                    // red
//                    color = speedColors[12];
//                } else if (weight >= 1300)
//                {
//                    color = speedColors[10];
//                } else if (weight >= 1100)
//                {
//                    color = speedColors[8];
//                } else if (weight >= 900)
//                {
//                    color = speedColors[6];
//                } else if (weight >= 700)
//                {
//                    color = speedColors[5];
//                } else if (weight >= 500)
//                {
//                    color = speedColors[4];
//                } else if (weight >= 300)
//                {
//                    color = Color.GRAY;
//                } else
//                {
//                    color = Color.LIGHT_GRAY;
//                }
                g2.setColor(speedColors[0]);

                // mg.plotEdge(g2, lat, lon, lat2, lon2, 1.3f);
                mg.plotEdge(g2, lat, lon, lat2, lon2, (float) Math.max(Math.log(count) * 0.8, 1));
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
                QueryGraph qGraph = new QueryGraph(graph).lookup(fromRes, toRes);
                RoutingAlgorithm algo = algoFactory.createAlgo(qGraph, algoOpts);
                if (algo instanceof DebugAlgo)
                {
                    ((DebugAlgo) algo).setGraphics2D(g2);
                }

                StopWatch sw = new StopWatch().start();
                logger.info("start searching from:" + fromRes + " to:" + toRes + " " + weighting);

                path = algo.calcPath(fromRes.getClosestNode(), toRes.getClosestNode());
                sw.stop();

                // if directed edges
                if (!path.isFound())
                {
                    logger.warn("path not found! direction not valid?");
                    return;
                }

                logger.info("found path in " + sw.getSeconds() + "s with nodes:"
                        + path.calcNodes().size() + ", millis: " + path.getTime() + ", " + path);
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

    public Color[] generateColors( int n )
    {
        Color[] cols = new Color[n];
        for (int i = 0; i < n; i++)
        {
            cols[i] = Color.getHSBColor((float) i / (float) n, 0.85f, 1.0f);
        }
        return cols;
    }

    void plotNodeName( Graphics2D g2, int node )
    {
        double lat = na.getLatitude(node);
        double lon = na.getLongitude(node);
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
        boolean plotNodes = false;
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

    static class MyBiDi extends DijkstraBidirectionRef
    {
        public MyBiDi( Graph graph, FlagEncoder encoder, Weighting weighting, TraversalMode tMode )
        {
            super(graph, encoder, weighting, tMode);
        }

        public TIntObjectMap<SPTEntry> getBestWeightMapFrom()
        {
            return bestWeightMapFrom;
        }

        public TIntObjectMap<SPTEntry> getBestWeightMapTo()
        {
            return bestWeightMapTo;
        }

        @Override
        protected double getCurrentFromWeight()
        {
            return super.getCurrentFromWeight();
        }

        @Override
        protected double getCurrentToWeight()
        {
            return super.getCurrentToWeight();
        }
    }
}
