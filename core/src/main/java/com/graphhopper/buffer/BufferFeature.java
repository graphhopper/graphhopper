package com.graphhopper.buffer;

import com.graphhopper.util.shapes.GHPoint3D;

public class BufferFeature {
    private Integer edge;
    private GHPoint3D point;
    private Double distance;

    public BufferFeature(Integer edge, GHPoint3D point, Double distance) {
        this.edge = edge;
        this.point = point;
        this.distance = distance;
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

    public GHPoint3D getPoint() {
        return this.point;
    }

    public Double getDistance() {
        return this.distance;
    }
}
