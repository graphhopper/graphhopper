import {tokenize} from './tokenize';

const comparisonOperators = ['==', '!='];
const logicOperators = ['||', '&&'];

let _categories;
let _tokens;
let _idx;

/**
 * Tokenizes and then parses the given expression
 */
function parse(expression, categories) {
    const tokens = tokenize(expression);
    const result = parseTokens(tokens.tokens, categories);
    // todo: translate token ranges to character ranges!
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
 *  - allow querying for completions at certain tokens (not just return errors)
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

export {parse, parseTokens};