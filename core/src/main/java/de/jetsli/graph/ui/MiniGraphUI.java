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

import de.jetsli.graph.dijkstra.DijkstraBidirection;
import de.jetsli.graph.dijkstra.DijkstraPath;
import de.jetsli.graph.reader.OSMReaderTrials;
import de.jetsli.graph.storage.DistEntry;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.storage.Location2IDQuadtree;
import de.jetsli.graph.storage.ID2LocationIndex;
import de.jetsli.graph.trees.QuadTree;
import de.jetsli.graph.trees.QuadTreeSimple;
import de.jetsli.graph.util.CoordTrig;
import de.jetsli.graph.util.MyIteratorable;
import de.jetsli.graph.util.StopWatch;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.util.Collection;
import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich
 */
public class MiniGraphUI {

    public static void main(String[] args) throws Exception {
        if (args.length < 1)
            throw new IllegalArgumentException("Osm file missing");

        String osmFile = args[0];
        Graph g = OSMReaderTrials.defaultRead(osmFile, "/tmp/mmap-graph");
        new MiniGraphUI(g).visualize();
    }
    private Logger logger = LoggerFactory.getLogger(getClass());
    private final QuadTree<Long> quadTree;
    private Collection<CoordTrig<Long>> quadTreeNodes;
    private DijkstraPath path;
    private final Graph graph;
    private final ID2LocationIndex index;
    private double scaleX = 0.001f;
    private double scaleY = 0.001f;
    // initial position to center unterfranken
    // 49.50381,9.953613 -> south unterfranken
    private double offsetX = -8.8f;
    private double offsetY = -39.7f;
    private String latLon = "";
    private double minX;
    private double minY;
    private double maxX;
    private double maxY;
    private JPanel infoPanel;
    private JPanel mainPanel;

