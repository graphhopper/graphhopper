package main.java.com.graphhopper.routing;

public interface IRouterConfig {
    int getMaxVisitedNodes();
    void setMaxVisitedNodes(int maxVisitedNodes);

    long getTimeoutMillis();
    void setTimeoutMillis(long timeoutMillis);

    int getMaxRoundTripRetries();
    void setMaxRoundTripRetries(int maxRoundTripRetries);

    int getNonChMaxWaypointDistance();
    void setNonChMaxWaypointDistance(int nonChMaxWaypointDistance);

    boolean isCalcPoints();
    void setCalcPoints(boolean calcPoints);

    boolean isInstructionsEnabled();
    void setInstructionsEnabled(boolean instructionsEnabled);

    boolean isSimplifyResponse();
    void setSimplifyResponse(boolean simplifyResponse);

    int getActiveLandmarkCount();
    void setActiveLandmarkCount(int activeLandmarkCount);

    double getElevationWayPointMaxDistance();
    void setElevationWayPointMaxDistance(double elevationWayPointMaxDistance);
}
