import SecondaryText from "./SecondaryText.js";

const TextInput = props => {
    return Input(Object.assign({}, props, {
        type: "text"
    }));
};

const TimeInput = props => {
    return Input(Object.assign({}, props, {
        type: "time"
    }));
};

const DateInput = props => {
    return Input(Object.assign({}, props, {
        type: "date"
    }));
};

const Select = ({
                    value,
                    label = "",
                    options,
                    onChange,
                    actionType
                }) => {
    return React.createElement("div", {
        className: "inputContainer"
    }, React.createElement(SecondaryText, null, label), React.createElement("select", {
        className: "select",
        value: value,
        onChange: e => onChange({
            type: actionType,
            value: e.target.value
        })
    }, options.map((option, i) => {
        return React.createElement("option", {
            value: option.value,
            key: i
        }, option.label);
    })));
};

const Input = ({
                   value,
                   label = "",
                   type,
                   actionType,
                   onChange
               }) => {
    return React.createElement("div", {
        className: "inputContainer"
    }, React.createElement(SecondaryText, null, label), React.createElement("input", {
        className: "input",
        type: type,
        value: value,
        onChange: e => onChange({
            type: actionType,
            value: e.target.value
        })
    }));
};

export { TextInput, TimeInput, DateInput, Select };