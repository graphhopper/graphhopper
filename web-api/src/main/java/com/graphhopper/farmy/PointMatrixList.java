package com.graphhopper.farmy;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.PathWrapper;
import com.graphhopper.util.shapes.GHPoint;

import java.util.AbstractList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class PointMatrixList extends AbstractList<PointMatrix> {

    private final List<PointMatrix> pointMatrixList;
    private final IdentifiedPointList pointList;
    private final GraphHopperAPI graphHopper;

    public PointMatrixList(GraphHopperAPI graphHopper, IdentifiedPointList pointList, GHPoint depotPoint) {
        this.graphHopper = graphHopper;
        this.pointList = pointList;
        this.pointList.add(depotPoint, "DEPOT"); // Zurich
        this.pointMatrixList = Build();
    }

    public PointMatrixList(GraphHopperAPI graphHopper, IdentifiedPointList pointList, IdentifiedGHPoint3D depotPoint) {
        this.graphHopper = graphHopper;
        this.pointList = pointList;
        this.pointList.add(depotPoint);
        this.pointMatrixList = Build();
    }

    public PointMatrixList(GraphHopperAPI graphHopper, IdentifiedPointList pointList) {
        this.graphHopper = graphHopper;
        this.pointList = pointList;
        this.pointMatrixList = Build();
    }

    public PointMatrix get(int index) {
        return pointMatrixList.get(index);
    }

    public List<PointMatrix> get() {
        return pointMatrixList;
    }

    public int size() {
        return pointMatrixList.size();
    }

    private GraphHopperAPI loadHopper() {
        return graphHopper;
    }

    private List<PointMatrix> Build() {
        List<PointMatrix> pointMatrixList = new LinkedList<>();
        int index1 = 0;
        for(IdentifiedGHPoint3D point: this.pointList) {
            int index2 = 0;
            for (IdentifiedGHPoint3D point2: this.pointList) {
//                if (point.equals(point2) || point.getId().equals(point2.getId())) continue;
                GHResponse response = this.graphHopper.route(new GHRequest(point, point2)
                        .setWeighting("fastest")
                        .setVehicle("car")
                        .setLocale(Locale.US));
                PathWrapper path = response.getBest();
                // Convert path time to seconds
                pointMatrixList.add(new PointMatrix(point, point2, path.getDistance(), path.getTime() / 1000, index1, index2));
                index2++;
            }
            index1++;
        }
        return pointMatrixList;
    }
}