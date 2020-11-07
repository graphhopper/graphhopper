package com.graphhopper.farmy;

import com.google.gson.Gson;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.analysis.SolutionAnalyser;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.job.*;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.*;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleType;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.util.Coordinate;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.jsprit.core.util.UnassignedJobReasonTracker;
import com.graphhopper.jsprit.core.util.VehicleRoutingTransportCostsMatrix;
import com.graphhopper.util.shapes.GHPoint;
import org.apache.commons.math3.stat.Frequency;

import java.util.*;

public class RouteOptimize {

    private VehicleRoutingProblemSolution solution;
    private IdentifiedPointList pointList;
    private final GraphHopperAPI graphHopper;
    private IdentifiedGHPoint3D depotPoint;
    private RoutePlanReader routePlanReader;
    private UnassignedJobReasonTracker reasonTracker;

    public VehicleRoutingTransportCostsMatrix vrtcm;

    public List<Double> speedAvg = new ArrayList<>();




//    public RouteOptimize(GraphHopperAPI graphHopper, InputStream fileStream, GHPoint depotPoint) throws Exception {
//        this.graphHopper = graphHopper;
//        buildSolution(fileStream, depotPoint);
//    }

//    public RouteOptimize(GraphHopperAPI graphHopper, FarmyOrder[] farmyOrders) throws Exception {
//        this.graphHopper = graphHopper;
//        buildSolution(farmyOrders);
//    }

    public RouteOptimize(GraphHopperAPI graphHopper, FarmyOrder[] farmyOrders, FarmyVehicle[] farmyVehicles) throws Exception {
        this.graphHopper = graphHopper;
        buildSolution(farmyOrders, farmyVehicles);
    }

//    public void buildSolution(InputStream fileStream, GHPoint depotPointParms) throws Exception {
//        RoutePlanReader routePlanReader = new RoutePlanReader(fileStream);
//        this.depotLocation = Location.newInstance(depotPointParms.getLat(), depotPointParms.getLon());
//        build(routePlanReader.getIdentifiedPointList());
//    }

    public void buildSolution(FarmyOrder[] farmyOrders, FarmyVehicle[] farmyVehicles) throws Exception {
        this.routePlanReader = new RoutePlanReader(farmyOrders);
        build(this.routePlanReader.getIdentifiedPointList(), farmyVehicles);
    }

    public HashMap<String, String> getOptimizedRoutes() {
        HashMap<String, String> allMap = new HashMap<>();

        HashMap<String, HashMap<String, Object>> optimizedRoutesMap = new HashMap<>();
        for (VehicleRoute route : solution.getRoutes()) {
            HashMap<String, Object> vehicleHashMap = new HashMap<>();
            IdentifiedGHPoint3D firstPoint = null;
            IdentifiedGHPoint3D lastPoint = null;
            ArrayList<GHPoint> waypoints = new ArrayList<>();
            double routeDistance = 0.0;

            // Add depot location
//            waypoints.add(this.depotPoint);

            TourActivity[] tourActivities = route.getActivities().toArray(new TourActivity[0]);

            for (TourActivity activity : tourActivities) {
                double distance;
                Job service;
                try {
                    service = ((DeliverService) activity).getJob();
                } catch (Exception e) {
                    service = ((PickupService) activity).getJob();
                }

                // Get Job as DeliveryService
                IdentifiedGHPoint3D idPoint = this.pointList.find(service.getId()); // Find point by service id
                idPoint.setPlannedTime(activity.getArrTime()); // set arrtime from activity
                waypoints.add(idPoint); // add the point to waypoints
//              Calc for distance

                if (lastPoint != null) {
                    distance = this.vrtcm.getDistance(idPoint.getId(), lastPoint.getId());
                    idPoint.setDistance(distance);
                    routeDistance += distance;

                    System.out.printf("Point: %s \n Distance: %s \n Time: %s%n", idPoint.getId(), distance, activity.getArrTime());
                }
                if (firstPoint == null) firstPoint = idPoint;
                lastPoint = idPoint;

            }


//          allMap.get(route.getVehicle().getId()).put(allMap.get(route.getVehicle().getId()).get("waypoints"), waypoints);
            vehicleHashMap.put("waypoints", waypoints.toArray());

            // Calc add add route distance
            if (this.vrtcm != null && firstPoint != null) {
                vehicleHashMap.put("distance", routeDistance);
            }

//          Calc for avg speed
            if (this.speedAvg.size() > 0) {
                vehicleHashMap.put("avg_speed", (routeDistance / (route.getEnd().getArrTime() - route.getStart().getArrTime())));
            }

            optimizedRoutesMap.put(routeVehicleId(route, optimizedRoutesMap, 0), vehicleHashMap);
        }
        Gson gson = new Gson();

        allMap.put("OptimizedRoutes", gson.toJson(optimizedRoutesMap));
        HashMap<String, Frequency> frequencyHashMap= new HashMap<>();
        this.reasonTracker.getFailedConstraintNamesFrequencyMapping().entrySet().stream().filter(o -> this.solution.getUnassignedJobs().stream().anyMatch(d -> d.getId().equals(o.getKey()))).forEach(d -> frequencyHashMap.put(d.getKey(), d.getValue()));

        allMap.put("UnassignedJobs",  gson.toJson(frequencyHashMap));
        return allMap;
    }

