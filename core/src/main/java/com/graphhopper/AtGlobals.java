package com.graphhopper;

import java.io.FileReader;
import java.util.HashMap;

import au.com.bytecode.opencsv.CSVReader;

/**
 * Crude hack to allow custom weights to be loaded from a CSV into a HashMap
 * so they may be merged-in during an OSM import
 */
public class AtGlobals {
    public static HashMap<Long, Double> popularities = new HashMap<Long, Double>();
    public static HashMap<Long, Double> scenicValues = new HashMap<Long, Double>();

    public static void loadAllTrailsCsv(String filename) {
      try {
          CSVReader reader = new CSVReader(new FileReader(filename));
          String [] nextLine;
          while ((nextLine = reader.readNext()) != null) {
              if ("osm_id".equals(nextLine[0]))
                  continue;
              try {
                  // Long wayId = Long.valueOf(nextLine[0]);
                  Long wayId = Double.valueOf(nextLine[0]).longValue();
                  Double popularity = Double.valueOf(nextLine[1]);
                  Double scenic_value = Double.valueOf(nextLine[2]);
                  if (popularity > 0.0)
                    popularities.put(wayId, popularity);
                  if (scenic_value > 0.0)
                    scenicValues.put(wayId, scenic_value);
              } catch (Exception ex) {
                  System.out.println("CSV parse exception: " + ex.getMessage());
              }
          }
          reader.close();
      } catch (Exception ex) {
          System.out.println("CSV read exception: " + ex.getMessage());
      }
  }
}