import CodeMirror from "codemirror";
import "codemirror/mode/javascript/javascript";
import "codemirror/addon/edit/matchbrackets";
import "codemirror/addon/edit/closebrackets";
import "codemirror/addon/hint/show-hint";
import "codemirror/addon/lint/lint";
import {validateJson} from "./validate_json";
import {complete} from "./complete.js";
import {parse} from "./parse.js";
import {completeJson} from "./complete_json";


class CustomModelEditor {
    // The underlying code mirror object, use at your own risk. For bigger changes it is probably better to implement here
    cm;
    _categories = {};
    _validListener;

    /**
     * Creates a custom model editor for the given categories and calls the given callback with the editor element
     * as argument.
     */
    constructor(categories, callback) {
        this._categories = categories;

        this.cm = CodeMirror(callback, {
            lineNumbers: false,
            matchBrackets: true,
            autoCloseBrackets: true,
            mode: "application/json",
            extraKeys: {
                'Ctrl-Space': this.showAutoCompleteSuggestions,
                'Alt-Enter': this.showAutoCompleteSuggestions
            },
            lint: {
                getAnnotations: this.getAnnotations
            },
            gutters: []
        });

        this.cm.on("cursorActivity", (e) => {
            // we update the auto complete window to allows filtering values while typing with an open popup)
            const open = this.cm.state.completionActive;
            this.cm.closeHint();
            if (open)
                this.showAutoCompleteSuggestions();
        });
    }

    set categories(categories) {
        this._categories = categories;
    }

    set value(value) {
        this.cm.setValue(value);
    }

    get value() {
        return this.cm.getValue();
    }

    get jsonObj() {
        try {
            return JSON.parse(this.cm.getValue());
        } catch (e) {
            throw 'invalid json: ' + this.cm.getValue() + 'error: ' + e;
        }
    }

    getUsedCategories = () => {
        const currentErrors = this.getCurrentErrors(this.cm.getValue(), this.cm);
        if (currentErrors.errors.length !== 0)
            console.warn('invalid custom model', currentErrors.errors);
        return currentErrors.usedCategories;
    }

    setExtraKey = (keyString, callback) => {
        (this.cm.getOption('extraKeys'))[keyString] = callback;
    }

    set validListener(validListener) {
        this._validListener = validListener;
    }

    getAnnotations = (text, options, editor) => {
        const errors = this.getCurrentErrors(text, editor).errors;
        if (this._validListener)
            this._validListener(errors.length === 0);
        return errors;
    }
    /**
     * Builds a list of errors for the current text such that they can be visualized in the editor.
     */
    getCurrentErrors = (text, editor) => {
        const validateResult = validateJson(text);
        const errors = validateResult.errors.map((err, i) => {
            return {
                message: err.path + ': ' + err.message,
                severity: 'error',
                from: editor.posFromIndex(err.range[0]),
                to: editor.posFromIndex(err.range[1])
            }
        });

        const areas = validateResult.areas;
        const conditionRanges = validateResult.conditionRanges;
        const usedCategories = new Set();
        conditionRanges.forEach((cr, i) => {
            const condition = text.substring(cr[0], cr[1]);
            Object.keys(this._categories).forEach((c) => {
                if (condition.indexOf(c) >= 0)
                    usedCategories.add(c);
            });
            if (condition.length < 3 || condition[0] !== `"` || condition[condition.length - 1] !== `"`) {
                errors.push({
                    message: `must be a non-empty string with double quotes, e.g. "true". given: ${condition}`,
                    severity: 'error',
                    from: editor.posFromIndex(cr[0]),
                    to: editor.posFromIndex(cr[1]),
                });
                return;
            }
            const parseRes = parse(condition.substring(1, condition.length-1), this._categories, areas);
            if (parseRes.error !== null) {
                errors.push({
                    message: parseRes.error,
                    severity: 'error',
                    from: editor.posFromIndex(cr[0] + parseRes.range[0] + 1),
                    to: editor.posFromIndex(cr[0] + parseRes.range[1] + 1)
                });
            }
        });

        // if there are no errors we consider the jsonErrors next (but most of them should be fixed at this point),
        // catching the errors manually before we get here can be better, because this way we can provide better error
        // messages and ranges and in some cases the user experience is better if we first show the more specific
        // 'schema' errors and only later syntax errors like unclosed brackets etc.
        if (errors.length === 0) {
            validateResult.jsonErrors.forEach(err => {
                errors.push({
                    message: err.path + ': ' + err.message,
                    severity: 'error',
                    from: editor.posFromIndex(err.range[0]),
                    to: editor.posFromIndex(err.range[1])
                });
            });
        }
        return {
            errors,
            usedCategories: Array.from(usedCategories)
        };
    }

    showAutoCompleteSuggestions = () => {
        const validateResult = validateJson(this.cm.getValue());
        const cursor = this.cm.indexFromPos(this.cm.getCursor());
        const completeRes = completeJson(this.cm.getValue(), cursor);
        if (completeRes.suggestions.length > 0) {
            if (completeRes.suggestions.length === 1 && completeRes.suggestions[0] === `__hint__type a condition`) {
                // if the json completion suggests entering a condition we run the condition completion on the found
                // condition range instead
                let start = completeRes.range[0];
                let stop = completeRes.range[1];
                if (this.cm.getValue()[start] === `"`) start++;
                if (this.cm.getValue()[stop-1] === `"`) stop--;
                const condition = this.cm.getValue().substring(start, stop);
                const completeConditionRes = complete(condition, cursor - start, this._categories, validateResult.areas);
                if (completeConditionRes.suggestions.length > 0) {
                    const range = [
                        this.cm.posFromIndex(completeConditionRes.range[0] + start),
                        this.cm.posFromIndex(completeConditionRes.range[1] + start)
                    ];
                    this._suggest(range, completeConditionRes.suggestions.sort());
                }
            } else {
                // limit the replacement range to the current line and do not include the new line character at the
                // end of the line. otherwise auto-complete messes up the following lines.
                const currLineStart = this.cm.indexFromPos({line: this.cm.getCursor().line, ch: 0});
                const currLineEnd = this.cm.indexFromPos({line: this.cm.getCursor().line + 1, ch: 0});
                const start = Math.max(currLineStart, completeRes.range[0]);
                let stop = Math.min(currLineEnd, completeRes.range[1]);
                if (stop > start && /\r\n|\r|\n/g.test(this.cm.getValue()[stop - 1]))
                    stop--;
                const range = [
                    this.cm.posFromIndex(start),
                    this.cm.posFromIndex(stop)
                ];
                this._suggest(range, completeRes.suggestions);
            }
        }
    }

    _suggest = (range, suggestions) => {
        const options = {
            hint: function () {
                const completion = {
                    from: range[0],
                    to: range[1],
                    list: suggestions.map(s => {
                        // hints are shown to the user, but no auto-completion is actually performed
                        if (startsWith(s, '__hint__')) {
                            return {
                                text: '',
                                displayText: s.substring('__hint__'.length)
                            }
                        } else {
                            return {
                                text: s
                            }
                        }
                    }),
                };
                CodeMirror.on(completion, "pick", function (selectedItem) {
                    // console.log(selectedItem);
                });
                return completion;
            },
            completeSingle: false
        };
        this.cm.showHint(options);
    }
}

function startsWith(str, substr) {
    return str.substr(0, substr.length) === substr;
}

export {CustomModelEditor}