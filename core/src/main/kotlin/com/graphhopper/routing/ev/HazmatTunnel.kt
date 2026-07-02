package com.graphhopper.routing.ev

/**
 * Defines the degree of restriction for the transport of hazardous goods through tunnels.<br></br>
 * If not tagged it will be [A]
 *
 * @see <a href="https://wiki.openstreetmap.org/wiki/Key:hazmat#Tunnel_restrictions">Hazmat Tunnel restrictions</a>
 */
enum class HazmatTunnel {
    /**
     * driving with any dangerous goods allowed
     */
    A,

    /**
     * no goods with very large explosion range
     */
    B,

    /**
     * no goods with large explosion or poisoning range
     */
    C,

    /**
     * no goods which threaten a large explosion, poisoning or fire
     */
    D,

    /**
     * forbids all dangerous goods except: UN 2919,3291, 3331, 3359, 3373
     */
    E;

    // for backward compatibility: no custom toString()

    companion object {
        const val KEY = "hazmat_tunnel"

        @JvmStatic
        fun create(): EnumEncodedValue<HazmatTunnel> = EnumEncodedValue(KEY, HazmatTunnel::class.java)
    }
}
