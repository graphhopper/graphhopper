package com.graphhopper;

// Extends GHRequest only temporary

// While some of this flags tecnically intersects, the developer really think about
// his response and what he gets, not about the implementation below it

// Right now is ALL enabled by default, not sure about this
public class HopperRequest extends GHRequest {

    public HopperRequest(double fromLat, double fromLon, double toLat, double toLon) {
        super(fromLat, fromLon, toLat, toLon);
    }

    private boolean enablePolyline = true;// global route

    private boolean enableCoordinates = true;// global route

    private boolean enablePoints = true;// global route

    private boolean enableInstructions = true;// global route

    private boolean enableInstructionsPoints = true;// for each instruction

    private boolean enableInstructionsPolyline = true;// for each instruction

    private boolean enableInstructionsCoordinates = true;// for each instruction

    /** ********************************* **/

    public boolean isEnablePolyline() {
        return enablePolyline;
    }

    public void setEnablePolyline(boolean enablePolyline) {
        this.enablePolyline = enablePolyline;
    }

    public boolean isEnableCoordinates() {
        return enableCoordinates;
    }

    public void setEnableCoordinates(boolean enableCoordinates) {
        this.enableCoordinates = enableCoordinates;
    }

    public boolean isEnablePoints() {
        return enablePoints;
    }

    public void setEnablePoints(boolean enablePoints) {
        this.enablePoints = enablePoints;
    }

    public boolean isEnableInstructions() {
        return enableInstructions;
    }

    public void setEnableInstructions(boolean enableInstructions) {
        this.enableInstructions = enableInstructions;
    }

    public boolean isEnableInstructionsPolyline() {
        return enableInstructionsPolyline;
    }

    public void setEnableInstructionsPolyline(boolean enableInstructionsPolyline) {
        this.enableInstructionsPolyline = enableInstructionsPolyline;
    }

    public boolean isEnableInstructionsCoordinates() {
        return enableInstructionsCoordinates;
    }

    public void setEnableInstructionsCoordinates(boolean enableInstructionsCoordinates) {
        this.enableInstructionsCoordinates = enableInstructionsCoordinates;
    }

    public boolean isEnableInstructionsPoints() {
        return enableInstructionsPoints;
    }

    public void setEnableInstructionsPoints(boolean enableInstructionsPoints) {
        this.enableInstructionsPoints = enableInstructionsPoints;
    }
}
