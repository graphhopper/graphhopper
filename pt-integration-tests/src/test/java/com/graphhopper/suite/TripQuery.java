package com.graphhopper.suite;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.LocalDateTime;

@JsonPropertyOrder({ "query_id", "query_date_time", "query_from_id", "query_from_name",
                "query_from_lon", "query_from_lat", "query_to_id", "query_to_name", "query_to_lon",
                "query_to_lat",
                "query_optimization",
                "trip_first_departure_date_time",
                "trip_last_arrival_date_time",
                "trip_legs_count"})
public class TripQuery {

    @JsonProperty("query_id")
    private final String id;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonProperty("query_date_time")
    private final LocalDateTime dateTime;
    @JsonProperty("query_from_id")
    private final String fromId;
    @JsonProperty("query_from_name")
    private final String fromName;
    @JsonProperty("query_from_lon")
    private final double fromLon;
    @JsonProperty("query_from_lat")
    private final double fromLat;
    @JsonProperty("query_to_id")
    private final String toId;
    @JsonProperty("query_to_name")
    private final String toName;
    @JsonProperty("query_to_lon")
    private final double toLon;
    @JsonProperty("query_to_lat")
    private final double toLat;
    @JsonProperty("query_optimization")
    private final Optimization optimization;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonProperty("trip_first_departure_date_time")
    private final LocalDateTime tripFirstDepartureDateTime;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonProperty("trip_last_arrival_date_time")
    private final LocalDateTime tripLastArrivalDateTime;
    @JsonProperty("trip_legs_count")
    private final int tripLegsCount;

    @JsonCreator
    public TripQuery(@JsonProperty("query_id") String id,
                    @JsonProperty("query_date_time") LocalDateTime dateTime,
                    @JsonProperty("query_from_id") String fromId,
                    @JsonProperty("query_from_name") String fromName,
                    @JsonProperty("query_from_lon") double fromLon,
                    @JsonProperty("query_from_lat") double fromLat,
                    @JsonProperty("query_to_id") String toId,
                    @JsonProperty("query_to_name") String toName,
                    @JsonProperty("query_to_lon") double toLon,
                    @JsonProperty("query_to_lat") double toLat,
                    @JsonProperty("query_optimization") Optimization optimization,
                    @JsonProperty("trip_first_departure_date_time") LocalDateTime tripFirstDepartureDateTime,
                    @JsonProperty("trip_last_arrival_date_time") LocalDateTime tripLastArrivalDateTime,
                    @JsonProperty("trip_legs_count") int tripLegsCount) {
        this.id = id;
        this.dateTime = dateTime;
        this.fromId = fromId;
        this.fromName = fromName;
        this.fromLon = fromLon;
        this.fromLat = fromLat;
        this.toId = toId;
        this.toName = toName;
        this.toLon = toLon;
        this.toLat = toLat;
        this.optimization = optimization;
        this.tripFirstDepartureDateTime = tripFirstDepartureDateTime;
        this.tripLastArrivalDateTime = tripLastArrivalDateTime;
        this.tripLegsCount = tripLegsCount;
    }

    public String getId() {
        return id;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public String getFromId() {
        return fromId;
    }

    public String getFromName() {
        return fromName;
    }

    public double getFromLon() {
        return fromLon;
    }

    public double getFromLat() {
        return fromLat;
    }

    public String getToId() {
        return toId;
    }

    public String getToName() {
        return toName;
    }

    public double getToLon() {
        return toLon;
    }

    public double getToLat() {
        return toLat;
    }
    
    public Optimization getOptimization() {
        return optimization;
    }

    public LocalDateTime getTripFirstDepartureDateTime() {
        return tripFirstDepartureDateTime;
    }

    public LocalDateTime getTripLastArrivalDateTime() {
        return tripLastArrivalDateTime;
    }

    public int getTripLegsCount() {
        return tripLegsCount;
    }
}
