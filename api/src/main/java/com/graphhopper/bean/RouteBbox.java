package com.graphhopper.bean;


import com.fasterxml.jackson.annotation.JsonProperty;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(value = XmlAccessType.FIELD)
public class RouteBbox {

    @JsonProperty("min_latitude")
    @XmlElement(name = "min_latitude")
    public double minLatitude = Double.NaN;

    @JsonProperty("max_latitude")
    @XmlElement(name = "max_latitude")
    public double maxLatitude = Double.NaN;

    @JsonProperty("min_longitude")
    @XmlElement(name = "min_longitude")
    public double minLongitude = Double.NaN;

    @JsonProperty("max_longitude")
    @XmlElement(name = "max_longitude")
    public double maxLongitude = Double.NaN;

    /** ******************************** **/

    public double getMinLongitude() {
        return minLongitude;
    }

    public void setMinLongitude(double minLongitude) {
        this.minLongitude = minLongitude;
    }

    public double getMaxLongitude() {
        return maxLongitude;
    }

    public void setMaxLongitude(double maxLongitude) {
        this.maxLongitude = maxLongitude;
    }

    public double getMinLatitude() {
        return minLatitude;
    }

    public void setMinLatitude(double minLatitude) {
        this.minLatitude = minLatitude;
    }

    public double getMaxLatitude() {
        return maxLatitude;
    }

    public void setMaxLatitude(double maxLatitude) {
        this.maxLatitude = maxLatitude;
    }
}
