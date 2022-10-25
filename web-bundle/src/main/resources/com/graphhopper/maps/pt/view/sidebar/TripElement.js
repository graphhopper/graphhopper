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

const Waypoint = ({
                    name,
                    time,
                    delay,
                    isPossible = true,
                    isLast = false
                  }) => {
  const waypointNameClassName = isPossible ? "waypointName" : "waypointNameImpossible";
  return React.createElement(TripElement, {
    decorationType: TripElementDecorationType.WAYPOINT,
    isLastElement: isLast
  }, React.createElement(TimeWithDelay, {
    time: time,
    delay: delay
  }), React.createElement("div", {
    className: waypointNameClassName
  }, React.createElement("span", null, name)));
};

const Arrival = ({
                   time,
                   delay
                 }) => {
  return React.createElement(TripElement, {
    decorationType: TripElementDecorationType.NONE
  }, React.createElement("div", {
    className: "arrivalTime"
  }, React.createElement(TimeWithDelay, {
    time: time,
    delay: delay
  })), React.createElement("span", null));
};

const TimeWithDelay = ({
                         time,
                         delay
                       }) => {
  function getDelay(delay) {
    if (delay) {
      let prefix = "";
      let style = "negativeDelay";

      if (delay > 0) {
        prefix = "+";
        style = "delay";
      }

      return React.createElement("span", {
        className: style
      }, " ", prefix + delay);
    }
  }

  return React.createElement("div", {
    className: "TimeWithDelay"
  }, React.createElement("span", null, time), getDelay(delay));
};

const Turn = ({
                sign,
                text
              }) => {
  return React.createElement(TripElement, {
    decorationType: TripElementDecorationType.NONE
  }, React.createElement("span", null), React.createElement("span", null, text));
};

const LegDescription = ({
                          icon,
                          onClick,
                          children
                        }) => {
  return React.createElement(TripElement, {
    decorationType: TripElementDecorationType.NONE
  }, React.createElement("img", {
    src: icon,
    className: "legDescriptionIcon"
  }), React.createElement("div", {
    className: "legRowDescription"
  }, React.createElement("button", {
    onClick: e => onClick(),
    className: "tripDescriptionButton"
  }, children)));
};

const StopOnLeg = ({
                     name,
                     time,
                     delay
                   }) => {
  function createDepartureTime(stop) {
    return React.createElement("div", null, React.createElement("span", null, moment(stop.departureTime).format("HH:mm")), stop.delay > 0 ? React.createElement("span", {
      className: "delay"
    }, " +", stop.delay) : "");
  }

  function getClassName(stop) {
    return stop.isCancelled ? "stopOnLegImpossible" : "";
  }

  return React.createElement(TripElement, {
    decorationType: TripElementDecorationType.STOP_ON_LEG
  }, React.createElement(TimeWithDelay, {
    time: time,
    delay: delay
  }), React.createElement("span", {
    className: getClassName(stop)
  }, name));
};

const Padding = () => {
  return React.createElement(TripElement, {
    decorationType: TripElementDecorationType.NONE
  }, React.createElement("span", {
    className: "padding"
  }), React.createElement("span", null));
};

class TripElement extends React.Component {
  constructor(props) {
    super(props);
  }

  render() {
    const {
      children,
      isLastElement = false,
      decorationType
    } = this.props;
    return React.createElement("div", {
      className: "tripElement"
    }, React.createElement("div", {
      className: "tripElementLeftColumn"
    }, children[0]), React.createElement("div", {
      className: "tripElementContent"
    }, React.createElement(TripElementDecoration, {
      isLastElement: isLastElement,
      decorationType: decorationType
    }), React.createElement("div", {
      className: "tripElementDetails"
    }, children[1])));
  }

}

const TripElementDecoration = ({
                                 isLastElement,
                                 decorationType
                               }) => {
  function getCircle(decorationType) {
    switch (decorationType) {
      case TripElementDecorationType.WAYPOINT:
        return React.createElement("div", {
          className: "waypointCircle"
        });

      case TripElementDecorationType.STOP_ON_LEG:
        return React.createElement("div", {
          className: "stopOnLegCircle"
        });

      default:
        return "";
    }
  }

  function getLine(isLastElement) {
    return isLastElement ? "" : React.createElement("div", {
      className: "line"
    });
  }

  return React.createElement("div", {
    className: "tripElementDecoration"
  }, getCircle(decorationType), getLine(isLastElement));
};

const TripElementDecorationType = {
  WAYPOINT: 0,
  STOP_ON_LEG: 1,
  NONE: 2
};
export { Waypoint, Arrival, LegDescription, StopOnLeg, Turn, Padding };