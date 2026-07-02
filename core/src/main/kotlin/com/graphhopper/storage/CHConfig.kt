package com.graphhopper.storage

import com.graphhopper.config.Profile
import com.graphhopper.routing.util.TraversalMode
import com.graphhopper.routing.weighting.Weighting

/**
 * Container to hold properties used for CH preparation Specifies all properties of a CH routing profile.
 *
 * @author easbar
 */
class CHConfig(chGraphName: String, val weighting: Weighting, val isEdgeBased: Boolean) {
    /**
     * will be used to store and identify the CH graph data on disk
     */
    private val chGraphName: String

    init {
        Profile.validateProfileName(chGraphName)
        this.chGraphName = chGraphName
    }

    val traversalMode: TraversalMode
        get() = if (isEdgeBased) TraversalMode.EDGE_BASED else TraversalMode.NODE_BASED

    fun toFileName(): String = chGraphName

    override fun toString(): String = chGraphName

    val name: String
        get() = chGraphName

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val chConfig = other as CHConfig
        return name == chConfig.name
    }

    override fun hashCode(): Int = name.hashCode()

    companion object {
        @JvmStatic
        fun nodeBased(chGraphName: String, weighting: Weighting): CHConfig =
            CHConfig(chGraphName, weighting, false)

        @JvmStatic
        fun edgeBased(chGraphName: String, weighting: Weighting): CHConfig =
            CHConfig(chGraphName, weighting, true)
    }
}
