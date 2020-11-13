package com.graphhopper.farmy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.analysis.SolutionAnalyser;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.job.Delivery;
import com.graphhopper.jsprit.core.problem.job.Job;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.DeliverService;
import com.graphhopper.jsprit.core.problem.solution.route.activity.PickupService;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleType;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.util.Coordinate;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.jsprit.core.util.UnassignedJobReasonTracker;
import com.graphhopper.jsprit.core.util.VehicleRoutingTransportCostsMatrix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BaseRouteOptimizer {

    private VehicleRoutingProblemSolution solution;
    private IdentifiedPointList pointList;
    private final GraphHopperAPI graphHopper;
    private final IdentifiedGHPoint3D depotPoint;
    private UnassignedJobReasonTracker reasonTracker;
    private SolutionAnalyser analyser;
    private VehicleRoutingTransportCostsMatrix vrtcm;
    private final List<Double> speedAvg = new ArrayList<>();

    public BaseRouteOptimizer(GraphHopperAPI graphHopper, FarmyOrder[] farmyOrders, FarmyVehicle[] farmyVehicles, IdentifiedGHPoint3D depotPoint) throws Exception {
        this.graphHopper = graphHopper;
        RoutePlanReader routePlanReader = new RoutePlanReader(farmyOrders);
        this.depotPoint = depotPoint;
        build(routePlanReader.getIdentifiedPointList(), farmyVehicles);
    }

    public JsonObject getOptimizedRoutes() {
        JsonObject allMap = new JsonObject();

        JsonObject optimizedRoutesMap = new JsonObject();

        for (VehicleRoute route : solution.getRoutes()) {
            JsonObject vehicleHashMap = new JsonObject();
            IdentifiedGHPoint3D firstPoint = null;
            IdentifiedGHPoint3D lastPoint = null;
            JsonArray waypoints = new JsonArray();

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
                idPoint.setEarliestOperationStartTime(activity.getTheoreticalEarliestOperationStartTime());
                idPoint.setLatestOperationStartTime(activity.getTheoreticalLatestOperationStartTime());
                waypoints.add(idPoint.toJsonObject()); // add the point to waypoints
//              Calc for distance

                if (lastPoint != null) {
                    distance = this.vrtcm.getDistance(idPoint.getId(), lastPoint.getId());
                    idPoint.setDistance(distance);

                    System.out.printf("Point: %s \n Distance: %s \n Time: %s%n", idPoint.getId(), distance, activity.getArrTime());
                }
                if (firstPoint == null) firstPoint = idPoint;
                lastPoint = idPoint;

            }


//          allMap.get(route.getVehicle().getId()).put(allMap.get(route.getVehicle().getId()).get("waypoints"), waypoints);
            vehicleHashMap.add("waypoints", waypoints);

            vehicleHashMap.addProperty("serviceTime", this.analyser.getServiceTime(route));
            vehicleHashMap.addProperty("distance", this.analyser.getDistance(route));
            vehicleHashMap.addProperty("waitingTime", this.analyser.getWaitingTime(route));
            vehicleHashMap.addProperty("transportTime", this.analyser.getTransportTime(route));
            vehicleHashMap.addProperty("cost", this.analyser.getVariableTransportCosts(route) + this.analyser.getFixedCosts(route));

            optimizedRoutesMap.add(route.getVehicle().getId(), vehicleHashMap);
        }

        allMap.add("OptimizedRoutes", optimizedRoutesMap);

        JsonObject frequencyHashMap= new JsonObject();
        this.reasonTracker.getFailedConstraintNamesFrequencyMapping().entrySet().stream()
                .filter(o -> this.solution.getUnassignedJobs().stream().anyMatch(d -> d.getId().equals(o.getKey())))
                .forEach(d -> frequencyHashMap.add(d.getKey(), JsonParser.parseString(d.getValue().getMode().toString())));

        allMap.add("UnassignedJobs", frequencyHashMap);

        JsonObject routeInfoHashMap= new JsonObject();
        routeInfoHashMap.addProperty("serviceTime", this.analyser.getServiceTime());
        routeInfoHashMap.addProperty("totalCosts", this.analyser.getTotalCosts());
        routeInfoHashMap.addProperty("distance", this.analyser.getDistance());
        routeInfoHashMap.addProperty("waitingTime", this.analyser.getWaitingTime());
        routeInfoHashMap.addProperty("transportTime", this.analyser.getTransportTime());
        allMap.add("RouteInfo", routeInfoHashMap);

        return allMap;
    }

    public VehicleRoutingProblemSolution getSolution() {
        return solution;
    }

    public Location getDepotLocation() {
        return Location.Builder.newInstance().setId(this.depotPoint.getId()).setCoordinate(this.depotPoint.getLocation().getCoordinate()).build();
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

        PointMatrixList pointMatrixList = new PointMatrixList(graphHopper, this.pointList, this.depotPoint);

        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance()
                .setFleetSize(VehicleRoutingProblem.FleetSize.FINITE);
        /*
         * Adding Vehicles
         */


        for (FarmyVehicle vehicle : farmyVehicles) {
            vrpBuilder.addVehicle(this.buildVehicle(vehicle));
            System.out.println("isReturnToDepot: " + vehicle.isReturnToDepot());
        }



        /*
         * Adding Services
         */

        VehicleRoutingTransportCostsMatrix.Builder costMatrixBuilder = VehicleRoutingTransportCostsMatrix.Builder.newInstance(true);

        for (IdentifiedGHPoint3D point : this.pointList) {
            if (point.getTimeWindow() != null)
                vrpBuilder.addJob(this.buildService(point));
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
        vra.setMaxIterations(64); // Fast iterations for testing

        Collection<VehicleRoutingProblemSolution> solutions = vra.searchSolutions();
        this.solution = Solutions.bestOf(solutions);


//        new Plotter(vrp).plot("./output/main2_problem2.png", "p01");
//        new Plotter(vrp, Solutions.bestOf(solutions)).plot("./output/main2_sol2.png", "po");
//        new GraphStreamViewer(vrp, Solutions.bestOf(solutions)).labelWith(GraphStreamViewer.Label.ID).setRenderDelay(200).display();
//        SolutionPrinter.print(vrp, Solutions.bestOf(solutions), SolutionPrinter.Print.VERBOSE);

        this.analyser = new SolutionAnalyser(vrp, Solutions.bestOf(solutions), vrp.getTransportCosts());
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

    }

    private VehicleImpl buildVehicle(FarmyVehicle vehicle) {
        VehicleType type = VehicleTypeImpl.Builder.newInstance(String.format("[TYPE] #%s", vehicle.getId()))
                .setFixedCost(vehicle.getFixedCosts()) //Fixe Bedienzeit
                .setCostPerDistance(vehicle.getCostsPerDistance())
                .setCostPerTransportTime(vehicle.getCostsPerTransportTime())
                .setCostPerServiceTime(vehicle.getCostsPerServiceTime())
                .setCostPerWaitingTime(vehicle.getCostPerWaitingTime())
                .addCapacityDimension(0, vehicle.getCapacity())
                .setMaxVelocity(vehicle.isPlus() ? 50.0/3.6 : 80.0/3.6) // ~50km/h // ~80km/h // 1 ~= 3.85
                .build();

        return VehicleImpl.Builder.newInstance(String.format("%s", vehicle.getId()))
                .setStartLocation(this.getDepotLocation())
                .setEndLocation(this.getDepotLocation())
                .setReturnToDepot(vehicle.isReturnToDepot())
                .setEarliestStart(vehicle.getEarliestDeparture()) // 14:00
                .setLatestArrival(vehicle.getLatestArrival())
                .setType(type)
                .build();
    }
    private Service buildService(IdentifiedGHPoint3D point) {
        return Delivery.Builder.newInstance(point.getId())
                .addSizeDimension(0, (int) point.getWeight())
                .setLocation(Location.Builder.newInstance().setId(point.getId()).setCoordinate(
                        Coordinate.newInstance(point.getLat(), point.getLon())).build()
                )
                .setTimeWindow(point.getTimeWindow())
                .setServiceTime(point.getServiceTime())
                .build();
    }

}
