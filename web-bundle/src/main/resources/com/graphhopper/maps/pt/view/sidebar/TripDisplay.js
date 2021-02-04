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

import {LegMode} from "../../data/Leg.js";
import {WaypointType} from "../../data/Waypoint.js";
import {Leg, PtLeg} from "./Leg.js";
import {Arrival, Waypoint} from "./TripElement.js";
import SecondaryText from "../components/SecondaryText.js";

export default (({
                   trips,
                   onSelectIndex
                 }) => {
  return React.createElement("div", {
    className: "tripDisplay"
  }, trips.paths.map((trip, i) => {
    return React.createElement(Trip, {
      key: i,
      trip: trip,
      selectedIndex: trips.selectedRouteIndex,
      index: i,
      onSelectIndex: onSelectIndex
    });
  }));
});

const Trip = ({
                trip,
                index,
                onSelectIndex
              }) => {
  return React.createElement("div", null, trip.isSelected ? React.createElement("div", null, React.createElement(TripHeader, {
    trip: trip
  }), React.createElement(TripDetails, {
    trip: trip
  })) : React.createElement("button", {
    className: "tripToggle",
    onClick: e => onSelectIndex(index)
  }, React.createElement(TripHeader, {
    trip: trip
  })));
};

const TripHeader = ({
                      trip
                    }) => {
  return React.createElement("div", {
    className: "tripHeader"
  }, React.createElement("div", {
    className: trip.isPossible ? "tripTime" : "tripTimeImpossible"
  }, React.createElement("span", null, moment(trip.departureTime).format("HH:mm"), " \u2013", " ", moment(trip.arrivalTime).format("HH:mm")), React.createElement("span", null, trip.durationInMinutes, " min")), React.createElement(SecondaryText, null, "Transfers: ", trip.transfers, " \u2013 ", trip.fare, " "));
};

const TripDetails = ({
                       trip
                     }) => {
  function getLeg(leg) {
    switch (leg.type) {
      case LegMode.PT:
        return React.createElement(PtLeg, {
          leg: leg,
          isLastLeg: false
        });

      default:
        return React.createElement(Leg, {
          leg: leg,
          isLastLeg: false
        });
    }
  }

  function getWaypointElement(waypoint) {
    let arrivalElement = "";

    if (waypoint.type === WaypointType.INBEETWEEN && waypoint.prevMode === LegMode.PT) {
      arrivalElement = React.createElement(Arrival, {
        time: moment(waypoint.arrivalTime).format("HH:mm"),
        delay: waypoint.arrivalDelay
      });
    }

    return React.createElement("div", null, arrivalElement, React.createElement(Waypoint, {
      name: waypoint.name,
      time: moment(waypoint.departureTime).format("HH:mm"),
      delay: waypoint.departureDelay,
      isPossible: waypoint.isPossible
    }));
  }

  function getLastWaypointElement(waypoints) {
    let last = waypoints[waypoints.length - 1];
    return React.createElement(Waypoint, {
      time: moment(last.arrivalTime).format("HH:mm"),
      delay: last.arrivalDelay,
      name: last.name,
      isLast: true
    });
  }

  return React.createElement("div", {
    className: "tripDetails"
  }, trip.legs.map((leg, i) => {
    return React.createElement("div", {
      key: i
    }, getWaypointElement(trip.waypoints[i]), getLeg(leg));
  }), getLastWaypointElement(trip.waypoints));
};