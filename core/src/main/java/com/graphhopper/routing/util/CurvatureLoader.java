package com.graphhopper.routing.util;


import de.micromata.opengis.kml.v_2_2_0.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Thi Class can Load Curvature Data from KML files.
 * It is required to load files in the Format that is created by: https://github.com/adamfranco/curvature.
 */
public enum CurvatureLoader {

    INSTANCE;

    private RoadData roadData;

    public RoadData getCurvyRoads(){
        return this.roadData;
    }

    private CurvatureLoader() {
        this.roadData = loadRoadDataFromKML(new File("germany.c_300.kml"));
    }

    private RoadData loadRoadDataFromKML(File kmlFile) {


        Kml worldBorders = Kml.unmarshal(kmlFile);
        RoadData data = new RoadData();
        Document document = (Document) worldBorders.getFeature();

        for (Feature feature : document.getFeature()) {
            data.add(generateRoadEntryFromFeature(feature));
        }

        return data;
    }

    private RoadEntry generateRoadEntryFromFeature(Feature feature) {

        Placemark placemark = (Placemark) feature;
        double curvature = getCurvatureFromDescription(placemark.getDescription());
        List<Point> points = getPointsFromPlaceMark(placemark);


        return new RoadEntry(placemark.getName() + "_" + points.size(), points, curvature, "speed", "replace");
    }

    private List<Point> getPointsFromPlaceMark(Placemark placemark) {
        Geometry geometry = placemark.getGeometry();
        List<Coordinate> coordinates = ((LineString) geometry).getCoordinates();
        List<Point> points = new ArrayList<Point>();
        for (Coordinate coordinate : coordinates) {

            double longitude = coordinate.getLongitude();
            double latitude = coordinate.getLatitude();
            points.add(new Point(latitude, longitude));
        }

        return points;
    }

    private double getCurvatureFromDescription(String description) {

        String curvature = description.split("\n")[0];
        curvature = curvature.split(" ")[1];
        return Double.parseDouble(curvature) * 100;

    }

}