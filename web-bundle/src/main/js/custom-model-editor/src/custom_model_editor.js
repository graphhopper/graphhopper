import CodeMirror from "codemirror";
import "codemirror/mode/yaml/yaml";
import "codemirror/addon/hint/show-hint";
import "codemirror/addon/lint/lint";
import {validate} from "./validate.js";
import {complete} from "./complete.js";
import {parse} from "./parse.js";


class CustomModelEditor {
    // The underlying code mirror object, use at your own risk. For bigger changes it is probably better to implement here
    cm;
    _categories = {};

    /**
     * Creates a custom model editor for the given categories and calls the given callback with the editor element
     * as argument.
     */
    constructor(categories, callback) {
        this._categories = categories;

        this.cm = CodeMirror(callback, {
            lineNumbers: true,
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
            // in case the auto-complete popup is active already we update it (this allows filtering values while typing
            // with an open popup)
            if (this.cm.state.completionActive) {
                this.showAutoCompleteSuggestions();
            }
        });
    }

    set categories (categories) {
        this._categories = categories;
    }

    set value (value) {
        this.cm.setValue(value);
    }

    get value() {
        return this.cm.getValue();
    }

    setExtraKey(keyString, callback) {
        (this.cm.getOption('extraKeys'))[keyString] = callback;
    }

    /**
     * Builds a list of errors for the current text such that they can be visualized in the editor.
     */
    getCurrentErrors = (text, options, editor) => {
        const validateResult = validate(text);
        const errors = validateResult.errors.map((err, i) => {
            return {
                message: err.path + ': ' + err.message,
                severity: 'error',
                from: editor.posFromIndex(err.range[0]),
                to: editor.posFromIndex(err.range[1])
            }
        });

        const conditionRanges = validateResult.conditionRanges;
        conditionRanges.forEach((cr, i) => {
            const condition = text.substring(cr[0], cr[1]);
            const parseRes = parse(condition, this._categories);
            if (parseRes.error !== null) {
                errors.push({
                    message: parseRes.error,
                    severity: 'error',
                    from: editor.posFromIndex(cr[0] + parseRes.range[0]),
                    to: editor.posFromIndex(cr[0] + parseRes.range[1])
                });
            }
        });
        return errors;
    }

    showAutoCompleteSuggestions = () => {
        const validateResult = validate(this.cm.getValue());
        const cursor = this.cm.indexFromPos(this.cm.getCursor());
        validateResult.conditionRanges
            .map(cr => {
                const condition = this.cm.getValue().substring(cr[0], cr[1]);
                const offset = cr[0];
                // note that we allow the cursor to be at the end (inclusive!) of the range
                if (cursor >= offset && cursor <= cr[1]) {
                    const completeRes = complete(condition, cursor - offset, this._categories);
                    if (completeRes.suggestions.length > 0) {
                        const range = [
                            this.cm.posFromIndex(completeRes.range[0] + offset),
                            this.cm.posFromIndex(completeRes.range[1] + offset),
                        ];
                        this._suggest(range, completeRes.suggestions);
                    }
                }
            });
    }

    _suggest = (range, suggestions) => {
        const options = {
            hint: function () {
                const completion = {
                    from: range[0],
                    to: range[1],
                    list: suggestions,
                };
                CodeMirror.on(completion, "pick", function (selectedItem) {
                    // console.log(selectedItem);
                });
                return completion;
            },
        };
        this.cm.showHint(options);
    }
}

export { CustomModelEditor }