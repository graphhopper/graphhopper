package com.graphhopper.routing.matrix;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestMatrix {

    public static GHMatrixRequest parseRawMatrix(String rawMatrix) {
        String[] rawPoints = rawMatrix.split("&");
        Stream<GHPoint> sourceGHPoints = Arrays.stream(rawPoints).filter(p -> p.startsWith("s")).map(p -> {
            String[] latLong = p.replace("s=", "").split(",");
            return new GHPoint(Double.parseDouble(latLong[0]), Double.parseDouble(latLong[1]));
        });
        Stream<GHPoint> destGHPoints = Arrays.stream(rawPoints).filter(p -> p.startsWith("d")).map(p -> {
            String[] latLong = p.replace("d=", "").split(",");
            return new GHPoint(Double.parseDouble(latLong[0]), Double.parseDouble(latLong[1]));
        });

        GHMatrixRequest test = new GHMatrixRequest().setProfile("car");
        test.setDestinations(destGHPoints.collect(Collectors.toList()));
        test.setOrigins(sourceGHPoints.collect(Collectors.toList()));
        return test;
    }


    public static void main(String[] args) {



        String rawMatrix = "s=43.729864504047356,7.424981927073241&d=43.732306114407095,7.422461325163131";

        GHMatrixRequest matrixRequest = parseRawMatrix(rawMatrix);


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
                .setGraphHopperLocation("/home/jp.lopez/maps/matrix/uk/")
                .setOSMFile("/home/jp.lopez/sources/graphhopper-matrix/core/files/uk.osm.gz")
                .init(config)
                .setGraphHopperLocation("/home/jp.lopez/maps/matrix/uk/")
                .setOSMFile("/home/jp.lopez/sources/graphhopper-matrix/core/files/uk.osm.gz")
                .importOrLoad();


        GHMatrixResponse matrix = gh.matrix(matrixRequest);
        //GHMatrixResponse matrix2 = gh.matrix(matrixRequest);
        //GHMatrixResponse matrix3 = gh.matrix(matrixRequest);
        //System.out.println(matrix.getMatrix());
        int errors = 0;

        int sourceIdx = 0;

        for (GHPoint source : matrixRequest.getOrigins()) {
            int targetIdx = 0;
            for (GHPoint destiny : matrixRequest.getDestinations()) {

                List<GHPoint> points = new ArrayList<>();
                points.add(source);
                points.add(destiny);

                GHRequest request = new GHRequest().setProfile("car").setPoints(points);
                //request.setAlgorithm(Parameters.Algorithms.DIJKSTRA_BI);

                GHResponse response = gh.route(request);

                double diffDistance = Math.round(response.getBest().getDistance() - matrix.getMatrix().getDistance(sourceIdx, targetIdx));
                double diffTime = Math.round(response.getBest().getTime() - matrix.getMatrix().getTime(sourceIdx, targetIdx));


                if(diffDistance > 0 || diffTime > 1){
                    System.out.println("");
                    System.out.println("++++++++++++++++++++++++++++++++++++++++++");
                    System.out.println(points);
                    System.out.println("Distance: " + diffDistance + " GH: " + response.getBest().getDistance() + " Matrix: " + matrix.getMatrix().getDistance(sourceIdx, targetIdx));
                    System.out.println("Time: " + diffTime + " GH: " + response.getBest().getTime() + " Matrix: " + matrix.getMatrix().getTime(sourceIdx, targetIdx));
                    errors++;
                }

                targetIdx++;
            }
            sourceIdx++;

        }

        System.out.println("Total errors:" + errors);
    }
}