    public VehicleRoutingProblemSolution getSolution() {
        return solution;
    }

    public Location getDepotLocation() {
        return this.depotPoint.getLocation();
    }

    public IdentifiedPointList getPointList() {
        return pointList;
    }

    public GraphHopperAPI getGraphHopper() {
        return graphHopper;
    }


    private void build(IdentifiedPointList pointList, FarmyVehicle[] farmyVehicles) throws Exception {

        //        Load the map
        this.pointList = pointList;

        if (this.pointList.size() == 0) {
//            System.out.println(this.pointList);
            throw new Exception("Point List is Empty");
        }


        this.depotPoint = this.routePlanReader.depotPoint();

        PointMatrixList pointMatrixList = new PointMatrixList(graphHopper, this.pointList, this.depotPoint);

        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance()
                .setFleetSize(VehicleRoutingProblem.FleetSize.FINITE);
        /*
         * Adding Vehicles
         */

//        vrpBuilder.addJob(Service.Builder.newInstance("DEPOT")
//                .setLocation(depotLocation)
//                .addSizeDimension(0, this.pointList.size()).build());


        for (FarmyVehicle vehicle : farmyVehicles) {
            VehicleType type = VehicleTypeImpl.Builder.newInstance(String.format("[TYPE] #%s", vehicle.getId()))
                    .setFixedCost(vehicle.getFixedCosts()) //Fixe Bedienzeit
                    .setCostPerDistance(vehicle.getCostsPerDistance())
                    .setCostPerTransportTime(vehicle.getCostsPerTransportTime())
                    .setCostPerServiceTime(vehicle.getCostsPerServiceTime())
                    .setCostPerWaitingTime(vehicle.getCostPerWaitingTime())
                    .addCapacityDimension(0, vehicle.getCapacity())
                    .setMaxVelocity(vehicle.isPlus() ? 50.0/3.6 : 80.0/3.6) // ~50km/h // ~80km/h // 1 ~= 3.85
                    .build();

            vrpBuilder.addVehicle(VehicleImpl.Builder.newInstance(String.format("%s", vehicle.getId()))
                    .setStartLocation(getDepotLocation())
                    .setEndLocation(getDepotLocation())
                    .setEarliestStart(50400) // 14:00
//                    .setLatestArrival(vehicle.getLatestArrival())
                    .setType(type)
                    .build());
        }



        /*
         * Adding Services
         */

        VehicleRoutingTransportCostsMatrix.Builder costMatrixBuilder = VehicleRoutingTransportCostsMatrix.Builder.newInstance(true);

        for (IdentifiedGHPoint3D point : this.pointList) {
//            System.out.println(String.format("#################\n%s\n#################", point));
            if (point.getTimeWindow() != null)
                vrpBuilder.addJob(Delivery.Builder.newInstance(point.getId())
                        .addSizeDimension(0, (int) point.getWeight())
                        .setLocation(Location.Builder.newInstance().setId(point.getId()).setCoordinate(
                                Coordinate.newInstance(point.getLat(), point.getLon())).build()
                        )
                        .setTimeWindow(point.getTimeWindow())
                        .setServiceTime(point.getServiceTime())
                        .build());
        }

        for (PointMatrix pointMatrix : pointMatrixList) {
            costMatrixBuilder.addTransportDistance(pointMatrix.getPoint1().getId(), pointMatrix.getPoint2().getId(), pointMatrix.getDistance());
            costMatrixBuilder.addTransportTime(pointMatrix.getPoint1().getId(), pointMatrix.getPoint2().getId(), pointMatrix.getTime());
//          Add avg speed in km/h aprox
            this.speedAvg.add(pointMatrix.getDistance() / (pointMatrix.getTime()));
        }

        this.vrtcm = costMatrixBuilder.build();
        vrpBuilder.setRoutingCost(this.vrtcm);


        VehicleRoutingProblem vrp = vrpBuilder.build();

//      Test Constraint


//          Time convert in min
//          Distance convert in km
//        StateManager stateManager = new StateManager(vrp);
//        ConstraintManager constraintManager = new ConstraintManager(vrp, stateManager);
//        constraintManager.addConstraint(new FarmyWorkingHoursConstraint(54000 + 28800, stateManager, this.vrtcm), ConstraintManager.Priority.HIGH);

//      End test constraint

        VehicleRoutingAlgorithm vra = Jsprit.Builder.newInstance(vrp)
//                .setStateAndConstraintManager(stateManager, constraintManager)
                .setProperty(Jsprit.Parameter.FAST_REGRET, "true")
                .setProperty(Jsprit.Parameter.THREADS, "8")
                .buildAlgorithm();

        this.reasonTracker = new UnassignedJobReasonTracker();

        vra.addListener(this.reasonTracker);
//        vra.setMaxIterations(64); // Fast iterations for testing

        Collection<VehicleRoutingProblemSolution> solutions = vra.searchSolutions();
        this.solution = Solutions.bestOf(solutions);


//        new Plotter(vrp).plot("./output/main2_problem2.png", "p01");
//        new Plotter(vrp, Solutions.bestOf(solutions)).plot("./output/main2_sol2.png", "po");
//        new GraphStreamViewer(vrp, Solutions.bestOf(solutions)).labelWith(GraphStreamViewer.Label.ID).setRenderDelay(200).display();
//        SolutionPrinter.print(vrp, Solutions.bestOf(solutions), SolutionPrinter.Print.VERBOSE);

        SolutionAnalyser analyser = new SolutionAnalyser(vrp, Solutions.bestOf(solutions), vrp.getTransportCosts());
        System.out.println("tp_distance: " + analyser.getDistance());
        System.out.println("tp_time: " + analyser.getTransportTime());
        System.out.println("waiting: " + analyser.getWaitingTime());
        System.out.println("service: " + analyser.getServiceTime());
        System.out.println("#picks: " + analyser.getNumberOfPickups());
        System.out.println("#deliveries: " + analyser.getNumberOfDeliveries());
        System.out.println("#load_delivered: " + analyser.getLoadDelivered());
        System.out.println("#capacity_violation: " + analyser.getCapacityViolation());
        System.out.println("#time_window_violation: " + analyser.getTimeWindowViolation());
        System.out.println("#number_of_deliveries: " + analyser.getNumberOfDeliveriesAtEnd());
        System.out.println("#unnasigned_jobs: " + this.solution.getUnassignedJobs().size());

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

    private String routeVehicleId(VehicleRoute route, HashMap<String, HashMap<String, Object>> allMap, int routeNumber) {
//          Check if vehicle is already added
        String vehicleId = route.getVehicle().getId().replaceAll("\\s+", "");
        if (allMap.containsKey(String.format("%s#%s", vehicleId, routeNumber))) {
            routeNumber++;
            return routeVehicleId(route, allMap, routeNumber);
        } else {
            return String.format("%s#%s", vehicleId, routeNumber);
        }
    }
}
