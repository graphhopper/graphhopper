import {Waypoint, LegDescription, StopOnLeg, Turn, Padding} from "./TripElement.js";
import {LegMode} from "../../data/Leg.js";

class Leg extends React.Component {
    constructor(props) {
        super(props);

        this._validateProps(props);

        this.state = {
            isCollapsed: true
        };
    }

    _validateProps(props) {
        if (!props.leg) throw Error("Leg component requires property 'leg'");
    }

    _calculateDuration(departure, arrival) {
        return moment(arrival).diff(moment(departure), "minutes");
    }

    _handleLegDescriptionClicked() {
        this.setState({
            isCollapsed: !this.state.isCollapsed
        });
    }

    renderLegDetails() {
        const {
            leg
        } = this.props;
        return React.createElement("div", null, leg.turns.map((turn, i) => {
            return React.createElement(Turn, {
                sign: turn.sign,
                text: turn.description,
                key: i
            });
        }), React.createElement(Padding, null));
    }

    getLegIcon() {
        return "view/img/foot.png";
    }

    render() {
        const {
            leg,
            onClick,
            isLastLeg
        } = this.props;
        return React.createElement("div", null, React.createElement(LegDescription, {
            icon: this.getLegIcon(),
            onClick: () => this._handleLegDescriptionClicked()
        }, React.createElement("div", {
            className: "legDescriptionDetails"
        }, React.createElement("span", null, this._calculateDuration(leg.departureTime, leg.arrivalTime), " ", "min,\xA0"), React.createElement("span", null, leg.distance), React.createElement("div", {
            className: this.state.isCollapsed ? "carrotContainer" : "carrotContainerFlipped"
        }, React.createElement(
            "svg",
            {viewBox: "0 0 460 240"},
            React.createElement(
                "line",
                {x1: 215, y1: 225, x2: 445, y2: 15, "strokeWidth": 30}
            ),
            React.createElement(
                "line",
                {x1: 15, y1: 15, x2: 215, y2: 225, "strokeWidth": 30}
            ),
        )))), !this.state.isCollapsed ? this.renderLegDetails() : "");
    }

}

class PtLeg extends Leg {
    constructor(props) {
        super(props);
    }

    renderLegDetails() {
        const {
            leg
        } = this.props;
        return React.createElement("div", null, leg.turns.map((stop, i) => {
            if (stop.name) {
                return React.createElement(StopOnLeg, {
                    name: stop.name,
                    time: moment(stop.departureTime).format("HH:mm"),
                    delay: stop.delay,
                    key: i
                });
            }

            return "";
        }), React.createElement(Padding, null));
    }

    getLegIcon() {
        return "view/img/bus.png";
    }

}

export {Leg, PtLeg};