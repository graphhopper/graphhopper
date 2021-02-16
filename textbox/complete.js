import {parse} from './parse.js';
import {tokenAtPos} from "./tokenize.js";

/**
 * Returns auto-complete suggestions for a given string/expression, categories and a character position. The returned
 * object contains two fields:
 *  - suggestions: a list of suggestions/strings
 *  - range: the character range that is supposed to be replaced by the suggestion
 */
function complete(expression, pos, categories) {
    const lastNonWhitespace = getLastNonWhitespacePos(expression);
    if (pos > lastNonWhitespace) {
        // this is a little trick: we run parse on a manipulated expression where we inserted a dummy character to
        // see which completions are offered to us (assuming we typed in something)
        let parseExpression = expression;
        while (parseExpression.length < pos)
            parseExpression += ' ';
        parseExpression = parseExpression.slice(0, pos) + '…';
        const parseResult = parse(parseExpression, categories);
        const tokenPos = tokenAtPos(parseExpression, pos);

        // in case the expression has an error at a position that is parsed before our position we return no suggestions
        if (parseResult.range[0] !== tokenPos.range[0])
            return empty();

        // we only keep the suggestions that match the already existing characters if there are any
        const suggestions = parseResult.completions.filter(c => {
            // we need to remove our dummy character for the filtering
            const partialToken = tokenPos.token.substring(0, tokenPos.token.length - 1);
            // todo: be careful with boolean encoded values later! c might not be a string here...
            return c.startsWith(partialToken);
        });
        return {
            suggestions: suggestions,
            range: suggestions.length === 0 ? null : [tokenPos.range[0], pos]
        }
    } else {
        const tokenPos = tokenAtPos(expression, pos);
        if (tokenPos.token === null) {
            return empty();
        } else {
            const parseExpression = expression.substring(0, tokenPos.range[0]) + '…' + expression.substring(tokenPos.range[1]);
            const parseResult = parse(parseExpression, categories);
            if (parseResult.range[0] !== tokenPos.range[0])
                return empty();
            return {
                suggestions: parseResult.completions,
                range: tokenPos.range
            }
        }
    }
}

function empty() {
    return {
        suggestions: [],
        range: null
    }
}

function getLastNonWhitespacePos(str) {
    for (let i = str.length - 1; i >= 0; --i) {
        if (str.slice(i, i + 1).trim() !== '') {
            return i;
        }
    }
    return -1;
}

export {complete};