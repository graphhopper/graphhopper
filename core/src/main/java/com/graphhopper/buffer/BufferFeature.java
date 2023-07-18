package com.graphhopper.buffer;

import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint3D;

public class BufferFeature {
    private Integer edge;
    private Integer node;
    private GHPoint3D point;
    private Double distance;
    private PointList path;

    public BufferFeature(Integer edge, GHPoint3D point, Double distance) {
        this.edge = edge;
        this.point = point;
        this.distance = distance;
        this.path = new PointList();
    }

    public BufferFeature(Integer edge, GHPoint3D point, Double distance, PointList path) {
        this.edge = edge;
        this.point = point;
        this.distance = distance;
        this.path = path;
    }

    public BufferFeature(Integer edge, Integer node, GHPoint3D point, Double distance, PointList path) {
        this.edge = edge;
        this.node = node;
        this.point = point;
        this.distance = distance;
        this.path = path;
    }

    public BufferFeature(Integer edge, GHPoint3D point) {
        this.edge = edge;
        this.point = point;
    }

    public BufferFeature(Integer edge, Double distance) {
        this.edge = edge;
        this.distance = distance;
    }

    public Integer getEdge() {
        return this.edge;
    }

    public Integer getNode() {
        return this.node;
    }

    public GHPoint3D getPoint() {
        return this.point;
    }

    public Double getDistance() {
        return this.distance;
    }

    public PointList getPath() {
        return this.path;
    }
}
