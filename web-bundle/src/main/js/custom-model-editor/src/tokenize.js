/** Symbols used for the tokenization */
const singleCharSymbols = ['(', ')', '<', '>'];
const doubleCharSymbols = ['||', '&&', '==', '!=', '<=', '>='];

/**
 * Finds the token at the given position. Returns an object containing:
 * - token: the token as string or null if there is only whitespace at the given position
 * - range: the position/range of the token (or the whitespace) in the expression as array [startInclusive, endExclusive] 
 */
function tokenAtPos(expression, pos) {
    if (pos < 0 || pos >= expression.length)
        throw `pos ${pos} out of range: [0, ${expression.length}[`;
    const tokens = tokenize(expression);
    for (let i = 0; i < tokens.ranges.length; ++i) {
        const range = tokens.ranges[i];
        if (range[0] <= pos && pos < range[1]) {
            return {
                token: tokens.tokens[i],
                range: range
            }
        } else if (range[0] > pos) {
            return {
                token: null,
                range: i === 0
                    ? [0, range[0]]
                    : [tokens.ranges[i - 1][1], range[0]]
            }
        }
    }
    return {
        token: null,
        range: tokens.ranges.length === 0
            ? [0, expression.length]
            : [tokens.ranges[tokens.ranges.length - 1][1], expression.length]
    };
}

/**
 * Tokenizes the given string/expression and returns the tokens along with their positions
 * given as arrays [start, end].
 * 
 * The expression is split on all whitespace symbols (which are discarded) and 
 * symbols (which are kept as tokens).
 */
function tokenize(expression) {
    let ranges = [];
    let tokens = [];

    // does the actual tokenization work. pos is the current position in the given expression
    // and buffer is the number of characters we found since we terminated the last tokens
    const tokenizeHelper = function (pos, buffer) {
        // break condition: when we get to the end of the expression we push the remaining buffer
        // and return everything we found
        if (pos >= expression.length) {
            push(pos - buffer, pos);
            return {
                ranges: ranges,
                tokens: tokens
            }
        }
        // we recursively extract one symbol or character at a time and repeat the same function for 
        // the remaining expression.. we keep track of how many characters we found since the last symbol
        // or whitespace ('buffer')
        if (isDoubleCharSymbol(expression, pos)) {
            push(pos - buffer, pos);
            push(pos, pos + 2);
            return tokenizeHelper(pos + 2, 0);
        } else if (isSingleCharSymbol(expression, pos)) {
            push(pos - buffer, pos);
            push(pos, pos + 1);
            return tokenizeHelper(pos + 1, 0);
        } else if (isNonWhitespace(expression, pos)) {
            buffer++;
            return tokenizeHelper(pos + 1, buffer);
        } else {
            push(pos - buffer, pos);
            return tokenizeHelper(pos + 1, 0);
        }
    }

    // stores the tokens and ranges we found for the given interval
    const push = function (start, end) {
        if (end > start) {
            tokens.push(expression.slice(start, end));
            ranges.push([start, end]);
        }
    }

    return tokenizeHelper(0, 0);
}

function isNonWhitespace(str, pos) {
    return str.slice(pos, pos + 1).trim() !== '';
}

function isSingleCharSymbol(str, pos) {
    return singleCharSymbols.indexOf(str[pos]) >= 0;
}

function isDoubleCharSymbol(str, pos) {
    return doubleCharSymbols.indexOf(str.slice(pos, pos + 2)) >= 0;
}

export { tokenize, tokenAtPos };