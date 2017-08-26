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

package com.graphhopper;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import static com.graphhopper.util.Parameters.Routing.POINT_HINT;

public class GHRequestValidator
        implements ConstraintValidator<ValidGHRequest, GHRequest> {

    @Override
    public void initialize(ValidGHRequest constraintAnnotation) {
    }

    @Override
    public boolean isValid(GHRequest ghRequest, ConstraintValidatorContext context) {
        context.disableDefaultConstraintViolation();
        if (ghRequest == null) {
            return false;
        }
        if(ghRequest.getPoints().isEmpty()) {
            context.buildConstraintViolationWithTemplate("You have to pass at least one point")
                    .addConstraintViolation();
            return false;
        }
        if (ghRequest.getPoints().size() != ghRequest.getFavoredHeadings().size()) {
            context.buildConstraintViolationWithTemplate("The number of 'heading' parameters must be <= 1 or equal to the number of points (${validatedValue.points.size()})")
                    .addConstraintViolation();
            return false;
        }
        if (ghRequest.getPointHints().size() > 0 && ghRequest.getPointHints().size() != ghRequest.getPoints().size()) {
            context.buildConstraintViolationWithTemplate("If you pass " + POINT_HINT + ", you need to pass a hint for every point, empty hints will be ignored")
                    .addConstraintViolation();
            return false;
        }
        return true;
    }

}
