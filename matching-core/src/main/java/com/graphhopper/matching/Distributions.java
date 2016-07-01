/**
 * Copyright (C) 2015, BMW Car IT GmbH
 * Author: Stefan Holder (stefan.holder@bmw.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.graphhopper.matching;

import static java.lang.Math.*;

class Distributions {

    static double normalDistribution(double sigma, double x) {
        return 1.0 / (sqrt(2.0 * PI) * sigma) * exp(-0.5 * pow(x / sigma, 2));
    }

    static double exponentialDistribution(double beta, double x) {
        return 1.0 / beta * exp(-x / beta);
    }

    /**
     * Use this function instead of Math.log(exponentialDistribution(beta, x)) to avoid an
     * arithmetic underflow for very small probabilities.
     */
    static double logExponentialDistribution(double beta, double x) {
        return log(1.0 / beta) - (x / beta);
    }
}
