package com.graphhopper.wrapper;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class PathsBean {

    /**
     * The bounding box of the route, format:
     * minLon, minLat, maxLon, maxLat
     */
    private List<Double> bbox;

    /**
     * The overall distance of the route, in meter
     */
    private double distance;

    /**
     * Undocumented
     */
    private double weight;

    /**
     *
     * Contains information about the instructions for this route.
     * The last instruction is always the Finish instruction and takes 0ms and 0meter.
     * Keep in mind that instructions are currently under active development and can
     * sometimes contain misleading information, so, make sure you always show an image
     * of the map at the same time when navigating your users!
     */
    private List<InstructionBean> instructions;

    /**
     * Points of the paths
     */
    private PointsBean points;

    /**
     * The polyline encoded coordinates of the path.
     */
    @XmlElement(name = "points_encoded")
    private String pointsEncoded;

    /**
     * The overall time of the route, in ms
     */
    private long time;

    public List<Double> getBbox() {
        return bbox;
    }

    public void setBbox(List<Double> bbox) {
        this.bbox = bbox;
    }

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

    public List<InstructionBean> getInstructions() {
        return instructions;
    }

    public void setInstructions(List<InstructionBean> instructions) {
        this.instructions = instructions;
    }

    public PointsBean getPoints() {
        return points;
    }

    public void setPoints(PointsBean points) {
        this.points = points;
    }

    public String getPointsEncoded() {
        return pointsEncoded;
    }

    public void setPointsEncoded(String pointsEncoded) {
        this.pointsEncoded = pointsEncoded;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}

