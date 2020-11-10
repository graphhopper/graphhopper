package com.graphhopper.farmy;

import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TimeWindow;
import com.graphhopper.util.shapes.GHPoint;

import java.io.*;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.LocalTime;
import java.util.Locale;
import java.util.regex.Pattern;

public class RoutePlanReader {

    public IdentifiedPointList identifiedPointList;

    public RoutePlanReader(String filePath) throws IOException, ParseException {
        BufferedReader csvReader = new BufferedReader(new FileReader(filePath));
        this.identifiedPointList = new IdentifiedPointList();
        String row;
        while ((row = csvReader.readLine()) != null) {
//            Check if row is a order
            if (!Pattern.compile("R[0-9](.*)").matcher(row).matches()) continue;
            String[] data = row.split(";");
//            Order Number
            if(data[0] == null || data[0].isEmpty() || data[0].equals("\"\"")) continue;
//            Latitude
            if(data[11] == null || data[11].isEmpty() || data[11].equals("\"\"")) continue;
//            Longitude
            if(data[12] == null || data[12].isEmpty() || data[12].equals("\"\"")) continue;
//            Direction
            if(data[13] == null || data[13].isEmpty() || data[13].equals("\"\"")) continue;
//            Start time window
            if(data[14] == null || data[14].isEmpty() || data[14].equals("\"\"")) continue;
//            End time window
            if(data[15] == null || data[15].isEmpty() || data[15].equals("\"\"")) continue;
//            Operating time
            if(data[16] == null || data[16].isEmpty() || data[16].equals("\"\"")) continue;
            NumberFormat nf = NumberFormat.getInstance(Locale.GERMAN);
            identifiedPointList.add(new IdentifiedGHPoint3D(new GHPoint(nf.parse(data[11]).doubleValue(), nf.parse(data[12]).doubleValue()), data[0])
                    .setServiceTime(Integer.parseInt(data[16]))
                    .setTimeWindow(LocalTime.parse(data[14]).toSecondOfDay(), LocalTime.parse(data[15]).toSecondOfDay())
                    .setDirection(data[13])
            );
        }

    }

    public RoutePlanReader(InputStream fileStream) throws IOException, ParseException {
        BufferedReader csvReader = new BufferedReader(new InputStreamReader(fileStream));
        this.identifiedPointList = new IdentifiedPointList();
        String row;
        while ((row = csvReader.readLine()) != null) {
//            Check if row is a order
            if (!Pattern.compile("R[0-9](.*)").matcher(row).matches()) continue;
            String[] data = row.split(";");
//            Order Number
            if(data[0] == null || data[0].isEmpty() || data[0].equals("\"\"")) continue;
//            Weight
            if(data[10] == null || data[10].isEmpty() || data[10].equals("\"\"")) continue;
//            Latitude
            if(data[11] == null || data[11].isEmpty() || data[11].equals("\"\"")) continue;
//            Longitude
            if(data[12] == null || data[12].isEmpty() || data[12].equals("\"\"")) continue;
//            Direction
            if(data[13] == null || data[13].isEmpty() || data[13].equals("\"\"")) continue;
//            Start time window
            if(data[14] == null || data[14].isEmpty() || data[14].equals("\"\"")) continue;
//            End time window
            if(data[15] == null || data[15].isEmpty() || data[15].equals("\"\"")) continue;
//            Operating time
            if(data[16] == null || data[16].isEmpty() || data[16].equals("\"\"")) continue;
            NumberFormat nf = NumberFormat.getInstance(Locale.GERMAN);
            identifiedPointList.add(new IdentifiedGHPoint3D(new GHPoint(nf.parse(data[11]).doubleValue(), nf.parse(data[12]).doubleValue()), data[0])
                    .setWeight(Double.parseDouble(data[10]))
                    .setServiceTime(Integer.parseInt(data[16]))
                    .setTimeWindow(LocalTime.parse(data[14]).toSecondOfDay(), LocalTime.parse(data[15]).toSecondOfDay())
                    .setDirection(data[13])
            );
        }

    }

    public RoutePlanReader(FarmyOrder[] farmyOrders) throws IOException, ParseException {
        this.identifiedPointList = new IdentifiedPointList();
//        if(this.identifiedPointList.findDepot().getId().equals("NOT_FOUND_POINT"))
//            identifiedPointList.add(depotPoint());
        for (FarmyOrder farmyOrder : farmyOrders) {
//            System.out.println(farmyOrder);
            if (farmyOrder.latitude != null && farmyOrder.longitude != null && identifiedPointList.find(farmyOrder.getNumber()).getId().equals("NOT_FOUND_POINT")) {
                identifiedPointList.add(new IdentifiedGHPoint3D(new GHPoint(farmyOrder.latitude, farmyOrder.longitude), farmyOrder.number)
                        .setWeight(farmyOrder.weight)
                        .setServiceTime(farmyOrder.serviceTime)
                        .setTimeWindow(farmyOrder.timeWindow)
                        .setDirection(farmyOrder.direction));
            }
        }
    }

    public IdentifiedPointList getIdentifiedPointList() {
        return identifiedPointList;
    }

    public IdentifiedGHPoint3D depotPoint() {
        IdentifiedGHPoint3D identifiedGHPoint3D = new IdentifiedGHPoint3D(new GHPoint(47.3822499, 8.4857342), "Depot");
        identifiedGHPoint3D.setWeight(0);
        identifiedGHPoint3D.setServiceTime(1200);
        identifiedGHPoint3D.setTimeWindow(TimeWindow.newInstance(50400, 54000)); // Test timewindow, between 14:00 and 15:00

        return identifiedGHPoint3D;
    }

}
