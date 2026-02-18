package com.graphhopper.util;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import javax.swing.*;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.shapes.BBox;

public class BaseGraphVisualizer {

    private BaseGraphVisualizer() {
    }

    public static void show(BaseGraph graph) {
        show(graph, null);
    }

    public static void show(BaseGraph graph, DecimalEncodedValue speedEnc) {
        if (graph.getNodes() > 1000 || graph.getEdges() > 1000)
            throw new IllegalArgumentException("BaseGraphVisualizer only supports up to 1000 nodes/edges, "
                    + "but graph has " + graph.getNodes() + " nodes and " + graph.getEdges() + " edges");
        var latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            var frame = new JFrame("BaseGraph");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    latch.countDown();
                }
            });
            frame.add(new GraphPanel(graph, speedEnc));
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class GraphPanel extends JPanel {
        private static final Locale L = Locale.US;
        private static final int PAD = 40;
        private static final int W = 800, H = 800;
        private static final DistanceCalc DIST = new DistanceCalcEuclidean();
        private static final Font LABEL_FONT = new Font("SansSerif", Font.PLAIN, 10);
        private static final Font STATS_FONT = new Font("SansSerif", Font.PLAIN, 12);

        private final BaseGraph graph;
        private final NodeAccess na;
        private final DecimalEncodedValue speedEnc;
        private final BBox bounds;
        private final String statsText;
        private int hoveredNode = -1;
        private int hoveredEdge = -1;
        private int hoveredAdjNode = -1;
        private int mouseX, mouseY;
        private final Set<Integer> clickedEdges = new HashSet<>();
        private double sumDist, sumFwdTime;

        GraphPanel(BaseGraph graph, DecimalEncodedValue speedEnc) {
            this.graph = graph;
            this.na = graph.getNodeAccess();
            this.speedEnc = speedEnc;
            setPreferredSize(new Dimension(W, H));
            setBackground(new Color(20, 20, 30));
            this.bounds = computeBounds();
            this.statsText = computeStats();
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    mouseX = e.getX();
                    mouseY = e.getY();
                    updateHover(mouseX, mouseY);
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.isShiftDown() && SwingUtilities.isLeftMouseButton(e) && hoveredEdge >= 0) {
                        if (clickedEdges.add(hoveredEdge)) {
                            EdgeIteratorState edge = graph.getEdgeIteratorState(hoveredEdge, hoveredAdjNode);
                            sumDist += edge.getDistance();
                            if (speedEnc != null) {
                                double speed = edge.get(speedEnc);
                                if (speed > 0) sumFwdTime += edge.getDistance() / speed;
                            }
                            repaint();
                        }
                    }
                    if (SwingUtilities.isRightMouseButton(e)) {
                        clickedEdges.clear();
                        sumDist = 0;
                        sumFwdTime = 0;
                        repaint();
                    }
                }
            });
        }

        private String computeStats() {
            String stats = graph.getNodes() + " nodes \u00b7 " + graph.getEdges() + " edges";
            if (graph.getEdges() > 0) {
                double minDist = Double.MAX_VALUE, maxDist = 0;
                double minSpeed = Double.MAX_VALUE, maxSpeed = 0;
                double minTime = Double.MAX_VALUE, maxTime = 0;
                AllEdgesIterator iter = graph.getAllEdges();
                while (iter.next()) {
                    double d = iter.getDistance();
                    minDist = Math.min(minDist, d);
                    maxDist = Math.max(maxDist, d);
                    if (speedEnc != null) {
                        double speedFwd = iter.get(speedEnc);
                        double speedBwd = iter.getReverse(speedEnc);
                        minSpeed = Math.min(minSpeed, Math.min(speedFwd, speedBwd));
                        maxSpeed = Math.max(maxSpeed, Math.max(speedFwd, speedBwd));
                        if (speedFwd > 0) {
                            double t = d / speedFwd;
                            minTime = Math.min(minTime, t);
                            maxTime = Math.max(maxTime, t);
                        }
                        if (speedBwd > 0) {
                            double t = d / speedBwd;
                            minTime = Math.min(minTime, t);
                            maxTime = Math.max(maxTime, t);
                        }
                    }
                }
                stats += String.format(L, " \u00b7 dist: %.0f..%.0fm", minDist, maxDist);
                if (speedEnc != null) {
                    stats += String.format(L, " \u00b7 speed: %.0f..%.0fm/s \u00b7 time: %.1f..%.1fs", minSpeed, maxSpeed, minTime, maxTime);
                }
            }
            return stats;
        }

        private BBox computeBounds() {
            BBox bounds = BBox.createInverse(false);
            for (int i = 0; i < graph.getNodes(); i++)
                bounds.update(na.getLat(i), na.getLon(i));
            double padLat = (bounds.maxLat - bounds.minLat) * 0.1;
            double padLon = (bounds.maxLon - bounds.minLon) * 0.1;
            if (padLat < 1e-9) padLat = 0.001;
            if (padLon < 1e-9) padLon = 0.001;
            bounds.minLat -= padLat;
            bounds.maxLat += padLat;
            bounds.minLon -= padLon;
            bounds.maxLon += padLon;
            return bounds;
        }

        private int sx(double lon) {
            return PAD + (int) ((lon - bounds.minLon) / (bounds.maxLon - bounds.minLon) * (getWidth() - 2 * PAD));
        }

        private int sy(double lat) {
            return getHeight() - PAD - (int) ((lat - bounds.minLat) / (bounds.maxLat - bounds.minLat) * (getHeight() - 2 * PAD));
        }

        private void updateHover(int mx, int my) {
            int prevNode = hoveredNode, prevEdge = hoveredEdge;
            hoveredNode = -1;
            hoveredEdge = -1;
            hoveredAdjNode = -1;

            // check nodes first (priority over edges)
            double bestD = 64;
            for (int i = 0; i < graph.getNodes(); i++) {
                double dx = sx(na.getLon(i)) - mx, dy = sy(na.getLat(i)) - my;
                double d = dx * dx + dy * dy;
                if (d < bestD) {
                    bestD = d;
                    hoveredNode = i;
                }
            }

            // check edges only if no node is hovered
            if (hoveredNode == -1) {
                double bestED = 36;
                AllEdgesIterator iter = graph.getAllEdges();
                while (iter.next()) {
                    int base = iter.getBaseNode(), adj = iter.getAdjNode();
                    double ax = sx(na.getLon(base)), ay = sy(na.getLat(base));
                    double bx = sx(na.getLon(adj)), by = sy(na.getLat(adj));
                    if (DIST.validEdgeDistance(my, mx, ay, ax, by, bx)) {
                        double d = DIST.calcNormalizedEdgeDistance(my, mx, ay, ax, by, bx);
                        if (d < bestED) {
                            bestED = d;
                            hoveredEdge = iter.getEdge();
                            hoveredAdjNode = adj;
                        }
                    }
                }
            }
            if (hoveredNode != prevNode || hoveredEdge != prevEdge) repaint();
        }

        private void drawTooltip(Graphics2D g, int x, int y, java.util.List<String> lines) {
            if (lines.isEmpty()) return;
            FontMetrics fm = g.getFontMetrics();
            int lineH = fm.getHeight();
            int pad = 4;
            int maxW = 0;
            for (String line : lines) maxW = Math.max(maxW, fm.stringWidth(line));
            int boxW = maxW + pad * 2;
            int boxH = lineH * lines.size() + pad * 2;
            int bx = x - pad, by = y - fm.getAscent() - pad;
            g.setColor(new Color(20, 20, 30, 255));
            g.fillRoundRect(bx, by, boxW, boxH, 6, 6);
            g.setColor(Color.WHITE);
            for (int i = 0; i < lines.size(); i++) {
                g.drawString(lines.get(i), x, y + i * lineH);
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            var g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // draw edges (duplicate edges between the same node pair get a thicker stroke)
            var seenPairs = new com.carrotsearch.hppc.LongHashSet();
            AllEdgesIterator iter = graph.getAllEdges();
            while (iter.next()) {
                int base = iter.getBaseNode(), adj = iter.getAdjNode();
                long key = (long) Math.min(base, adj) << 32 | Math.max(base, adj);
                boolean isDuplicate = !seenPairs.add(key);
                boolean isClicked = clickedEdges.contains(iter.getEdge());
                boolean isHov = iter.getEdge() == hoveredEdge;
                if (isClicked) g.setColor(new Color(34, 197, 94));
                else if (isHov) g.setColor(Color.WHITE);
                else g.setColor(new Color(59, 130, 246, 130));
                float width = isHov || isClicked ? 2 : isDuplicate ? 4 : 1;
                g.setStroke(new BasicStroke(width));
                g.drawLine(sx(na.getLon(base)), sy(na.getLat(base)), sx(na.getLon(adj)), sy(na.getLat(adj)));
            }

            // draw nodes
            g.setStroke(new BasicStroke(1));
            g.setFont(LABEL_FONT);
            for (int i = 0; i < graph.getNodes(); i++) {
                boolean isHov = i == hoveredNode;
                int r = isHov ? 6 : 3;
                int px = sx(na.getLon(i)), py = sy(na.getLat(i));
                g.setColor(isHov ? Color.WHITE : new Color(229, 229, 229));
                g.fillOval(px - r, py - r, r * 2, r * 2);
                g.setColor(new Color(160, 160, 180));
                g.drawString(String.valueOf(i), px + 8, py - 6);
            }

            // stats
            g.setFont(STATS_FONT);
            g.setColor(new Color(160, 160, 180));
            g.drawString(statsText, 10, getHeight() - 10);

            // hover info (next to cursor)
            g.setFont(LABEL_FONT);
            int tx = mouseX + 15, ty = mouseY - 10;
            if (hoveredEdge >= 0) {
                EdgeIteratorState e = graph.getEdgeIteratorState(hoveredEdge, hoveredAdjNode);
                var lines = new ArrayList<String>();
                lines.add(String.format(L, "Edge %d: %d -> %d", e.getEdge(), e.getBaseNode(), e.getAdjNode()));
                lines.add(String.format(L, "Distance: %.0f m", e.getDistance()));
                if (speedEnc != null) {
                    double fwdSpeed = e.get(speedEnc);
                    double bwdSpeed = e.getReverse(speedEnc);
                    lines.add(String.format(L, "Speed-Fwd: %.1f m/s (%.0f km/h)", fwdSpeed, fwdSpeed * 3.6));
                    lines.add(String.format(L, "Speed-Bwd: %.1f m/s (%.0f km/h)", bwdSpeed, bwdSpeed * 3.6));
                    double fwdTime = fwdSpeed > 0 ? e.getDistance() / fwdSpeed : 0;
                    double bwdTime = bwdSpeed > 0 ? e.getDistance() / bwdSpeed : 0;
                    lines.add(String.format(L, "Time-Fwd: %.2f s", fwdTime));
                    lines.add(String.format(L, "Time-Bwd: %.2f s", bwdTime));
                }
                drawTooltip(g, tx, ty, lines);
            }
            if (hoveredNode >= 0) {
                var lines = new ArrayList<String>();
                lines.add(String.format(L, "Node %d", hoveredNode));
                lines.add(String.format(L, "Lat: %.6f", na.getLat(hoveredNode)));
                lines.add(String.format(L, "Lon: %.6f", na.getLon(hoveredNode)));
                drawTooltip(g, tx, ty, lines);
            }

            // sum of clicked edges (top-right)
            if (!clickedEdges.isEmpty()) {
                g.setFont(LABEL_FONT);
                g.setColor(new Color(34, 197, 94));
                String sumInfo = String.format(L, "Sum (%d edges): Distance=%.0fm", clickedEdges.size(), sumDist);
                if (speedEnc != null) {
                    sumInfo += String.format(L, ", Time-Fwd=%.2fs", sumFwdTime);
                }
                FontMetrics fm = g.getFontMetrics();
                g.drawString(sumInfo, getWidth() - fm.stringWidth(sumInfo) - 10, 20);
            }

            // instructions (bottom-right)
            g.setFont(LABEL_FONT);
            g.setColor(new Color(100, 100, 120));
            String instructions = "shift+click: sum edges | right-click: reset";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(instructions, getWidth() - fm.stringWidth(instructions) - 10, getHeight() - 10);
        }
    }
}
