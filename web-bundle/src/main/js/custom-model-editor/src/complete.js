import {parse} from './parse.js';
import {tokenAtPos} from "./tokenize.js";

/**
 * Returns auto-complete suggestions for a given string/expression, categories, areas, and a character position.
 * The returned object contains two fields:
 *  - suggestions: a list of suggestions/strings
 *  - range: the character range that is supposed to be replaced by the suggestion
 */
function complete(expression, pos, categories, areas) {
    const lastNonWhitespace = getLastNonWhitespacePos(expression);
    if (pos > lastNonWhitespace) {
        // pad the expression with whitespace until pos, remove everything after pos
        let parseExpression = expression;
        while (parseExpression.length < pos)
            parseExpression += ' ';
        parseExpression = parseExpression.slice(0, pos);
        // we use a little trick: we run parse() on a manipulated expression where we inserted a dummy character to
        // see which completions are offered to us (assuming we typed in something)
        parseExpression += '…';
        const parseResult = parse(parseExpression, categories, areas);
        const tokenPos = tokenAtPos(parseExpression, pos);

        // in case the expression has an error at a position that is parsed before our position we return no suggestions
        if (parseResult.range[0] !== tokenPos.range[0])
            return empty();

        // we only keep the suggestions that match the already existing characters if there are any
        const suggestions = parseResult.completions.filter(c => {
            // we need to remove our dummy character for the filtering
            const partialToken = tokenPos.token.substring(0, tokenPos.token.length - 1);
            return startsWith(c, partialToken);
        });
        return {
            suggestions: suggestions,
            range: suggestions.length === 0 ? null : [tokenPos.range[0], pos]
        }
    } else {
        let tokenPos = tokenAtPos(expression, pos);
        // we replace the token at pos with a dummy character
        const parseExpression = expression.substring(0, tokenPos.range[0]) + '…' + expression.substring(tokenPos.range[1]);
        // pos might be a whitespace position but right at the end of the *previous* token. we have to deal with some
        // special cases (and this is actually similar to the situation where we are at the end of the expression).
        // this is quite messy, but relying on the tests for now...
        const modifiedTokenPos = tokenAtPos(parseExpression, tokenPos.range[0]);
        const parseResult = parse(parseExpression, categories, areas);
        if (parseResult.range[0] !== modifiedTokenPos.range[0])
            return empty();
        const suggestions = parseResult.completions.filter(c => {
            let partialToken = tokenPos.token === null
                ? modifiedTokenPos.token.substring(0, modifiedTokenPos.token.length - 1)
                : tokenPos.token.substring(0, pos - tokenPos.range[0]);
            return startsWith(c, partialToken);
        });
        return {
            suggestions: suggestions,
            range: suggestions.length === 0
                ? null
                : [modifiedTokenPos.range[0], tokenPos.token === null ? pos : tokenPos.range[1]]
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

function startsWith(str, substr) {
    // str.startsWith(substr) is not supported by IE11...
    return str.substring(0, substr.length) === substr;
}

export {complete};