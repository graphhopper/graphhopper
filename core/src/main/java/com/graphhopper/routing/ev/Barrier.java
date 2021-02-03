package com.graphhopper.routing.ev;

public enum Barrier {
    NO("no"), YES("yes"), GATE("gate"), BOLLARD("bollard"), LIFT_GATE("lift_gate"),
    KERB("kerb"), BLOCK("block"), CYCLE_BARRIER("cycle_barrier"), STILE("stile"),
    ENTRANCE("entrance"), CATTLE_GRID("cattle_grid"), TOLL_BOOTH("toll_booth"),
    SWING_GATE("swing_gate"), KISSING_GATE("kissing_gate"), CHAIN("chain"), TURNSTILE("turnstile"),
    FENCE("fence"), BORDER_CONTROL("border_control"), WALL("wall"), SLIDING_GATE("sliding_gate"),
    BUS_TRAP("bus_trap"), MOTORCYCLE_BARRIER("motorcycle_barrier"), SUMP_BUSTER("sump_buster"),
    HANDRAIL("handrail");

    public static final String KEY = "barrier";

    private final String name;

    Barrier(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static Barrier find(String name) {
        if (name == null || name.isEmpty())
            return NO;

        for (Barrier barrier : values()) {
            if (barrier.name().equalsIgnoreCase(name)) {
                return barrier;
            }
        }

        return NO;
    }
}
