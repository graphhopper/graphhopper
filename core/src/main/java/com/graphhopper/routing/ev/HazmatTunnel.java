package com.graphhopper.routing.ev;

/**
 * Defines the degree of restriction for the transport of hazardous goods through tunnels.<br>
 * If not tagged it will be {@link #A}
 * 
 * @see <a href="https://wiki.openstreetmap.org/wiki/Key:hazmat#Tunnel_restrictions">Hazmat Tunnel restrictions</a>
 */
public enum HazmatTunnel {
    /** driving with any dangerous goods allowed */
    A("A"),
    /** no goods with very large explosion range */
    B("B"),
    /** no goods with large explosion or poisoning range */
    C("C"),
    /** no goods which threaten a large explosion, poisoning or fire */
    D("D"),
    /** forbids all dangerous goods except: UN 2919,3291, 3331, 3359, 3373 */
    E("E");

    public static final String KEY = "hazmat_tunnel";

    private final String name;

    HazmatTunnel(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
