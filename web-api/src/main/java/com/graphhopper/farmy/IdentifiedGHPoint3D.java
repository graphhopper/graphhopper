package com.graphhopper.farmy;

import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TimeWindow;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;

import java.util.HashMap;

public class IdentifiedGHPoint3D extends GHPoint3D {

    public String id;
    public String direction;
    public double serviceTime;
    public TimeWindow timeWindow;
    public double plannedTime;
    public double weight;
    public double distance;

    public IdentifiedGHPoint3D(double lat, double lon, double elevation, String id) {
        super(lat, lon, elevation);
        this.id = id;
    }

    public IdentifiedGHPoint3D(double lat, double lon, double elevation, int id) {
        super(lat, lon, elevation);
        this.id = Integer.toString(id);
    }

    public IdentifiedGHPoint3D(GHPoint3D point, int id) {
        super(point.getLat(), point.getLon(), point.getElevation());
        this.id = Integer.toString(id);
    }

    public IdentifiedGHPoint3D(GHPoint3D point, String id) {
        super(point.getLat(), point.getLon(), point.getElevation());
        this.id = id;
    }

    public IdentifiedGHPoint3D(GHPoint point, String id) {
        super(point.getLat(), point.getLon(), 0);
        this.id = id;
    }

    public IdentifiedGHPoint3D(Location point, String id) {
        super(point.getCoordinate().getX(), point.getCoordinate().getY(), 0);
        this.id = id;
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }


    public double getServiceTime() {
        return serviceTime;
    }

    public IdentifiedGHPoint3D setServiceTime(double serviceTime) {
        this.serviceTime = serviceTime;
        return this;
    }

    public TimeWindow getTimeWindow() {
        return timeWindow;
    }

    public IdentifiedGHPoint3D setTimeWindow(TimeWindow timeWindow) {
        this.timeWindow = timeWindow;
        return this;
    }

    public IdentifiedGHPoint3D setTimeWindow(double start, double end) {
        this.timeWindow = new TimeWindow(start, end);
        return this;
    }

    public String getDirection() {
        return direction;
    }

    public IdentifiedGHPoint3D setDirection(String direction) {
        this.direction = direction;
        return this;
    }

    public double getPlannedTime() {
        return plannedTime;
    }

    public IdentifiedGHPoint3D setPlannedTime(double plannedTime) {
        this.plannedTime = plannedTime;
        return this;
    }

    public double getWeight() {
        return weight;
    }

    public IdentifiedGHPoint3D setWeight(double weight) {
        this.weight = weight;
        return this;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    @Override
    public String toString() {
        return super.toString() + "," + id;
    }

    public Location getLocation() {
        return Location.newInstance(this.getLat(), this.getLon());
    }

    public HashMap<String, Object> toGeoJsonWithId() {
        return new HashMap<String, Object>() {{
            put("id", id);
            put("latitude", String.valueOf(lat));
            put("longitude", String.valueOf(lon));
            put("elevation", String.valueOf(ele));
            put("time_window_start", getTimeWindow() != null ? String.valueOf(getTimeWindow().getStart() / 1000) : "");
            put("time_window_end", getTimeWindow() != null ? String.valueOf(getTimeWindow().getEnd() / 1000) : "");
            put("service_time", String.valueOf(getServiceTime() / 1000));
            put("direction", getDirection());
            put("planned_time", String.valueOf(getPlannedTime() / 1000));
            put("weight", String.valueOf(getWeight()));
            put("distance", String.valueOf(getDistance()));
        }};
    }
}