    public MiniGraphUI(Graph g) {
        this.graph = g;
        this.index = new Location2IDQuadtree(g).prepareIndex(2000);
        this.quadTree = new QuadTreeSimple<Long>(8, 7 * 8);
//        this.quadTree = new SpatialHashtable(2, 3).init(graph.getLocations());

        QuadTree.Util.fill(quadTree, graph);
        logger.info("read " + quadTree.size() + " entries");

        infoPanel = new JPanel() {

            @Override protected void paintComponent(Graphics g) {
                g.clearRect(0, 0, 10000, 10000);

                g.setColor(Color.BLUE);
                g.drawString(latLon, 40, 20);
                g.drawString("scale:" + scaleX, 40, 40);
                g.drawString("minX:" + (int) minX + " minY:" + (int) minY
                        + " maxX:" + (int) maxX + " maxY:" + (int) maxY, 40, 60);
            }
        };

        // TODO PERFORMANCE draw graph on an offscreen image and translate + scale that one!
        // but then we have a memory problem and less resolution!
        // final BufferedImage offscreenImage = new BufferedImage(11000, 11000, BufferedImage.TYPE_INT_ARGB);
        // final Graphics2D g2 = offscreenImage.createGraphics();

        mainPanel = new JPanel() {

            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.clearRect(0, 0, 10000, 10000);

//                AffineTransform at = AffineTransform.getScaleInstance(scaleX, scaleY);
//                at.concatenate(AffineTransform.getTranslateInstance(offsetX, offsetY));
//                AffineTransformOp op = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
//                g2.drawImage(offscreenImage, op, 0, 0);

                int locs = graph.getLocations();
                minX = Integer.MAX_VALUE;
                minY = Integer.MAX_VALUE;
                maxX = 0;
                maxY = 0;
                g.setColor(Color.RED);
                g.drawOval((int) MiniGraphUI.this.getX(49.990532), (int) MiniGraphUI.this.getY(9.020827), 10, 10);

                int size;
                if (scaleX < 3e-5)
                    size = 2;
                else if (scaleX < 3e-4)
                    size = 1;
                else
                    size = 0;
                StopWatch sw = new StopWatch().start();
                for (int i = 0; i < locs; i++) {
                    int count = MyIteratorable.count(graph.getEdges(i));
                    float lat = graph.getLatitude(i);
                    float lon = graph.getLongitude(i);
                    plot(g2, lat, lon, count, size);

                    for (DistEntry de : graph.getOutgoing(i)) {
                        float lat2 = graph.getLatitude(de.node);
                        float lon2 = graph.getLongitude(de.node);
                        if (lat2 <= 0 || lon2 <= 0)
                            logger.info("ERROR " + de.node + " " + de.distance + " " + lat2 + "," + lon2);
                        plotEdge(g, lat, lon, lat2, lon2);
                    }
                }
                logger.info("frame took " + sw.stop().getSeconds() + "sec");

                if (quadTreeNodes != null) {
                    logger.info("found neighbors:" + quadTreeNodes.size());
                    for (CoordTrig<Long> coord : quadTreeNodes) {
                        plot(g, coord.lat, coord.lon, 1, 1);
                    }
                }

                if (path != null) {
                    logger.info("found path:" + path);
                    g.setColor(Color.MAGENTA);
                    int tmpLocs = path.locations();
                    float prevLat = -1;
                    float prevLon = -1;
                    for (int i = 0; i < tmpLocs; i++) {
                        int id = path.location(i);
                        float lat = graph.getLatitude(id);
                        float lon = graph.getLongitude(id);
                        if (prevLat >= 0)
                            plotEdge(g, prevLat, prevLon, lat, lon, 3);

                        prevLat = lat;
                        prevLon = lon;
                    }
                }

                infoPanel.repaint();
            }
        };
    }

    private void plotEdge(Graphics g, double lat, double lon, double lat2, double lon2, int width) {
        ((Graphics2D) g).setStroke(new BasicStroke(width));
        g.drawLine((int) getX(lon), (int) getY(lat), (int) getX(lon2), (int) getY(lat2));
    }

    private void plotEdge(Graphics g, double lat, double lon, double lat2, double lon2) {
        plotEdge(g, lat, lon, lat2, lon2, 1);
    }

    private double getX(double lon) {
        return (lon + offsetX) / scaleX;
    }

    private double getY(double lat) {
        return (90 - lat + offsetY) / scaleY;
    }

    private void plot(Graphics g, double lat, double lon, int count, int width) {
        double x = getX(lon);
        double y = getY(lat);
        if (y < minY)
            minY = y;
        else if (y > maxY)
            maxY = y;
        if (x < minX)
            minX = x;
        else if (x > maxX)
            maxX = x;

        Color color;

        // logger.info(i + " y:" + y + " lat:" + lat + "," + lon + " count:" + count);
        if (count == 1)
            color = Color.RED;
        else if (count == 2)
            color = Color.BLACK;
        else if (count == 3)
            color = Color.BLUE;
        else if (count == 4)
            color = Color.GREEN;
        else if (count == 5)
            color = Color.MAGENTA;
        else
            color = new Color(Math.min(250, count * 10), 111, 111);

        g.setColor(color);

        if (count > 0)
            g.drawOval((int) x, (int) y, width, width);
    }

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
                            double tmpFactor = 0.5f;
                            if (e.getWheelRotation() > 0)
                                tmpFactor = 2;

                            double resX = scaleX * tmpFactor;
                            if (resX > 0)
                                scaleX = resX;

                            double resY = scaleY * tmpFactor;
                            if (resY > 0)
                                scaleY = resY;

                            // TODO respect mouse x,y when scaling
                            offsetX -= offsetX * scaleX;
                            offsetY -= offsetY * scaleY;
                            mainPanel.repaint();
                        }
                    });

                    MouseAdapter ml = new MouseAdapter() {

                        double fromLat, fromLon;
                        boolean fromDone = false;

                        @Override public void mouseClicked(MouseEvent e) {
                            if (!fromDone) {
                                fromLat = getLat(e.getY());
                                fromLon = getLon(e.getX());
                            } else {
                                double toLat = getLat(e.getY());
                                double toLon = getLon(e.getX());
                                StopWatch sw = new StopWatch().start();
                                logger.info("start searching from " + fromLat + "," + fromLon
                                        + " to " + toLat + "," + toLon);
                                // get from and to node id
                                int from = index.findID((float) fromLat, (float) fromLon);
                                int to = index.findID((float) toLat, (float) toLon);
                                logger.info("found ids " + from + " -> " + to + " in " + sw.stop().getSeconds() + "s");
                                sw = new StopWatch().start();
                                path = new DijkstraBidirection(graph).calcShortestPath(from, to);
                                logger.info("found path in " + sw.stop().getSeconds() + "s " + path);
                                mainPanel.repaint();
                            }

                            fromDone = !fromDone;
                        }
                    };
                    // important: calculate x/y for mouse event relative to mainPanel not frame!
                    // move graph via dragging and do something on click                    
                    MouseAdapter mouseAdapterQuadTree = new MouseAdapter() {

                        int currentPosX;
                        int currentPosY;

                        @Override public void mouseClicked(MouseEvent e) {
                            updateLatLon(e);

                            // copy to clipboard
                            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                            StringSelection stringSelection = new StringSelection(latLon);
                            clipboard.setContents(stringSelection, new ClipboardOwner() {

                                @Override public void lostOwnership(Clipboard clipboard, Transferable contents) {
                                }
                            });

                            // show quad tree nodes
                            double lat = getLat(e.getY());
                            double lon = getLon(e.getX());

                            StopWatch sw = new StopWatch().start();
                            quadTreeNodes = quadTree.getNodes(lat, lon, 10);
                            logger.info("search at " + lat + "," + lon + " took " + sw.stop().getSeconds());

                            // open browser
//                            try {
//                                Desktop.getDesktop().browse(new URI("http://maps.google.de/maps?q=" + latLon));
//                            } catch (Exception ex) {
//                                ex.printStackTrace();
//                            }
                            mainPanel.repaint();
                        }

                        @Override public void mouseDragged(MouseEvent e) {
                            update(e);
                        }

                        @Override public void mouseMoved(MouseEvent e) {
                            updateLatLon(e);
                            infoPanel.repaint();
                        }

                        private void updateLatLon(MouseEvent e) {
                            latLon = getLat(e.getY()) + "," + getLon(e.getX());
                        }

                        @Override public void mousePressed(MouseEvent e) {
                            currentPosX = e.getX();
                            currentPosY = e.getY();
                        }

                        public void update(MouseEvent e) {
                            offsetX += (e.getX() - currentPosX) * scaleX;
                            offsetY += (e.getY() - currentPosY) * scaleY;
                            mainPanel.repaint();
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

    double getLon(int x) {
        return x * scaleX - offsetX;
    }

    double getLat(int y) {
        return 90 - (y * scaleY - offsetY);
    }
}
