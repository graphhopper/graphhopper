package com.graphhopper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.graphhopper.bean.RouteBbox;
import com.graphhopper.bean.RouteError;
import com.graphhopper.bean.RouteInstruction;
import com.graphhopper.bean.RoutePoint;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

/**
 * Wrapper to simplify... the wrapper made to simplify the output of GraphHopper :)
 * <p/>
 * @author Piro Fabio
 */
@XmlRootElement(name = "response")
@XmlAccessorType(value = XmlAccessType.FIELD)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HopperResponse {

    private List<RouteError> errors;

    private double distance;

    private double weight;

    private long time;

    private RouteBbox bbox;// This could be a double[], but RouteBox is more semantical with getters and in json

    private List<RouteInstruction> instructions;

    private List<RoutePoint> points;// LazyPointList

    private List<double[]> coordinates;

    private String polyline;

    /** ******************************** **/

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public RouteBbox getBbox() {
        return bbox;
    }

    public void setBbox(RouteBbox bbox) {
        this.bbox = bbox;
    }

    public List<RouteInstruction> getInstructions() {
        return instructions;
    }

    public void setInstructions(List<RouteInstruction> instructions) {
        this.instructions = instructions;
    }

    public List<RoutePoint> getPoints() {
        return points;
    }

    public void setPoints(List<RoutePoint> points) {
        this.points = points;
    }

    public List<double[]> getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(List<double[]> coordinates) {
        this.coordinates = coordinates;
    }

    public String getPolyline() {
        return polyline;
    }

    public void setPolyline(String polyline) {
        this.polyline = polyline;
    }
}
