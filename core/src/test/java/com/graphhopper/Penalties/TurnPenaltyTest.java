package com.graphhopper.Penalties;

import com.graphhopper.WeightingWithPenalties;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.Test;

import java.util.Random;

import static com.graphhopper.Penalties.TurnPenalty.SHARP_TURN_FACTOR;
import static com.graphhopper.Penalties.TurnPenalty.SLIGHT_TURN_FACTOR;
import static org.junit.Assert.*;

public class TurnPenaltyTest {
    Random random = new Random();
    double penaltyTurnRight = random.nextDouble();
    double penaltyTurnLeft = random.nextDouble();
    double penaltyStraight = random.nextDouble();

    TurnPenalty turnPenalty = new TurnPenalty(penaltyTurnRight, penaltyTurnLeft, penaltyStraight);
    EdgeIteratorState edge = null;
    boolean reverse = false;
    int prevOrNextEdgeId = random.nextInt();

    @Test
    public void calculateSignRight() {
        WeightingWithPenalties.WayData wayDataFrom = new WeightingWithPenalties.WayData(32.116391, 34.817488, 32.118253, 34.818363);
        WeightingWithPenalties.WayData wayDataTo = new WeightingWithPenalties.WayData(32.118266, 34.818477, 32.117267, 34.820775);
        double penalty = turnPenalty.getPenalty(edge, reverse, prevOrNextEdgeId, wayDataFrom, wayDataTo);
        assertEquals(penalty, penaltyTurnRight, 0.01);
    }
    @Test
    public void calculateSignLeft() {
        WeightingWithPenalties.WayData wayDataFrom = new WeightingWithPenalties.WayData(32.118266, 34.818477, 32.117267, 34.820775);
        WeightingWithPenalties.WayData wayDataTo = new WeightingWithPenalties.WayData(32.116391, 34.817488, 32.118253, 34.818363);
        double penalty = turnPenalty.getPenalty(edge, reverse, prevOrNextEdgeId, wayDataFrom, wayDataTo);
        assertEquals(penalty, penaltyTurnLeft, 0.01);
    }
    @Test
    public void calculateSignSlightRight() {
        WeightingWithPenalties.WayData wayDataFrom = new WeightingWithPenalties.WayData(32.116700, 34.817617, 32.117319, 34.817891);
        WeightingWithPenalties.WayData wayDataTo = new WeightingWithPenalties.WayData(32.117342, 34.817945, 32.117731, 34.818230);
        double penalty = turnPenalty.getPenalty(edge, reverse, prevOrNextEdgeId, wayDataFrom, wayDataTo);
        assertEquals(penalty, penaltyTurnRight * SLIGHT_TURN_FACTOR, 0.01);
    }
    @Test
    public void calculateSignSlightLeft() {
        WeightingWithPenalties.WayData wayDataFrom = new WeightingWithPenalties.WayData(32.123799, 34.812758, 32.123837, 34.811917);
        WeightingWithPenalties.WayData wayDataTo = new WeightingWithPenalties.WayData(32.123836, 34.811879, 32.123738, 34.811667);
        double penalty = turnPenalty.getPenalty(edge, reverse, prevOrNextEdgeId, wayDataFrom, wayDataTo);
        assertEquals(penalty, penaltyTurnLeft * SLIGHT_TURN_FACTOR, 0.01);
    }
    @Test
    public void calculateSignSharpRight() {
        WeightingWithPenalties.WayData wayDataFrom = new WeightingWithPenalties.WayData(32.124639, 34.812672, 32.124713, 34.812428);
        WeightingWithPenalties.WayData wayDataTo = new WeightingWithPenalties.WayData(32.124755, 34.812483, 32.124728, 34.813218);
        double penalty = turnPenalty.getPenalty(edge, reverse, prevOrNextEdgeId, wayDataFrom, wayDataTo);
        assertEquals(penalty, penaltyTurnRight * SHARP_TURN_FACTOR, 0.01);
    }
    @Test
    public void calculateSignSharpLeft() {
        WeightingWithPenalties.WayData wayDataFrom = new WeightingWithPenalties.WayData(32.124728, 34.813218, 32.124755, 34.812483);
        WeightingWithPenalties.WayData wayDataTo = new WeightingWithPenalties.WayData(32.124713, 34.812428, 32.124639, 34.812672);
        double penalty = turnPenalty.getPenalty(edge, reverse, prevOrNextEdgeId, wayDataFrom, wayDataTo);
        assertEquals(penalty, penaltyTurnLeft * SHARP_TURN_FACTOR, 0.01);
    }
}