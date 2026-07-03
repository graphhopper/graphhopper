package com.graphhopper.routing.weighting.custom

fun interface NameValidator {
    fun isValid(name: String): Boolean
}
