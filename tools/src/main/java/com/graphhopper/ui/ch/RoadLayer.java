package com.graphhopper.ui.ch;

import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.ui.DefaultMapLayer;
import com.graphhopper.ui.GraphicsWrapper;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.BBox;

import java.awt.*;

public class RoadLayer extends DefaultMapLayer {
    private final Graph graph;
    private final FlagEncoder encoder;
    private final NodeAccess na;
    private final GraphicsWrapper mg;

    public RoadLayer(Graph graph, FlagEncoder encoder, GraphicsWrapper mg) {
        this.graph = graph;
        this.encoder = encoder;
        this.na = graph.getNodeAccess();
        this.mg = mg;
    }

    @Override
    public void paintComponent(Graphics2D g2) {
        clearGraphics(g2);
        Rectangle d = getBounds();
        BBox bbox = mg.setBounds(0, d.width, 0, d.height);

        g2.setColor(Color.black);

        for (int node = 0; node < graph.getNodes(); ++node) {
            mg.plotNode(g2, node, Color.RED, 10, String.valueOf(node));

        }

        AllEdgesIterator edge = graph.getAllEdges();
        while (edge.next()) {
            int nodeIndex = edge.getBaseNode();
            double lat = na.getLatitude(nodeIndex);
            double lon = na.getLongitude(nodeIndex);
            int nodeId = edge.getAdjNode();
            double lat2 = na.getLatitude(nodeId);
            double lon2 = na.getLongitude(nodeId);

            if (!bbox.contains(lat, lon) && !bbox.contains(lat2, lon2))
                continue;

            drawEdge(g2, edge, lat, lon, lat2, lon2);
        }

        g2.setColor(Color.BLACK);
    }

    protected void drawEdge(Graphics2D g2, EdgeIteratorState edge, double latFrom, double lonFrom, double latTo, double lonTo) {
        mg.plotText(g2, latFrom * 0.5 + latTo * 0.5, lonFrom * 0.5 + lonTo * 0.5, String.valueOf(edge.getEdge()));
        g2.setColor(Color.BLUE);
        boolean fwd = encoder.isForward(edge.getFlags());
        boolean bwd = encoder.isBackward(edge.getFlags());
        float width = encoder.getSpeed(edge.getFlags()) > 90 ? 1f : 0.8f;
        if (fwd && !bwd) {
            mg.plotDirectedEdge(g2, latFrom, lonFrom, latTo, lonTo, width);
        } else {
            mg.plotEdge(g2, latFrom, lonFrom, latTo, lonTo, width);
        }
    }

}
