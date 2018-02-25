package com.graphhopper.ui.ch;

import com.graphhopper.routing.util.*;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;
import com.graphhopper.ui.GraphicsWrapper;
import com.graphhopper.ui.LayeredPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class CHMiniGraphUI {
    private static final Logger LOGGER = LoggerFactory.getLogger(CHMiniGraphUI.class);

    // ui elements
    private final LayeredPanel mainPanel;
    private final JPanel infoPanel;
    private final RoadLayer roadLayer;
    private final GraphicsWrapper mg;

    // ui state
    private int currentPosX;
    private int currentPosY;
    private String latLon = "";

    public static void main(String[] args) {
        new CHMiniGraphUI().run();
    }

    public CHMiniGraphUI() {
        FlagEncoder encoder = new CarFlagEncoder();
        EncodingManager encodingManager = new EncodingManager(encoder);
        Weighting weighting = new FastestWeighting(encoder);
        GraphHopperStorage graph = new GraphBuilder(encodingManager).setCHGraph(weighting).setEdgeBasedCH(true).create();

        // setup graph here
        graph.getNodeAccess().setNode(0, 50.000, 0.000);
        graph.getNodeAccess().setNode(1, 50.001, 0.001);
        graph.getNodeAccess().setNode(2, 50.002, 0.002);
        graph.getNodeAccess().setNode(3, 50.003, 0.003);
        graph.getNodeAccess().setNode(4, 50.004, 0.004);

        graph.edge(0, 1, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(1, 2, 1, false);
        graph.edge(2, 3, 1, false);
        graph.edge(3, 4, 1, false);

        this.mg = new GraphicsWrapper(graph);
        mainPanel = new LayeredPanel();
        infoPanel = new InfoPanel();
        roadLayer = new RoadLayer(graph, encoder, mg);
        mainPanel.addLayer(roadLayer);
    }

    private void run() {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    int frameHeight = 800;
                    int frameWidth = 1200;
                    JFrame frame = new JFrame("GraphHopper Contraction Hierarchies UI");
                    frame.setLayout(new BorderLayout());
                    frame.add(mainPanel, BorderLayout.CENTER);
                    frame.add(infoPanel, BorderLayout.NORTH);

                    infoPanel.setPreferredSize(new Dimension(300, 100));

                    mainPanel.addMouseWheelListener(new MyMouseWheeListener());
                    MyMouseListener ml = new MyMouseListener();
                    mainPanel.addMouseListener(ml);
                    mainPanel.addMouseMotionListener(ml);

                    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                    frame.setSize(frameWidth + 10, frameHeight + 30);
                    frame.setVisible(true);
                }
            });
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void repaint() {
        roadLayer.repaint();
        mainPanel.repaint();
    }

    private class MyMouseWheeListener implements MouseWheelListener {
        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            mg.scale(e.getX(), e.getY(), e.getWheelRotation() < 0);
            repaint();
        }
    }

    private class MyMouseListener extends MouseAdapter {
        boolean dragging = false;

        @Override
        public void mouseMoved(MouseEvent e) {
            updateLatLon(e);
        }

        @Override
        public void mousePressed(MouseEvent e) {
            updateLatLon(e);
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            dragging = true;
            update(e);
            updateLatLon(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (dragging) {
                dragging = false;
                update(e);
            }
        }

        void update(MouseEvent e) {
            mg.setNewOffset(e.getX() - currentPosX, e.getY() - currentPosY);
            repaint();
        }

        private void updateLatLon(MouseEvent e) {
            latLon = mg.getLat(e.getY()) + "," + mg.getLon(e.getX());
            infoPanel.repaint();
            currentPosX = e.getX();
            currentPosY = e.getY();
        }
    }

    private class InfoPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(Color.WHITE);
            Rectangle b = infoPanel.getBounds();
            g.fillRect(0, 0, b.width, b.height);

            g.setColor(Color.BLUE);
            g.drawString("mouse: " + latLon, 40, 20);
            g.drawString("scale: " + mg.getScaleX(), 40, 40);
            int w = mainPanel.getBounds().width;
            int h = mainPanel.getBounds().height;
            g.drawString("bounds: " + mg.setBounds(0, w, 0, h).toLessPrecisionString(), 40, 60);
        }
    }
}
