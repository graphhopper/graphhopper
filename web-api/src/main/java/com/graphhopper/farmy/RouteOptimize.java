package com.graphhopper.farmy;

import com.graphhopper.GraphHopperAPI;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.PickupService;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleType;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.util.Coordinate;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.jsprit.core.util.VehicleRoutingTransportCostsMatrix;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class RouteOptimize {

    private VehicleRoutingProblemSolution solution;
    private Location depotLocation;
    private IdentifiedPointList pointList;
    private final GraphHopperAPI graphHopper;

    public RouteOptimize(GraphHopperAPI graphHopper) throws Exception {
        this.graphHopper = graphHopper;
        buildSolution();
    }

    public void buildSolution() throws Exception {

//        Init logger

//        Read route plan

//        RoutePlanReader routePlanReader = new RoutePlanReader("input/route_plan_zurich_evening.csv");
        RoutePlanReader routePlanReader = new RoutePlanReader("input/route_plan_lausanne_evening.csv");


        //        Load the map
        this.pointList = routePlanReader.getIdentifiedPointList();

        if (this.pointList.size() == 0) {
            System.out.println(this.pointList);
            throw new Exception("Point List is Empty");
        }

        PointMatrixList pointMatrixList = new PointMatrixList(graphHopper, this.pointList);

        IdentifiedGHPoint3D depotPoint = this.pointList.find("DEPOT");

        this.depotLocation = Location.Builder.newInstance().setId(depotPoint.getId()).setCoordinate(Coordinate.newInstance(depotPoint.getLat(), depotPoint.getLon())).build();

        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance()
                .setFleetSize(VehicleRoutingProblem.FleetSize.INFINITE);
        /*
         * Adding Vehicles
         */


        for (int i = 0; i < 2; i++) {
            VehicleType type = VehicleTypeImpl.Builder.newInstance("vt1" + i)
                    .setFixedCost(0)
                    .addCapacityDimension(0, 6)
                    .setMaxVelocity(13)
                    .build();
            vrpBuilder.addVehicle(VehicleImpl.Builder.newInstance("vt1" + i + "-vehicle" + i)
                    .setStartLocation(depotLocation)
                    .setType(type)
                    .setReturnToDepot(true)
                    .build());
        }

        for (int i = 0; i < 2; i++) {
            VehicleType type = VehicleTypeImpl.Builder.newInstance("vt2" + i)
                    .setFixedCost(0)
                    .addCapacityDimension(0, 4)
                    .setMaxVelocity(21)
                    .build();
            vrpBuilder.addVehicle(VehicleImpl.Builder.newInstance("vt2" + i + "-vehicle" + i)
                    .setStartLocation(depotLocation)
                    .setType(type)
                    .setReturnToDepot(true)
                    .build());
        }



        /*
         * Adding Services
         */

        VehicleRoutingTransportCostsMatrix.Builder costMatrixBuilder = VehicleRoutingTransportCostsMatrix.Builder.newInstance(true);

        for (IdentifiedGHPoint3D point : this.pointList) {
            if (point.getId().equals(depotPoint.getId())) continue;
            vrpBuilder.addJob(Service.Builder.newInstance(point.getId())
                    .addSizeDimension(0, 1)
                    .setLocation(Location.Builder.newInstance().setId(point.getId()).setCoordinate(Coordinate.newInstance(point.getLat(), point.getLon())).build())
//                    .setTimeWindow(point.getTimeWindow())
                    .setServiceTime(point.getServiceTime())
                    .build());
        }

        for (PointMatrix pointMatrix : pointMatrixList) {
            costMatrixBuilder.addTransportDistance(pointMatrix.getPoint1().getId(), pointMatrix.getPoint2().getId(), pointMatrix.getDistance());
            costMatrixBuilder.addTransportTime(pointMatrix.getPoint1().getId(), pointMatrix.getPoint2().getId(), pointMatrix.getTime());
        }

        vrpBuilder.setRoutingCost(costMatrixBuilder.build());


        VehicleRoutingProblem vrp = vrpBuilder.build();


        VehicleRoutingAlgorithm vra = Jsprit.createAlgorithm(vrp);

        Collection<VehicleRoutingProblemSolution> solutions = vra.searchSolutions();
        this.solution = Solutions.bestOf(solutions);


//        new Plotter(vrp).plot("./output/main2_problem2.png", "p01");
//        new Plotter(vrp, Solutions.bestOf(solutions)).plot("./output/main2_sol2.png", "po");
//        new GraphStreamViewer(vrp, Solutions.bestOf(solutions)).labelWith(GraphStreamViewer.Label.ID).setRenderDelay(200).display();
//        SolutionPrinter.print(vrp, Solutions.bestOf(solutions), SolutionPrinter.Print.VERBOSE);
//        SolutionAnalyser analyser = new SolutionAnalyser(vrp, Solutions.bestOf(solutions), vrp.getTransportCosts());


//
//        System.out.println("tp_distance: " + analyser.getDistance());
//        System.out.println("tp_time: " + analyser.getTransportTime());
//        System.out.println("waiting: " + analyser.getWaitingTime());
//        System.out.println("service: " + analyser.getServiceTime());
//        System.out.println("#picks: " + analyser.getNumberOfPickups());
//        System.out.println("#deliveries: " + analyser.getNumberOfDeliveries());

//        for (VehicleRoute route : solution.getRoutes()) {
//            StringBuilder urlStr = new StringBuilder("http://localhost:8989/maps/");
//            urlStr.append("?point=").append(depotLocation.getCoordinate().getX()).append(",").append(depotLocation.getCoordinate().getY());
//            for (TourActivity activity : route.getActivities()) {
//                Coordinate coordinate = activity.getLocation().getCoordinate();
//                urlStr.append("&point=").append(coordinate.getX()).append(",").append(coordinate.getY());
//            }
//            System.out.println(urlStr);
//        }

    }

    public HashMap<String, ArrayList> getOptimizedRoutes() {
        HashMap<String, ArrayList> allMap = new HashMap<>();
        for (VehicleRoute route : solution.getRoutes()) {
            ArrayList<GHPoint> waypoints = new ArrayList<>();
            waypoints.add(new IdentifiedGHPoint3D(depotLocation.getCoordinate().getX(), depotLocation.getCoordinate().getY(), 0, "DEPOT"));
            for (TourActivity activity : route.getActivities()) {
                Service service = ((PickupService) activity).getJob();
                waypoints.add(this.pointList.find(service.getId()));
            }
            allMap.put(route.getVehicle().getId(), waypoints);
        }
        return allMap;
    }

    public VehicleRoutingProblemSolution getSolution() {
        return solution;
    }

    public Location getDepotLocation() {
        return depotLocation;
    }

    public IdentifiedPointList getPointList() {
        return pointList;
    }

    public GraphHopperAPI getGraphHopper() {
        return graphHopper;
    }
}
