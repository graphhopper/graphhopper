package com.graphhopper.routing;

import com.graphhopper.GHRequest;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.PathProcessor;
import com.graphhopper.util.shapes.GHPoint;

import java.util.Collections;
import java.util.List;

// TODO ORS (minor): this class seems not in use, can it be removed?
public class ExtendedGHRequest extends GHRequest {
    private EdgeFilter edgeFilter;
    private PathProcessor pathProcessor;

    public ExtendedGHRequest() {
        super();
    }
    public ExtendedGHRequest(int size) {
        super(size);
    }

    /**
     * Set routing request from specified startPlace (fromLat, fromLon) to endPlace (toLat, toLon)
     * with a preferred start and end heading. Headings are north based azimuth (clockwise) in (0,
     * 360) or NaN for equal preference.
     */
    public ExtendedGHRequest(double fromLat, double fromLon, double toLat, double toLon,
                     double startHeading, double endHeading) {
        super(fromLat, fromLon, toLat, toLon, startHeading, endHeading);
    }

    /**
     * Set routing request from specified startPlace (fromLat, fromLon) to endPlace (toLat, toLon)
     */
    public ExtendedGHRequest(double fromLat, double fromLon, double toLat, double toLon) {
        super(fromLat, fromLon, toLat, toLon);
    }

    /**
     * Set routing request from specified startPlace to endPlace with a preferred start and end
     * heading. Headings are north based azimuth (clockwise) in (0, 360) or NaN for equal preference
     */
    public ExtendedGHRequest(GHPoint startPlace, GHPoint endPlace, double startHeading, double endHeading) {
        super(startPlace, endPlace, startHeading, endHeading);
    }

    public ExtendedGHRequest(GHPoint startPlace, GHPoint endPlace) {
        super(startPlace, endPlace, Double.NaN, Double.NaN);
    }

    /**
     * Set routing request
     * <p>
     *
     * @param points          List of stopover points in order: start, 1st stop, 2nd stop, ..., end
     * @param favoredHeadings List of favored headings for starting (start point) and arrival (via
     *                        and end points) Headings are north based azimuth (clockwise) in (0, 360) or NaN for equal
     */
    public ExtendedGHRequest(List<GHPoint> points, List<Double> favoredHeadings) {
        super(points, favoredHeadings);
    }

    /**
     * Set routing request
     * <p>
     *
     * @param points List of stopover points in order: start, 1st stop, 2nd stop, ..., end
     */
    public ExtendedGHRequest(List<GHPoint> points) {
        this(points, Collections.nCopies(points.size(), Double.NaN));
    }

    // ****************************************************************
    // ORS-GH MOD START
    // ****************************************************************
    // Modification by Maxim Rylov: Added getEdgeFilter method.
    // TODO ORS (minor): provide a reason for this change
    public EdgeFilter getEdgeFilter() {
        return edgeFilter;
    }
    // Modification by Maxim Rylov: Added setEdgeFilter method.
    public GHRequest setEdgeFilter(EdgeFilter edgeFilter) {
        if (edgeFilter != null) {
            this.edgeFilter = edgeFilter;
        }
        return this;
    }

    // TODO ORS (minor): provide a reason for this change
    public PathProcessor getPathProcessor() {
        return this.pathProcessor;
    }

    // TODO ORS (minor): provide a reason for this change
    public void setPathProcessor(PathProcessor pathProcessor) {
        this.pathProcessor = pathProcessor;
    }

}
