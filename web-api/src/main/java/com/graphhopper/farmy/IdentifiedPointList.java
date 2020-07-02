package com.graphhopper.farmy;

import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;

import java.util.AbstractList;
import java.util.LinkedList;
import java.util.List;

public class IdentifiedPointList extends AbstractList<IdentifiedGHPoint3D> {

    private List<IdentifiedGHPoint3D> identifiedPointList;

    public IdentifiedGHPoint3D get(int index) {
        return identifiedPointList.get(index);
    }

    public IdentifiedPointList() {
        this.identifiedPointList = new LinkedList<IdentifiedGHPoint3D>();
    }

    public IdentifiedPointList(PointList pointList) {
        this.identifiedPointList = pointListToIdentifiedPointList(pointList);
    }

    public boolean add(GHPoint3D point3D, String id) {
        this.identifiedPointList.add(new IdentifiedGHPoint3D(point3D, id));
        return true;
    }

    public boolean add(GHPoint point, String id) {
        this.identifiedPointList.add(new IdentifiedGHPoint3D(point, id));
        return true;
    }

    public boolean add(IdentifiedGHPoint3D point) {
        this.identifiedPointList.add(point);
        return true;
    }

    public IdentifiedGHPoint3D find(String id) {
        return this.identifiedPointList.stream()
                .filter(point -> point.getId().equals(id))
                .findFirst()
                .orElse(new IdentifiedGHPoint3D(new GHPoint(0, 0), "NOT_FOUND_POINT"));
    }

    public IdentifiedGHPoint3D findDepot() {
        return this.find("DEPOT");
    }

    public int size() {
        return identifiedPointList.size();
    }

    private IdentifiedPointList pointListToIdentifiedPointList(PointList pointList) {
        IdentifiedPointList tempIdentifiedPL = new IdentifiedPointList();
        int index = 0;
        for (GHPoint3D point : pointList) {
            tempIdentifiedPL.add(new IdentifiedGHPoint3D(point, index));
        }

        return tempIdentifiedPL;
    }
}
