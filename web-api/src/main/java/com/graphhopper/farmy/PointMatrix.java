package com.graphhopper.farmy;

public class PointMatrix {

    private IdentifiedGHPoint3D point1;
    private int index1;
    private IdentifiedGHPoint3D point2;
    private int index2;
    private double distance;
    private long time;

    public PointMatrix(IdentifiedGHPoint3D p1, IdentifiedGHPoint3D p2, double distance, long time) {
        this.point1 = p1;
        this.point2 = p2;
        this.distance = distance;
        this.time = time;
    }

    public PointMatrix(IdentifiedGHPoint3D p1, IdentifiedGHPoint3D p2, double distance, long time, int index1, int index2) {
        this.point1 = p1;
        this.point2 = p2;
        this.distance = distance;
        this.time = time;
        this.index1 = index1;
        this.index2 = index2;
    }

    @Override
    public String toString() {
        return "com.graphhopper.farmygh.PointMatrix{" +
                "point1=" + point1 +
                ", point2=" + point2 +
                ", distance=" + distance +
                ", time=" + time +
                '}';
    }

    /*Getters and Setters*/

    public IdentifiedGHPoint3D getPoint1() {
        return point1;
    }
    public void setPoint1(IdentifiedGHPoint3D point1) {
        this.point1 = point1;
    }

    public IdentifiedGHPoint3D getPoint2() {
        return point2;
    }

    public void setPoint2(IdentifiedGHPoint3D point2) {
        this.point2 = point2;
    }

    public int getIndex1() {
        return index1;
    }

    public void setIndex1(int index1) {
        this.index1 = index1;
    }

    public int getIndex2() {
        return index2;
    }

    public void setIndex2(int index2) {
        this.index2 = index2;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public long getTime() {
        return time;
    }
    public void setTime(long time) {
        this.time = time;
    }

}
