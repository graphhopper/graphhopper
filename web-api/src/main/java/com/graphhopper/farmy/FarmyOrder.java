package com.graphhopper.farmy;

import com.graphhopper.jsprit.core.problem.solution.route.activity.TimeWindow;

import java.util.ArrayList;

public class FarmyOrder {
    public Integer id;
    public String number;
    public Double latitude;
    public Double longitude;
    public Double weight;
    public Integer serviceTime;
    public TimeWindow timeWindow;
    public String direction;

    public FarmyOrder(Integer id, String number, Double latitude, Double longitude, Double weight, Integer serviceTime, TimeWindow timeWindow, String direction) {
        this.id = id;
        this.number = number;
        this.latitude = latitude;
        this.longitude = longitude;
        this.weight = weight;
        this.serviceTime = serviceTime;
        this.timeWindow = timeWindow;
        this.direction = direction;
    }

    public FarmyOrder() {}

    @Override
    public String toString() {
        return "FarmyOrder{" +
                "id=" + id +
                ", number='" + number + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", weight=" + weight +
                ", serviceTime=" + serviceTime +
                ", timeWindow=" + timeWindow +
                ", direction='" + direction + '\'' +
                '}';
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public Integer getServiceTime() {
        return serviceTime;
    }

    public void setServiceTime(Integer serviceTime) {
        this.serviceTime = serviceTime;
    }

    public TimeWindow getTimeWindow() {
        return timeWindow;
    }

    public void setTimeWindow(ArrayList<Double> timeWindow) {
        this.timeWindow = new TimeWindow(timeWindow.get(0), timeWindow.get(1));
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }
}
