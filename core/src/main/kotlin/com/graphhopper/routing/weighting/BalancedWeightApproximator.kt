/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.weighting

/**
 * Turns an unidirectional weight Approximation into a bidirectional balanced one.
 * This means it can be used with an A* implementation that uses the stopping criterion
 * described in [1].
 *
 * Note: In the paper, it is called a consistent (rather than balanced)
 * approximator, but as noted in [2], consistent also means a different property which
 * an approximator must already have before it should be plugged into this class (!).
 *
 * Most literature uses balanced for the property that this class is about.
 *
 * [1] Ikeda, T., Hsu, M.-Y., Imai, H., Nishimura, S., Shimoura, H., Hashimoto, T., Tenmoku, K., and
 * Mitoh, K. (1994). A fast algorithm for finding better routes by ai search techniques. In VNIS,
 * pages 291–296.
 *
 * [2] Pijls, W.H.L.M, &amp; Post, H. (2008). A new bidirectional algorithm for shortest paths (No. EI 2008-25).
 * Report / Econometric Institute, Erasmus University Rotterdam
 *
 * @author jansoe
 * @author Peter Karich
 */
class BalancedWeightApproximator(weightApprox: WeightApproximator?) {

    private val uniDirApproximatorForward: WeightApproximator
    private val uniDirApproximatorReverse: WeightApproximator

    // Constants to shift the estimate (reverse estimate) so that it is actually 0 at the destination (source).
    private var fromOffset = 0.0
    private var toOffset = 0.0

    init {
        requireNotNull(weightApprox) { "WeightApproximator cannot be null" }
        uniDirApproximatorForward = weightApprox
        uniDirApproximatorReverse = weightApprox.reverse()
    }

    val approximation: WeightApproximator
        get() = uniDirApproximatorForward

    fun setFromTo(from: Int, to: Int) {
        uniDirApproximatorReverse.setTo(from)
        uniDirApproximatorForward.setTo(to)
        fromOffset = 0.5 * uniDirApproximatorForward.approximate(from)
        toOffset = 0.5 * uniDirApproximatorReverse.approximate(to)
    }

    fun approximate(node: Int, reverse: Boolean): Double {
        val weightApproximation = 0.5 * (uniDirApproximatorForward.approximate(node) - uniDirApproximatorReverse.approximate(node))
        return if (reverse) {
            fromOffset - weightApproximation
        } else {
            toOffset + weightApproximation
        }
    }

    val slack: Double
        get() = uniDirApproximatorForward.slack

    override fun toString(): String {
        return uniDirApproximatorForward.toString()
    }
}
