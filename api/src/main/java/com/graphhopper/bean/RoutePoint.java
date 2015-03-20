package com.graphhopper.bean;

import com.fasterxml.jackson.annotation.JsonInclude;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@JsonInclude(JsonInclude.Include.NON_NULL)
@XmlAccessorType(value = XmlAccessType.FIELD)
public class RoutePoint {

    private double latitude = Double.NaN;
    private double longitude = Double.NaN;
    private double elevation = Double.NaN;

    /** ************************************ **/

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getElevation() {
        return elevation;
    }

    public void setElevation(double elevation) {
        this.elevation = elevation;
    }
}
