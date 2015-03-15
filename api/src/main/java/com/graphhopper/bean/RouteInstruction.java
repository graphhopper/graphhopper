package com.graphhopper.bean;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@XmlAccessorType(value = XmlAccessType.FIELD)
public class RouteInstruction {

    private double distance;

    private int[] interval;

    private List<RoutePoint> points;// LazyPointList: must be documented VERY well

    private List<double[]> coordinates;// Removed the immutable "LineString" for each request, yes, not plug-and-play on leaflet, but managing forever a constant is a bad idea

    private String polyline;

    private int sign;

    private String text;

    @JsonProperty("annotation_text")
    @XmlElement(name = "annotation_text")
    private String annotationText;

    @JsonProperty("annotation_importance")
    @XmlElement(name = "annotation_importance")
    private Integer annotationImportance;

    private long time;

    /** *********************************** **/

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public int[] getInterval() {
        return interval;
    }

    public void setInterval(int[] interval) {
        this.interval = interval;
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

    public int getSign() {
        return sign;
    }

    public void setSign(int sign) {
        this.sign = sign;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getAnnotationText() {
        return annotationText;
    }

    public void setAnnotationText(String annotationText) {
        this.annotationText = annotationText;
    }

    public Integer getAnnotationImportance() {
        return annotationImportance;
    }

    public void setAnnotationImportance(Integer annotationImportance) {
        this.annotationImportance = annotationImportance;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
