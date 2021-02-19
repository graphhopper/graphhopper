import CodeMirror from "codemirror";
import "codemirror/mode/yaml/yaml";
import "codemirror/addon/hint/show-hint";
import "codemirror/addon/lint/lint";
import { validate } from "./validate.js";
import { complete } from "./complete.js";
import { parse } from "./parse.js";

/**
 * Creates a custom model editor for the given categories calls the given callback with the editor element
 * as argument. Everything is configured within this function at the moment and besides specifying these two parameters
 * nothing can be customized. However, this function returns the CodeMirror instance that can be further modified at
 * your own risk. For bigger changes it is probably better to implement the functionality here.
 */
function create(categories, callback) {
    const cm = CodeMirror(callback, {
        lineNumbers: true,
        mode: "yaml",
        extraKeys: {
            'Ctrl-Space': showAutoCompleteSuggestions
        },
        lint: {
            getAnnotations: getCurrentErrors
        },
        gutters: ["CodeMirror-linenumbers", "CodeMirror-lint-markers"]
    });

    cm.on("cursorActivity", (e) => {
        // in case the auto-complete popup is active already we update it (allow filtering values while typing with
        // an open popup)
        if (cm.state.completionActive) {
            showAutoCompleteSuggestions();
        }
    });

    /**
     * Builds a list of errors for the current text such that they can be visualized in the editor.
     */
    function getCurrentErrors(text, options, editor) {
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
            const parseRes = parse(condition, categories);
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

    function showAutoCompleteSuggestions() {
        const validateResult = validate(cm.getValue());
        const cursor = cm.indexFromPos(cm.getCursor());
        validateResult.conditionRanges
            .map(cr => {
                const condition = cm.getValue().substring(cr[0], cr[1]);
                const offset = cr[0];
                // note that we allow the cursor to be at the end (inclusive!) of the range
                if (cursor >= offset && cursor <= cr[1]) {
                    const completeRes = complete(condition, cursor - offset, categories);
                    if (completeRes.suggestions.length > 0) {
                        const range = [
                            cm.posFromIndex(completeRes.range[0] + offset),
                            cm.posFromIndex(completeRes.range[1] + offset),
                        ];
                        suggest(range, completeRes.suggestions);
                    }
                }
            });
    }

    function suggest(range, suggestions) {
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
        cm.showHint(options);
    }

    return cm;
}

export { create }