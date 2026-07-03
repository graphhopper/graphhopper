package com.graphhopper.routing.ch

interface NodeOrderingProvider {

    fun getNodeIdForLevel(level: Int): Int

    fun getNumNodes(): Int

    companion object {
        @JvmStatic
        fun identity(nodes: Int): NodeOrderingProvider = object : NodeOrderingProvider {
            override fun getNodeIdForLevel(level: Int): Int = level

            override fun getNumNodes(): Int = nodes
        }

        @JvmStatic
        fun fromArray(vararg nodes: Int): NodeOrderingProvider = object : NodeOrderingProvider {
            override fun getNodeIdForLevel(level: Int): Int = nodes[level]

            override fun getNumNodes(): Int = nodes.size
        }
    }
}
