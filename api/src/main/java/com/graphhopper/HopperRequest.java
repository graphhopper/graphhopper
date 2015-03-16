package com.graphhopper;

// Extends GHRequest only temporary

// While some of this flags tecnically intersects, the developer really think about
// his response and what he gets, not about the implementation below it

// Right now is ALL enabled by default, not sure about this
public class HopperRequest extends GHRequest {

    public HopperRequest(double fromLat, double fromLon, double toLat, double toLon) {
        super(fromLat, fromLon, toLat, toLon);
    }

    private boolean fetchPolyline = true;// global route

    private boolean fetchCoordinates = true;// global route

    private boolean fetchPoints = true;// global route

    private boolean fetchInstructions = true;// global route

    private boolean fetchInstructionsPoints = true;// for each instruction

    private boolean fetchInstructionsPolyline = true;// for each instruction

    private boolean fetchInstructionsCoordinates = true;// for each instruction

    /** ********************************* **/

    public boolean isFetchPolyline() {
        return fetchPolyline;
    }

    public void setFetchPolyline(boolean fetchPolyline) {
        this.fetchPolyline = fetchPolyline;
    }

    public boolean isFetchCoordinates() {
        return fetchCoordinates;
    }

    public void setFetchCoordinates(boolean fetchCoordinates) {
        this.fetchCoordinates = fetchCoordinates;
    }

    public boolean isFetchPoints() {
        return fetchPoints;
    }

    public void setFetchPoints(boolean fetchPoints) {
        this.fetchPoints = fetchPoints;
    }

    public boolean isFetchInstructions() {
        return fetchInstructions;
    }

    public void setFetchInstructions(boolean fetchInstructions) {
        this.fetchInstructions = fetchInstructions;
    }

    public boolean isFetchInstructionsPolyline() {
        return fetchInstructionsPolyline;
    }

    public void setFetchInstructionsPolyline(boolean fetchInstructionsPolyline) {
        this.fetchInstructionsPolyline = fetchInstructionsPolyline;
    }

    public boolean isFetchInstructionsCoordinates() {
        return fetchInstructionsCoordinates;
    }

    public void setFetchInstructionsCoordinates(boolean fetchInstructionsCoordinates) {
        this.fetchInstructionsCoordinates = fetchInstructionsCoordinates;
    }

    public boolean isFetchInstructionsPoints() {
        return fetchInstructionsPoints;
    }

    public void setFetchInstructionsPoints(boolean fetchInstructionsPoints) {
        this.fetchInstructionsPoints = fetchInstructionsPoints;
    }
}
