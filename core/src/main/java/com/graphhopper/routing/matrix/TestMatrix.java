package com.graphhopper.routing.matrix;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class TestMatrix {

    public static GHPoint generateRandomLocation(GHPoint origin) {

        double lat = ThreadLocalRandom.current().nextDouble(origin.lat, origin.lat + 0.01);
        double lon = ThreadLocalRandom.current().nextDouble(origin.lon, origin.lon + 0.01);

        return new GHPoint(lat, lon);
    }

    public static void main(String[] args) {


        //try {
        //    System.in.read();
        //} catch(Exception e) {
        //    System.out.println("eo");
        //}

        //UK
        //GHPoint from = new GHPoint(51.472,-0.129);
        //GHPoint to = new GHPoint(51.501,-0.100);


        //Andorra
        //GHPoint from = new GHPoint(42.51563823109501, 1.520477128586076);
        //GHPoint to = new GHPoint(42.509281169850254, 1.5409253398454361);

        GHPoint from = new GHPoint(42.52268155770159, 1.5270400223312923);
        GHPoint to = new GHPoint(42.51809310570478, 1.5321160685824744);

        GHPoint from2 = new GHPoint(42.52268155770159, 1.5270400223312923);
        GHPoint to2 = new GHPoint(42.49878652482251, 1.5630379943925652);

        GHPoint from3 = new GHPoint(42.52268155770159, 1.5270400223312923);
        GHPoint to3 = new GHPoint(42.5031050925358, 1.5726280934108343);


        List<GHPoint> origins = new ArrayList<>();
        origins.add(from);
        origins.add(from2);
        origins.add(from3);

        List<GHPoint> targets = new ArrayList<>();
        targets.add(to);
        targets.add(to2);
        targets.add(to3);

        GHMatrixRequest matrixRequest = new GHMatrixRequest().setProfile("car");
        matrixRequest.setOrigins(origins);
        matrixRequest.setDestinations(targets);


        //request.setAlgorithm(Parameters.Algorithms.DIJKSTRA_BI);

        Profile carProfile = new Profile("car");
        carProfile.setTurnCosts(true);
        CHProfile chCarProfile = new CHProfile("car");

        List<Profile> profiles = new ArrayList<>();
        profiles.add(carProfile);

        List<CHProfile> chProfiles = new ArrayList<>();
        chProfiles.add(chCarProfile);

        GraphHopperConfig config = new GraphHopperConfig();
        config.setProfiles(profiles);
        config.setCHProfiles(chProfiles);

        GraphHopper gh = new GraphHopper()
                .setGraphHopperLocation("/home/jp.lopez/maps/matrix/andorra/")
                .setOSMFile("/home/jp.lopez/maps/osm/andorra.osm.pbf")
                .init(config)
                .importOrLoad();


        //GHResponse response2 = gh.route(request2);

        GHMatrixResponse matrix = gh.matrix(matrixRequest);
        //System.out.println(matrix.getMatrix());

        int sourceIdx = 0;

        for (GHPoint source : origins) {
            int targetIdx = 0;
            for (GHPoint destiny : targets) {


                List<GHPoint> points = new ArrayList<>();
                points.add(source);
                points.add(destiny);

                GHRequest request = new GHRequest().setProfile("car").setPoints(points);

                GHResponse response = gh.route(request);

                double diffDistance = Math.round(response.getBest().getDistance() - matrix.getMatrix().getDistance(sourceIdx, targetIdx));
                double diffTime = Math.round(response.getBest().getTime() - matrix.getMatrix().getTime(sourceIdx, targetIdx));

                System.out.println(diffDistance + "-" + response.getBest().getDistance() + " - " + matrix.getMatrix().getDistance(sourceIdx, targetIdx));
                System.out.println(diffTime + "-" + response.getBest().getTime() + " - " + matrix.getMatrix().getTime(sourceIdx, targetIdx));
                targetIdx++;
            }
            sourceIdx++;

        }
    }
}