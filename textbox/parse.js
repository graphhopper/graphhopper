import { tokenize } from './tokenize.js';

const comparisonOperators = ['==', '!='];
const logicOperators = ['||', '&&'];

let _categories;
let _tokens;
let _idx;

/**
 * Tokenizes and then parses the given expression. Returns an object containing:
 * - error, completions: see parseTokens
 * - range: the character range in which the (first) error occurred as list [startInclusive, endExclusive].
 *          if there are no invalid tokens, but rather something is missing the range will be
 *          [expression.length, expression.length]
 */
function parse(expression, categories) {
    const tokens = tokenize(expression);
    const result = parseTokens(tokens.tokens, categories);

    // translate token ranges to character ranges
    if (result.error !== null) {
        const tokenRanges = tokens.ranges;
        const errorStartToken = result.range[0];
        const errorEndToken = result.range[1];
        const start = errorStartToken === tokenRanges.length
            ? expression.length
            : tokenRanges[errorStartToken][0];
        const end = errorEndToken === tokenRanges.length
            ? expression.length
            : tokenRanges[errorEndToken - 1][tokenRanges[errorEndToken - 1].length - 1];
        result.range = [start, end];
    }
    return result;
}

/**
 * Parses a given list of tokens according to the following grammar.
 *
 * expression -> comparison (logicOperator comparison)*
 * comparison -> category comparator value | '(' expression ')'
 * logicOperator -> '&&' | '||'
 * comparator -> '==' | '!='
 *
 * Note that we do not care about operator precedence between && and || because our aim is not
 * actually evaluating the expression, but rather checking the validity.
 *
 * This function returns an object containing:
 * - error: an error string (or null) in case the tokens do not represent a valid expression.
 *          the parsing stops when the first error is encountered.
 * - range: the tokens range in which the (first) error occurred as list [startInclusive, endExclusive].
 *          if there are no invalid tokens, but rather something is missing the range will be
 *          [tokens.length, tokens.length]
 * - completions: a list of suggested tokens that could be used to replace the faulty ones
 *
 * An alternative to the implementation here could be using a parser library like pegjs or nearly.
 *
 * todo:
 *  - negation
 *  - boolean encoded values
 *  - numeric encoded values with <,>,<=,>= operators
 *  - parentheses around operands like (road_class) == (MOTORWAY)? -> probably not
 *  - boolean literal values 'true', 'false'
 *
 */
function parseTokens(tokens, categories) {
    if (Object.keys(categories).length < 1)
        return error(`no categories given`);
    for (let [k, v] of Object.entries(categories)) {
        if (v.length < 1)
            return error(`no values given for category a`);
    }

    _categories = categories;
    _tokens = tokens;
    _idx = 0;

    const result = parseExpression();
    if (result.error !== null) return result;
    if (finished()) return valid();
    return error(`unexpected token '${_tokens[_idx]}'`, [_idx, _idx + 1], logicOperators);
}

function parseExpression() {
    // rule: expression -> comparison
    const result = parseComparison();
    if (result.error !== null) return result;
    if (finished()) return valid();

    // rule: expression -> comparison (logicOperator comparison)*
    while (isLogicOperator()) {
        nextToken();
        if (finished())
            return error(`unexpected token '${_tokens[_idx - 1]}'`, [_idx - 1, _idx], []);
        const result = parseComparison();
        if (result.error !== null) return result;
    }
    return valid();
}

function parseComparison() {
    if (isCategory()) {
        // rule: comparison -> category comparator value
        if (_idx + 1 >= _tokens.length)
            return error(`invalid comparison. missing operator.`, [_idx, _idx + 1], []);
        const category = _tokens[_idx];
        const comparator = _tokens[_idx + 1];
        if (!tokensIsComparator(comparator))
            return error(`invalid operator '${comparator}'`, [_idx + 1, _idx + 2], comparisonOperators);
        if (_idx + 2 >= _tokens.length)
            return error(`invalid comparison. missing value.`, [_idx, _idx + 2], []);
        const value = _tokens[_idx + 2];
        if (!isCategoryValue(category, value))
            return error(`invalid ${category}: '${value}'`, [_idx + 2, _idx + 3], _categories[category]);
        _idx += 3;
        return valid();
    } else if (isOpening()) {
        // rule: comparison -> '(' expression ')'
        const from = _idx;
        if (finished())
            return error(`unmatched opening '('`, [from, _idx], []);
        nextToken();
        const result = parseExpression();
        if (result.error !== null) return result;
        if (!isClosing()) return error(`unmatched opening '('`, [from, _idx], []);
        nextToken();
        return valid();
    } else if (finished()) {
        return error(`empty comparison`, [_idx, _idx], []);
    } else {
        return error(`unexpected token '${_tokens[_idx]}'`, [_idx, _idx + 1], Object.keys(_categories));
    }
}

function finished() {
    return _idx === _tokens.length;
}

function nextToken() {
    _idx++;
}

function isLogicOperator() {
    return tokensIsLogicOperator(_tokens[_idx]);
}

function tokensIsComparator(tokens) {
    return comparisonOperators.indexOf(tokens) >= 0;
}

function tokensIsLogicOperator(tokens) {
    return logicOperators.indexOf(tokens) >= 0;
}

function isCategory() {
    return typeof _categories[_tokens[_idx]] !== "undefined";
}

function isCategoryValue(category, value) {
    return _categories[category].indexOf(value) >= 0;
}

function isOpening() {
    return _tokens[_idx] === '(';
}

function isClosing() {
    return _tokens[_idx] === ')';
}

function error(error, range, completions) {
    return { error, range, completions };
}

function valid() {
    return { error: null, range: [], completions: [] };
}

export { parse, parseTokens };