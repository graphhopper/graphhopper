package com.graphhopper.suite;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.util.Helper;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.urls.StandardXYURLGenerator;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.assertFalse;

public class GraphHopperRnvGtfsSuiteIT {

    private static final String GRAPH_LOC = "target/graphhopperIT-rnv-gtfs";
    private static GraphHopperGtfs graphHopper;
    public static final LocalDateTime GTFS_START_DATE = LocalDate.of(2016, 9, 19).atStartOfDay();

    @BeforeClass
    public static void init() {
        Helper.removeDir(new File(GRAPH_LOC));

        graphHopper = new GraphHopperGtfs();
        graphHopper.setGtfsFile("files/rnv.zip");
        graphHopper.setCreateWalkNetwork(true);
        graphHopper.setGraphHopperLocation(GRAPH_LOC);
        graphHopper.importOrLoad();
    }

    @AfterClass
    public static void tearDown() {
        if (graphHopper != null)
            graphHopper.close();
    }

    @Test
    public void checkTripQueries() throws IOException {
        final TripQueryCsvReader reader = new TripQueryCsvReader();
        final File tripQueriesFile = new File("files/rnv-trips-least-duration-01.csv");
        final List<TripQuery> tripQueries = reader.read(tripQueriesFile);
        XYSeries travelTimes = new XYSeries("Travel times", false, true);
        for (TripQuery tripQuery : tripQueries) {
            LocalDateTime departureTime = tripQuery.getDateTime();
            long earliestDepartureTime = Duration.between(GTFS_START_DATE, departureTime).getSeconds();
            GHRequest ghRequest = new GHRequest(tripQuery.getFromLat(), tripQuery.getFromLon(),
                            tripQuery.getToLat(), tripQuery.getToLon());
            ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT,
                    earliestDepartureTime);
            GHResponse route = graphHopper.route(ghRequest);
            assertFalse(route.hasErrors());
            System.out.println(MessageFormat.format("Routing from {0} to {1} at {2} (trip query id {3}).",
                            tripQuery.getFromName(), tripQuery.getToName(),
                            tripQuery.getDateTime(), tripQuery.getId()));
            final LocalDateTime expectedArrivalDateTime = tripQuery.getTripLastArrivalDateTime();
            System.out.println(MessageFormat.format("Expected arrival: {0}.", expectedArrivalDateTime));
            if (!route.getAll().isEmpty()) {
                final LocalDateTime actualArrivalDateTime = GTFS_START_DATE.plusSeconds(Math.round(route.getBest().getRouteWeight()));
                System.out.println(MessageFormat.format("Actual arrival: {0}", actualArrivalDateTime));
                Duration expectedTravelTime = Duration.between(tripQuery.getDateTime(), expectedArrivalDateTime);
                long actualTravelTimeSeconds = (long) route.getBest().getRouteWeight() - earliestDepartureTime;
                travelTimes.add(expectedTravelTime.getSeconds(), actualTravelTimeSeconds);
            } else {
                System.out.println("Path not found.");
            }
        }
        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(travelTimes);
        JFreeChart scatterPlot = createChart(dataset);
        ChartUtilities.saveChartAsPNG(new File(GRAPH_LOC + "/traveltimes.png"), scatterPlot, 600, 600);
    }

    private JFreeChart createChart(XYSeriesCollection dataset) {
        NumberAxis xAxis = new LogarithmicAxis("Web site");
        xAxis.setRange(Math.min(dataset.getRangeLowerBound(false), dataset.getDomainLowerBound(false)), Math.max(dataset.getRangeUpperBound(false), dataset.getDomainUpperBound(false)));
        NumberAxis yAxis = new LogarithmicAxis("GraphHopper");
        yAxis.setRange(Math.min(dataset.getRangeLowerBound(false), dataset.getDomainLowerBound(false)), Math.max(dataset.getRangeUpperBound(false), dataset.getDomainUpperBound(false)));
        XYPlot plot = new XYPlot(dataset, xAxis, yAxis, (XYItemRenderer)null);
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(false, true);
        plot.setRenderer(renderer);
        plot.setOrientation(PlotOrientation.VERTICAL);
        JFreeChart chart = new JFreeChart("Travel time [s]", JFreeChart.DEFAULT_TITLE_FONT, plot, true);
        return chart;
    }
}
