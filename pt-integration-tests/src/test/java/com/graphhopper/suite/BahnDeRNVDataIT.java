package com.graphhopper.suite;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.PathWrapper;
import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.util.Helper;
import com.graphhopper.util.StopWatch;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.RectangleEdge;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.Assert.assertFalse;

public class BahnDeRNVDataIT {

    private static final String GRAPH_LOC = "target/graphhopperIT-rnv-gtfs";
    private static GraphHopperGtfs graphHopper;

    @BeforeClass
    public static void init() {
        Helper.removeDir(new File(GRAPH_LOC));
        graphHopper = GraphHopperGtfs.createGraphHopperGtfs(GRAPH_LOC, "files/rnv.zip", true);
    }

    @AfterClass
    public static void tearDown() {
//        if (graphHopper != null)
//            graphHopper.close();
    }

    @Test
    public void checkTripQueries() throws IOException {
        final TripQueryCsvReader reader = new TripQueryCsvReader();
        final File tripQueriesFile = new File("files/rnv-trips-least-duration-01.csv");
        final List<TripQuery> tripQueries = reader.read(tripQueriesFile);
        XYSeries travelTimes = new XYSeries("Travel times", false, true);
        StopWatch stopWatch = new StopWatch().start();
        for (TripQuery tripQuery : tripQueries) {
            GHRequest ghRequest = new GHRequest(tripQuery.getFromLat(), tripQuery.getFromLon(),
                            tripQuery.getToLat(), tripQuery.getToLon());
            ghRequest.getHints().put(GraphHopperGtfs.IGNORE_TRANSFERS, true);
            ghRequest.getHints().put(GraphHopperGtfs.EARLIEST_DEPARTURE_TIME_HINT,
                    tripQuery.getDateTime().toString());
            GHResponse route = graphHopper.route(ghRequest);
            assertFalse(route.hasErrors());
            System.out.println(MessageFormat.format("Routing from {0} to {1} at {2} (trip query id {3}).",
                            tripQuery.getFromName(), tripQuery.getToName(),
                            tripQuery.getDateTime(), tripQuery.getId()));
            final LocalDateTime expectedArrivalDateTime = tripQuery.getTripLastArrivalDateTime();
            System.out.println(MessageFormat.format("Expected arrival: {0}.", expectedArrivalDateTime));
            if (!route.getAll().isEmpty()) {
                final LocalDateTime actualArrivalDateTime = tripQuery.getDateTime().plus(route.getBest().getTime(), ChronoUnit.MILLIS);
                System.out.println(MessageFormat.format("Actual arrival: {0}", actualArrivalDateTime));
                Duration expectedTravelTime = Duration.between(tripQuery.getDateTime(), expectedArrivalDateTime);
                travelTimes.add(new MyXYDataItem(expectedTravelTime.getSeconds(), route.getBest().getTime() / 1000, route.getBest()));
            } else {
                Assert.fail("No route found.");
            }
        }
        stopWatch.stop();

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(travelTimes);
        JFreeChart scatterPlot = createChart(dataset, tripQueries.size(), stopWatch.getTime());
        ChartUtilities.saveChartAsPNG(new File(GRAPH_LOC + "/traveltimes.png"), scatterPlot, 600, 600);
    }

    private JFreeChart createChart(XYSeriesCollection dataset, int nQueries, long time) {
        final double lower = Math.min(dataset.getRangeLowerBound(false), dataset.getDomainLowerBound(false));
        final double upper = Math.max(dataset.getRangeUpperBound(false), dataset.getDomainUpperBound(false));
        NumberAxis xAxis = new LogarithmicAxis("bahn.de");
        xAxis.setRange(lower, upper);
        NumberAxis yAxis = new LogarithmicAxis("GraphHopper");
        yAxis.setRange(lower, upper);
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(false, true) {
            @Override
            public Paint getItemPaint(int row, int column) {
                if (((MyXYDataItem) dataset.getSeries(0).getDataItem(column)).bestRoute.getNumChanges() == -1) {
                    return Color.RED;
                } else {
                    return Color.BLACK;
                }
            }
        };
        XYPlot plot = new XYPlot(dataset, xAxis, yAxis, renderer);
        plot.setOrientation(PlotOrientation.VERTICAL);
        JFreeChart chart = new JFreeChart("Travel time [s]", JFreeChart.DEFAULT_TITLE_FONT, plot, false);
        TextTitle legendText = new TextTitle(String.format("n: %d\truntime: %ds", nQueries, time /1000));
        legendText.setPosition(RectangleEdge.BOTTOM);
        chart.addSubtitle(legendText);
        return chart;
    }

    private static class MyXYDataItem extends XYDataItem {
        final PathWrapper bestRoute;

        MyXYDataItem(long seconds, long actualTravelTimeSeconds, PathWrapper bestRoute) {
            super(seconds, actualTravelTimeSeconds);
            this.bestRoute = bestRoute;
        }
    }

}
