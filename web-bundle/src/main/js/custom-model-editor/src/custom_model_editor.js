import CodeMirror from "codemirror";
import "codemirror/mode/yaml/yaml";
import "codemirror/mode/javascript/javascript";
import "codemirror/addon/edit/matchbrackets";
import "codemirror/addon/hint/show-hint";
import "codemirror/addon/lint/lint";
import YAML from "yaml";
import {validate} from "./validate.js";
import {complete} from "./complete.js";
import {parse} from "./parse.js";
import {completeYaml} from "./yaml_complete";


class CustomModelEditor {
    // The underlying code mirror object, use at your own risk. For bigger changes it is probably better to implement here
    cm;
    _categories = {};
    _yaml = true;
    _yamlContent = '';
    _yamlCursor;
    _validListener;

    /**
     * Creates a custom model editor for the given categories and calls the given callback with the editor element
     * as argument.
     */
    constructor(categories, callback) {
        this._categories = categories;

        this.cm = CodeMirror(callback, {
            lineNumbers: true,
            matchBrackets: true,
            mode: "yaml",
            extraKeys: {
                'Ctrl-Space': this.showAutoCompleteSuggestions
            },
            lint: {
                getAnnotations: this.getCurrentErrors
            },
            gutters: ["CodeMirror-linenumbers", "CodeMirror-lint-markers"]
        });

        this.cm.on("cursorActivity", (e) => {
            if (!this._yaml)
                return;
            // we update the auto complete window to allows filtering values while typing with an open popup)
            this.cm.closeHint();
            if (this.cm.state.completionActive) {
                this.showAutoCompleteSuggestions();
            }
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
            return YAML.parse(this.cm.getValue());
        } catch (e) {
            console.error('invalid yaml', e);
            return null;
        }
    }

    setExtraKey = (keyString, callback) => {
        (this.cm.getOption('extraKeys'))[keyString] = callback;
    }

    get yaml() {
        return this._yaml;
    }

    set validListener(validListener) {
        this._validListener = validListener;
    }

    toggleJsonYAML = () => {
        if (this._yaml) {
            this._yamlContent = this.cm.getValue();
            this._yamlCursor = this.cm.getCursor();
            const json = JSON.stringify(YAML.parseDocument(this._yamlContent).toJS(), null, 2);
            this._yaml = false;
            this.cm.setOption('mode', 'application/json');
            this.cm.setOption('readOnly', true);
            this.cm.setValue(json);
        } else {
            this.cm.setOption('mode', 'yaml');
            this.cm.setOption('readOnly', false);
            this.cm.setValue(this._yamlContent);
            this.cm.setCursor(this._yamlCursor);
            this.cm.focus();
            this._yaml = true;
        }
    }

    /**
     * Builds a list of errors for the current text such that they can be visualized in the editor.
     */
    getCurrentErrors = (text, options, editor) => {
        if (!this._yaml)
            return [];
        const validateResult = validate(text);
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
        conditionRanges.forEach((cr, i) => {
            const condition = text.substring(cr[0], cr[1]);
            const parseRes = parse(condition, this._categories, areas);
            if (parseRes.error !== null) {
                errors.push({
                    message: parseRes.error,
                    severity: 'error',
                    from: editor.posFromIndex(cr[0] + parseRes.range[0]),
                    to: editor.posFromIndex(cr[0] + parseRes.range[1])
                });
            }
        });

        // if there are no errors we consider the yamlErrors next (but most of them should be fixed at this point),
        // catching the errors manually before we get here can be better, because this way we can provide better error
        // messages and ranges and in some cases the user experience is better if we first show the more specific
        // 'schema' errors and only later syntax errors like unclosed brackets etc.
        if (errors.length === 0) {
            validateResult.yamlErrors.forEach(err => {
                errors.push({
                    message: err.path + ': ' + err.message,
                    severity: 'error',
                    from: editor.posFromIndex(err.range[0]),
                    to: editor.posFromIndex(err.range[1])
                });
            });
        }
        if (this._validListener)
            this._validListener(errors.length === 0);
        return errors;
    }

    showAutoCompleteSuggestions = () => {
        const validateResult = validate(this.cm.getValue());
        const cursor = this.cm.indexFromPos(this.cm.getCursor());
        const completeRes = completeYaml(this.cm.getValue(), cursor);
        if (completeRes.suggestions.length > 0) {
            if (completeRes.suggestions.length === 1 && completeRes.suggestions[0] === `__hint__type a condition`) {
                // if the yaml completion suggests entering a condition we run the condition completion on the found
                // condition range instead
                const condition = this.cm.getValue().substring(completeRes.range[0], completeRes.range[1]);
                const offset = completeRes.range[0];
                const completeConditionRes = complete(condition, cursor - offset, this._categories, validateResult.areas);
                if (completeConditionRes.suggestions.length > 0) {
                    const range = [
                        this.cm.posFromIndex(completeConditionRes.range[0] + offset),
                        this.cm.posFromIndex(completeConditionRes.range[1] + offset)
                    ];
                    this._suggest(range, completeConditionRes.suggestions);
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
                // filter suggestions based on existing value
                const suggestions = completeRes.suggestions.filter(s =>
                    startsWith(s, this.cm.getValue().substring(start, stop).trim()));
                this._suggest(range, suggestions);
            }
        }
    }

    _suggest = (range, suggestions) => {
        const options = {
            hint: function () {
                const completion = {
                    from: range[0],
                    to: range[1],
                    list: suggestions.sort().map(s => {
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