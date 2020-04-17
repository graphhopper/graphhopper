import { SearchActionType, TimeOption } from "../../data/Search.js";
import { DateInput, Select, TextInput, TimeInput } from "../components/Inputs.js";
import Point from "../../data/Point.js";
export default (({
                     search,
                     onSearchChange
                 }) => {
    return React.createElement(SearchInput, {
        search: search,
        onSearchChange: onSearchChange
    });
});
const options = [{
    value: TimeOption.DEPARTURE,
    label: "Departure at"
}, {
    value: TimeOption.ARRIVAL,
    label: "Arrival at"
}];

class SearchInput extends React.Component {
    constructor(props) {
        super(props);
        this.onChange = this.onChange.bind(this);
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
            value: this.props.search.from != null ? this.props.search.from.toString() : "",
            label: "From",
            actionType: SearchActionType.FROM,
            onChange: this.onChange
        }), React.createElement(TextInput, {
            value: this.props.search.to != null ? this.props.search.to.toString() : "",
            label: "To",
            actionType: SearchActionType.TO,
            onChange: this.onChange
        })), React.createElement("div", {
            className: "timeSelect"
        }, React.createElement(Select, {
            value: this.props.search.timeOption,
            label: "Time",
            options: options,
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
        }, "Options"), this.state.isShowingOptions ? React.createElement("div", null, React.createElement(TextInput, {
            value: this.props.search.limitSolutions,
            label: "# Alternatives",
            actionType: SearchActionType.LIMIT_SOLUTIONS,
            onChange: this.onChange
        })) : ""));
    }

    onChange(action) {
        console.log(action);

        switch (action.type) {
            case SearchActionType.FROM:
                this.props.onSearchChange({
                    from: Point.create(action.value)
                });
                break;

            case SearchActionType.TO:
                this.props.onSearchChange({
                    to: Point.create(action.value)
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

            case SearchActionType.LIMIT_SOLUTIONS:
                this.props.onSearchChange({
                    limitSolutions: action.value
                });
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

}