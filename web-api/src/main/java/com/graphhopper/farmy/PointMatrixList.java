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

    public PointMatrixList(GraphHopperAPI graphHopper, IdentifiedPointList pointList) {
        this.graphHopper = graphHopper;
        this.pointList = pointList;
        this.pointList.add(new GHPoint(47.38449,8.4904332), "DEPOT"); // Zurich
//        this.pointList.add(new GHPoint(46.5412127,6.6175954), "DEPOT"); // Laussanne
//        this.pointList.add(new GHPoint(46.449892,6.8683), "DEPOT"); // Laussanne

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
        List<PointMatrix> pointMatrixList = new LinkedList<PointMatrix>();
        for(IdentifiedGHPoint3D point: this.pointList) {
            for (IdentifiedGHPoint3D point2: this.pointList) {
                if (point.equals(point2) && point.getId().equals(point2.getId())) continue;
                GHResponse response = this.graphHopper.route(new GHRequest(point, point2)
                        .setWeighting("fastest")
                        .setVehicle("car")
                        .setLocale(Locale.US));
                PathWrapper path = response.getBest();

                pointMatrixList.add(new PointMatrix(point, point2, path.getDistance(), path.getTime()));
            }
        }
        return pointMatrixList;
    }
}
