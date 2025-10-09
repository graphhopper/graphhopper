package com.graphhopper.util;

import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DistanceCalculationHelperTest {

    private DistanceCalculationHelper helper;

    @BeforeEach
    void setUp() {
        // Create helper with null dependencies for testing standalone methods
        helper = new DistanceCalculationHelper(null, null);
    }

    @Test
    void testCalculatePointDistance_SamePoint() {
        // Given
        double lat = 52.5200;
        double lon = 13.4050;

        // When
        double distance = helper.calculatePointDistance(lat, lon, lat, lon);

        // Then
        assertEquals(0.0, distance, 0.001, "Distance between same points should be 0");
    }

    @Test
    void testCalculatePointDistance_BerlinToMunich() {
        // Given - Berlin to Munich (approximately 504 km)
        double berlinLat = 52.5200;
        double berlinLon = 13.4050;
        double munichLat = 48.1351;
        double munichLon = 11.5820;

        // When
        double distance = helper.calculatePointDistance(berlinLat, berlinLon, munichLat, munichLon);

        // Then
        assertTrue(distance > 500000 && distance < 600000,
                  "Distance between Berlin and Munich should be approximately 504 km, got: " + distance);
    }

    @Test
    void testCalculatePointDistance_SmallDistance() {
        // Given - Two points about 1 km apart
        double lat1 = 52.5200;
        double lon1 = 13.4050;
        double lat2 = 52.5290; // About 1 km north
        double lon2 = 13.4050;

        // When
        double distance = helper.calculatePointDistance(lat1, lon1, lat2, lon2);

        // Then
        assertTrue(distance > 900 && distance < 1100,
                  "Distance should be approximately 1000m, got: " + distance);
    }

    @Test
    void testCalculatePointDistance_NegativeCoordinates() {
        // Given - Test with negative coordinates (Southern hemisphere, Western longitude)
        double lat1 = -33.8688; // Sydney
        double lon1 = 151.2093;
        double lat2 = -37.8136; // Melbourne
        double lon2 = 144.9631;

        // When
        double distance = helper.calculatePointDistance(lat1, lon1, lat2, lon2);

        // Then
        assertTrue(distance > 700000 && distance < 900000,
                  "Distance between Sydney and Melbourne should be approximately 700-900 km, got: " + distance);
    }

    @Test
    void testCalculateDistanceToLineSegment_PointOnSegment() {
        // Given - Point lies exactly on the line segment (midpoint)
        double segStartLat = 52.5100;
        double segStartLon = 13.4000;
        double segEndLat = 52.5300;
        double segEndLon = 13.4100;
        double pointLat = (segStartLat + segEndLat) / 2;
        double pointLon = (segStartLon + segEndLon) / 2;

        // When
        double distance = helper.calculateDistanceToLineSegment(
            pointLat, pointLon, segStartLat, segStartLon, segEndLat, segEndLon);

        // Then
        assertTrue(distance < 50, "Point on segment should have very small distance, got: " + distance);
    }

    @Test
    void testCalculateDistanceToLineSegment_PerpendicularCase() {
        // Given - Point perpendicular to a vertical line segment
        double segStartLat = 52.5100;
        double segStartLon = 13.4050;
        double segEndLat = 52.5300;
        double segEndLon = 13.4050; // Vertical line
        double pointLat = 52.5200;   // Midpoint latitude
        double pointLon = 13.4100;   // 50m east

        // When
        double distance = helper.calculateDistanceToLineSegment(
            pointLat, pointLon, segStartLat, segStartLon, segEndLat, segEndLon);

        // Then
        assertTrue(distance > 300 && distance < 600,
                  "Perpendicular distance should be reasonable, got: " + distance);
    }

    @Test
    void testCalculateDistanceToLineSegment_ZeroLengthSegment() {
        // Given - Segment with zero length (start and end are the same)
        double pointLat = 52.5200;
        double pointLon = 13.4050;
        double segLat = 52.5100;
        double segLon = 13.4000;

        // When
        double distance = helper.calculateDistanceToLineSegment(
            pointLat, pointLon, segLat, segLon, segLat, segLon);

        // Then
        double expectedDistance = helper.calculatePointDistance(pointLat, pointLon, segLat, segLon);
        assertEquals(expectedDistance, distance, 0.001,
                    "Zero-length segment should return point-to-point distance");
    }

    @Test
    void testCalculateDistanceToLineSegment_ProjectionBeyondSegmentEnd() {
        // Given - Point where perpendicular projection falls beyond segment end
        double segStartLat = 52.5100;
        double segStartLon = 13.4050;
        double segEndLat = 52.5200;
        double segEndLon = 13.4050;
        double pointLat = 52.5400; // Far beyond end
        double pointLon = 13.4050;

        // When
        double distance = helper.calculateDistanceToLineSegment(
            pointLat, pointLon, segStartLat, segStartLon, segEndLat, segEndLon);

        // Then
        double distanceToEnd = helper.calculatePointDistance(pointLat, pointLon, segEndLat, segEndLon);
        assertEquals(distanceToEnd, distance, 0.001,
                    "Should return distance to nearest endpoint when projection is beyond segment");
    }

    @Test
    void testCalculateDistanceToLineSegment_ProjectionBeforeSegmentStart() {
        // Given - Point where perpendicular projection falls before segment start
        double segStartLat = 52.5100;
        double segStartLon = 13.4050;
        double segEndLat = 52.5200;
        double segEndLon = 13.4050;
        double pointLat = 52.5000; // Before start
        double pointLon = 13.4050;

        // When
        double distance = helper.calculateDistanceToLineSegment(
            pointLat, pointLon, segStartLat, segStartLon, segEndLat, segEndLon);

        // Then
        double distanceToStart = helper.calculatePointDistance(pointLat, pointLon, segStartLat, segStartLon);
        assertEquals(distanceToStart, distance, 0.001,
                    "Should return distance to start point when projection is before segment");
    }

    @Test
    void testCalculateDistanceToLineSegment_DiagonalSegment() {
        // Given - Diagonal line segment and point to the side
        double segStartLat = 52.5100;
        double segStartLon = 13.4000;
        double segEndLat = 52.5200;
        double segEndLon = 13.4100;
        double pointLat = 52.5150;
        double pointLon = 13.3950; // To the left of the segment

        // When
        double distance = helper.calculateDistanceToLineSegment(
            pointLat, pointLon, segStartLat, segStartLon, segEndLat, segEndLon);

        // Then
        assertNotEquals(0.0, distance, "Distance should not be zero for point not on segment");
        assertTrue(distance > 0, "Distance should be positive");
        assertTrue(distance < 10000, "Distance should be reasonable for nearby point");
    }

    @Test
    void testCalculateDistanceToLineSegment_VerySmallSegment() {
        // Given - Very small segment (1 meter)
        double segStartLat = 52.5200;
        double segStartLon = 13.4050;
        double segEndLat = 52.5200090; // About 1 meter north
        double segEndLon = 13.4050;
        double pointLat = 52.5200045; // Midpoint
        double pointLon = 13.4051; // About 7 meters east (0.0001° at this latitude)

        // When
        double distance = helper.calculateDistanceToLineSegment(
            pointLat, pointLon, segStartLat, segStartLon, segEndLat, segEndLon);

        // Then
        assertTrue(distance > 6 && distance < 8,
                  "Distance to small segment should be approximately 7m, got: " + distance);
    }

    @Test
    void testCalculateDistanceToLineSegment_EdgeCases() {
        // Given - Test with very close coordinates
        double segStartLat = 52.5200000;
        double segStartLon = 13.4050000;
        double segEndLat = 52.5200001;
        double segEndLon = 13.4050001;
        double pointLat = 52.5200000;
        double pointLon = 13.4050000;

        // When
        double distance = helper.calculateDistanceToLineSegment(
            pointLat, pointLon, segStartLat, segStartLon, segEndLat, segEndLon);

        // Then
        assertTrue(distance < 1.0, "Distance for very close coordinates should be minimal, got: " + distance);
    }

    @Test
    void testConstructor() {
        // Given
        Graph graph = null;
        NodeAccess nodeAccess = null;

        // When
        DistanceCalculationHelper newHelper = new DistanceCalculationHelper(graph, nodeAccess);

        // Then
        assertNotNull(newHelper, "Constructor should create valid instance");
    }

    @Test
    void testCalculatePointDistance_CrossEquator() {
        // Given - Points on different sides of the equator
        double lat1 = 10.0;  // North
        double lon1 = 0.0;
        double lat2 = -10.0; // South
        double lon2 = 0.0;

        // When
        double distance = helper.calculatePointDistance(lat1, lon1, lat2, lon2);

        // Then
        assertTrue(distance > 2000000 && distance < 2500000,
                  "Distance across equator should be approximately 2200 km, got: " + distance);
    }

    @Test
    void testCalculatePointDistance_LongitudinalDistance() {
        // Given - Points at same latitude but different longitudes
        double lat = 0.0; // Equator for maximum longitude effect
        double lon1 = 0.0;
        double lon2 = 90.0; // Quarter of the way around the earth

        // When
        double distance = helper.calculatePointDistance(lat, lon1, lat, lon2);

        // Then
        assertTrue(distance > 9000000 && distance < 11000000,
                  "Quarter earth distance should be approximately 10000 km, got: " + distance);
    }

    @Test
    void testCalculateDistanceToLineSegment_NearZeroLength() {
        // Given - Segment with nearly zero length but not exactly zero
        double segStartLat = 52.5200;
        double segStartLon = 13.4050;
        double segEndLat = 52.5200 + 1e-12; // Very small difference
        double segEndLon = 13.4050 + 1e-12;
        double pointLat = 52.5201;
        double pointLon = 13.4051;

        // When
        double distance = helper.calculateDistanceToLineSegment(
            pointLat, pointLon, segStartLat, segStartLon, segEndLat, segEndLon);

        // Then
        double expectedDistance = helper.calculatePointDistance(pointLat, pointLon, segStartLat, segStartLon);
        assertTrue(Math.abs(distance - expectedDistance) < 1.0,
                  "Near-zero length segment should behave like point distance");
    }
}
