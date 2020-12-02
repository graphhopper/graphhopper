package com.graphhopper.config;


public class CustomAreaFile {
    
    private String location;
    private String idField;
    private String encodedValue = "";
    private int encodedValueLimit = -1;
    private String maxBbox = "-180,180,-90,90";
    
    
    private CustomAreaFile() {
        // default constructor needed for jackson
    }

    public CustomAreaFile(String location) {
        setLocation(location);
    }

    public String getLocation() {
        return location;
    }


    public void setLocation(String location) {
        this.location = location;
    }


    public String getIdField() {
        return idField;
    }


    public void setIdField(String idField) {
        this.idField = idField;
    }


    public String getEncodedValue() {
        return encodedValue;
    }


    public void setEncodedValue(String encodedValue) {
        this.encodedValue = encodedValue;
    }

    public int getEncodedValueLimit() {
        return encodedValueLimit;
    }


    public void setEncodedValueLimit(int encodedValueLimit) {
        this.encodedValueLimit = encodedValueLimit;
    }


    public String getMaxBbox() {
        return maxBbox;
    }


    public void setMaxBbox(String maxBbox) {
        this.maxBbox = maxBbox;
    }


    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("CustomAreaFile [location=");
        builder.append(location);
        builder.append(", idField=");
        builder.append(idField);
        builder.append(", encodedValue=");
        builder.append(encodedValue);
        builder.append(", maxBbox=");
        builder.append(maxBbox);
        builder.append("]");
        return builder.toString();
    }
}
