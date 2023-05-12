import {DateInput, Select, TextInput, TimeInput} from "../components/Inputs.js";
import {TimeOption} from "../../data/Query.js";

export default (({
                     search,
                     onSearchChange
                 }) => {
    return React.createElement(SearchInput, {
        search: search,
        onSearchChange: onSearchChange
    });
});
const SearchActionType = {
    FROM: "SearchActionType_FROM",
    TO: "SearchActionType_TO",
    DEPARTURE_TIME: "SearchActionType_DEPARTURE_TIME",
    DEPARTURE_DATE: "SearchActionType_DEPARTURE_DATE",
    TIME_OPTION: "SearchActionType_TIME_OPTION",
};

class SearchInput extends React.Component {
    constructor(props) {
        super(props);
        this.onChange = this.onChange.bind(this);
        this.handleInputChange = this.handleInputChange.bind(this);
        this.state = {
            isShowingOptions: false
        };
    }

    render() {
        return React.createElement("div", {
            className: "searchInput"
        }, React.createElement("div", {
            className: "locationInput"
        }, React.createElement(TextInput, {
            value: this.props.search.from != null ? [this.props.search.from.lat,this.props.search.from.lng].toString() : "",
            label: "From",
            actionType: SearchActionType.FROM,
            onChange: this.onChange
        }), React.createElement(TextInput, {
            value: this.props.search.to != null ? [this.props.search.to.lat,this.props.search.to.lng].toString() : "",
            label: "To",
            actionType: SearchActionType.TO,
            onChange: this.onChange
        })), React.createElement("div", {
            className: "timeSelect"
        }, React.createElement(Select, {
            value: this.props.search.timeOption,
            label: "Time",
            options: [{
                value: TimeOption.DEPARTURE,
                label: "Departure at"
            }, {
                value: TimeOption.ARRIVAL,
                label: "Arrival at"
            }],
            onChange: this.onChange,
            actionType: SearchActionType.TIME_OPTION
        }), this.props.search.timeOption != TimeOption.NOW ? React.createElement("div", {
            className: "dateTimeContainer"
        }, React.createElement(TimeInput, {
            value: this.props.search.departureDateTime.format("HH:mm"),
            onChange: this.onChange,
            actionType: SearchActionType.DEPARTURE_TIME
        }), React.createElement(DateInput, {
            value: this.props.search.departureDateTime.format("YYYY-MM-DD"),
            onChange: this.onChange,
            actionType: SearchActionType.DEPARTURE_DATE
        })) : ""), React.createElement("div", null, React.createElement("button", {
            className: "optionsButton",
            onClick: e => this.setState(prevState => ({
                isShowingOptions: !prevState.isShowingOptions
            }))
        }, "Options"), this.state.isShowingOptions ? React.createElement(
            "div",
            null,
            React.createElement(Select, {
                value: this.props.search.accessProfile,
                label: "Access profile",
                options: this.accessEgressProfileOptions(),
                onChange: this.handleInputChange,
                actionType: "accessProfile"
            }),
            React.createElement(TextInput, {
                value: this.props.search.betaAccessTime,
                label: "Access time beta",
                onChange: this.handleInputChange,
                actionType: "betaAccessTime"
            }),
            React.createElement(Select, {
                value: this.props.search.egressProfile,
                label: "Egress profile",
                options: this.accessEgressProfileOptions(),
                onChange: this.handleInputChange,
                actionType: "egressProfile"
            }),
            React.createElement(TextInput, {
                value: this.props.search.betaEgressTime,
                label: "Egress time beta",
                onChange: this.handleInputChange,
                actionType: "betaEgressTime"
            }),
            React.createElement(
                TextInput,
                {
                    actionType: "rangeQueryDuration",
                    value: this.props.search.rangeQueryDuration,
                    label: "Range (ISO duration)",
                    onChange: this.handleInputChange
                }
            ),
            React.createElement(
                TextInput,
                {
                    actionType: "limitStreetTime",
                    value: this.props.search.limitStreetTime,
                    label: "Maximum access/egress time (ISO duration)",
                    onChange: this.handleInputChange
                }
            ),
            React.createElement(Select, {
                value: this.props.search.ignoreTransfers,
                label: "Ignore # transfers as criterion",
                options: [{
                    value: "true",
                    label: "true"
                }, {
                    value: "false",
                    label: "false"
                }],
                onChange: this.handleInputChange,
                actionType: "ignoreTransfers"
            })
        ) : ""));
    }

    accessEgressProfileOptions() {
        return this.props.search.info.profiles
            .filter(function (profile) {
                return profile.name != "pt";
            })
            .map(function (profile) {
                return {
                    value: profile.name,
                    label: profile.name
                }
            });
    }

    handleInputChange(action) {
        this.props.onSearchChange({[action.type]: action.value})
    }

    onChange(action) {
        console.log(action);

        switch (action.type) {
            case SearchActionType.FROM:
                this.props.onSearchChange({
                    from: this.createFromString(action.value)
                });
                break;

            case SearchActionType.TO:
                this.props.onSearchChange({
                    to: this.createFromString(action.value)
                });
                break;

            case SearchActionType.DEPARTURE_TIME:
                let departure1 = moment(action.value, "HH:mm");

                if (departure1.isValid()) {
                    departure1.year(this.props.search.departureDateTime.year());
                    departure1.month(this.props.search.departureDateTime.month());
                    departure1.date(this.props.search.departureDateTime.date());
                    this.props.onSearchChange({
                        departureDateTime: departure1
                    });
                }

                break;

            case SearchActionType.DEPARTURE_DATE:
                let departure2 = moment(action.value, "YYYY-MM-DD");

                if (departure2.isValid()) {
                    departure2.hour(this.props.search.departureDateTime.hour());
                    departure2.minute(this.props.search.departureDateTime.minute());
                    this.props.onSearchChange({
                        departureDateTime: departure2
                    });
                }

                break;

            case SearchActionType.TIME_OPTION:
                this.props.onSearchChange({
                    timeOption: parseInt(action.value)
                });
                break;

            default:
                break;
        }
    }

    createFromString(coord) {
        let split = coord.split(",");
        let map = split.map(value => {
            let number = Number.parseFloat(value);
            return Number.isNaN(number) ? 0 : number;
        });
        return new mapboxgl.LngLat(map[1],map[0]);
    }

}