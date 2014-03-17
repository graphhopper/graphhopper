package com.graphhopper.util;

public class UpcomingInstruction {
    private Instruction nextInstruction;
    private double distanceToGo;
    private long millisToGo;

    // Points to go till next instruction is encountered
    private PointList points;

    public UpcomingInstruction(Instruction nextInstruction, double distanceToGo, long millisToGo, PointList points) {
        this.nextInstruction = nextInstruction;
        this.distanceToGo = distanceToGo;
        this.millisToGo = millisToGo;
        this.points = points;
    }

    public Instruction getNextInstruction() {
        return nextInstruction;
    }
    public void setNextInstruction(Instruction nextInstruction) {
        this.nextInstruction = nextInstruction;
    }
    public double getDistanceToGo() {
        return distanceToGo;
    }
    public void setDistanceToGo(double distanceToGo) {
        this.distanceToGo = distanceToGo;
    }
    public long getMillisToGo() {
        return millisToGo;
    }
    public void setMillisToGo(long millisToGo) {
        this.millisToGo = millisToGo;
    }

    public PointList getPoints() {
        return points;
    }

    public void setPoints(PointList points) {
        this.points = points;
    }


}
