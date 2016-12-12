package com.graphhopper.json.geo;

import com.graphhopper.routing.util.spatialrules.Polygon;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by robin on 12.12.16.
 */
public class GeoJsonPolygon implements Geometry {

    private List<Polygon> polygons = new ArrayList<>();


    @Override
    public String getType() {
        return null;
    }

    @Override
    public boolean isPoint() {
        return false;
    }

    @Override
    public GHPoint asPoint() {
        return null;
    }

    @Override
    public boolean isPointList() {
        return false;
    }

    @Override
    public PointList asPointList() {
        return null;
    }

    @Override
    public boolean isPolygon() {
        return true;
    }

    @Override
    public GeoJsonPolygon asPolygon() {
        return this;
    }

    public List<Polygon> getPolygons() {
        return polygons;
    }

    public void addPolygon(Polygon polygon) {
        this.polygons.add(polygon);
    }
}
