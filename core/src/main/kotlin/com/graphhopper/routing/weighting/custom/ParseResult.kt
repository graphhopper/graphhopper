package com.graphhopper.routing.weighting.custom

class ParseResult {
    @JvmField
    internal var converted: StringBuilder? = null

    @JvmField
    internal var ok = false

    @JvmField
    internal var invalidMessage: String? = null

    @JvmField
    internal var guessedVariables: MutableSet<String>? = null

    @JvmField
    internal var operators: MutableSet<String>? = null
}
