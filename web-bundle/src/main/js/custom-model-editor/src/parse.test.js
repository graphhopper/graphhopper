import {parse, parseTokens} from './parse';

const categories = {
    "a": {type: 'enum', values: ['a1', 'a2', 'a3']},
    "b": {type: 'enum', values: ['b1', 'b2']},
    "num1": {type: 'numeric'},
    "num2": {type: 'numeric'},
    "bool1": {type: 'boolean'},
    "bool2": {type: 'boolean'}
}
const areas = ["area1", "area2", "area3"];
const allowedLefts = Object.keys(categories).concat(areas.map(a => 'in_' + a)).concat(['true', 'false']);

describe("parse", () => {

    test("empty categories", () => {
        expect(parseTokens('a == a1', {a: {type: 'enum', values: []}}).error)
            .toBe(`no values given for enum category a`);
    });

    test("parse single comparison, valid", () => {
        test_parseTokens_valid(['a', '==', 'a1']);
        test_parseTokens_valid(['b', '==', 'b2']);
        test_parseTokens_valid(['a', '!=', 'a1']);
        test_parseTokens_valid(['num1', '<=', '0.8']);
        test_parseTokens_valid(['num2', '==', '0.8']);
        test_parseTokens_valid(['num2', '>', '-0.8']);
        test_parseTokens_valid(['num1', '<', '0.8']);
        test_parseTokens_valid(['(', 'num2', '>', '0.6', ')']);
        test_parseTokens_valid(['bool1', '!=', 'true']);
        test_parseTokens_valid(['bool2', '==', 'false']);
        test_parseTokens_valid(['(', 'a', '==', 'a1', ')']);
        test_parseTokens_valid(['(', 'a', '!=', 'a1', ')']);
        test_parseTokens_valid(['(', '(', 'a', '!=', 'a1', ')', ')']);
        test_parseTokens_valid(['in_area1']);
        test_parseTokens_valid(['(', 'in_area1', ')']);
        test_parseTokens_valid(['in_area2', '==', 'true']);
        test_parseTokens_valid(['(', 'in_area1', '!=', 'false', ')']);
        test_parseTokens_valid(['true']);
        test_parseTokens_valid(['(', 'false', ')']);
        test_parseTokens_valid(['bool1']);
        test_parseTokens_valid(['(', 'bool2', ')']);
    });

    test("parse single comparison, invalid", () => {
        test_parseTokens([], 'empty comparison', [0, 0], []);
        test_parseTokens(['a', '!=', 'b', 'c'], `invalid a: 'b'`, [2, 3], ['a1', 'a2', 'a3']);
        test_parseTokens(['(', 'a', '!=', 'b1', ')'], `invalid a: 'b1'`, [3, 4], ['a1', 'a2', 'a3']);
        test_parseTokens(['b', '!=', 'a3'], `invalid b: 'a3'`, [2, 3], ['b1', 'b2']);
        test_parseTokens(['a', '==', '404'], `invalid a: '404'`, [2, 3], ['a1', 'a2', 'a3']);
        test_parseTokens(['a', '=='], `invalid comparison. missing value.`, [0, 2], []);
        test_parseTokens(['a'], `invalid comparison. missing operator.`, [0, 1], []);
        test_parseTokens(['a', '=', 'a1'], `invalid operator '='`, [1, 2], ['==', '!=']);
        test_parseTokens(['404', '==', 'a1'], `unexpected token '404'`, [0, 1], allowedLefts);
        test_parseTokens(['(', 'a', '==', 'a1'], `unmatched opening '('`, [0, 4], []);
        test_parseTokens(['(', 'a', '==', 'a1', ')', ')'], `unexpected token ')'`, [5, 6], ['||', '&&']);
        test_parseTokens(['(', ')'], `unexpected token ')'`, [1, 2], allowedLefts);
        test_parseTokens(['(', '(', ')', ')'], `unexpected token ')'`, [2, 3], allowedLefts);
        test_parseTokens(['('], `empty comparison`, [1, 1], []);
        test_parseTokens(['(', '('], `empty comparison`, [2, 2], []);
        test_parseTokens([')'], `unexpected token ')'`, [0, 1], allowedLefts);
        test_parseTokens([')', ')'], `unexpected token ')'`, [0, 1], allowedLefts);
        test_parseTokens(['(', 'a', '==', 'a1', ')', 'a1'], `unexpected token 'a1'`, [5, 6], ['||', '&&']);
        test_parseTokens(['(', 'a', '==', ')', 'a1'], `invalid a: ')'`, [3, 4], ['a1', 'a2', 'a3']);
        test_parseTokens(['a', '(', '==', 'a1', ')'], `invalid operator '('`, [1, 2], ['==', '!=']);
        // here it would be nice to recognize that the right side is valid and 'only' the a category on the far left
        // makes the expression invalid, but probably this is just how it is because we parse from left to right(?)
        test_parseTokens(['a', '(', 'b', '&&', 'b1', ')'], `invalid operator '('`, [1, 2], ['==', '!=']);
    });

    test("parse single comparison, invalid, area", () => {
        test_parseTokens(['in_'], `unknown area: ''`, [0, 1], ['in_area1', 'in_area2', 'in_area3']);
        test_parseTokens(['in_area404'], `unknown area: 'area404'`, [0, 1], ['in_area1', 'in_area2', 'in_area3']);
        test_parseTokens(['area2'], `area names must be prefixed with 'in_'`, [0, 1], ['in_area1', 'in_area2', 'in_area3']);
        test_parseTokens(['in_area1', '<=', 'true'], `unexpected token '<='`, [1, 2], ['||', '&&']);
    });

    test("parse single comparison, invalid, numeric and boolean", () => {
        test_parseTokens(['a', '<', 'a1'], `invalid operator '<'`, [1, 2], ['==', '!=']);
        test_parseTokens(['bool1', '>=', 'true'], `unexpected token '>='`, [1, 2], ['||', '&&']);
        test_parseTokens(['num', '>', '0.5'], `unexpected token 'num'`, [0, 1], allowedLefts);
        test_parseTokens(['num2', '<', 'xyz'], `invalid num2: 'xyz'`, [2, 3], ['__hint__type a number']);
        test_parseTokens(['bool1', '<=', 'true'], `unexpected token '<='`, [1, 2], ['||', '&&']);
    });

    test("parse multiple comparisons, valid", () => {
        test_parseTokens_valid(['a', '==', 'a1', '&&', 'b', '==', 'b1']);
        test_parseTokens_valid(['a', '==', 'a1', '||', 'b', '==', 'b1', '&&', 'a', '!=', 'a2']);
        test_parseTokens_valid(['a', '==', 'a1', '||', '(', 'b', '==', 'b1', ')', '&&', 'a', '!=', 'a2']);
        test_parseTokens_valid(['in_area3', '&&', 'a', '==', 'a1']);
        test_parseTokens_valid(['b', '!=', 'b1', '||', 'in_area3']);
        test_parseTokens_valid(['b', '!=', 'b1', '||', 'in_area2', '!=', 'true']);
        test_parseTokens_valid(['bool1', '==', 'false', '&&', 'bool2', '||', 'bool1']);
    });

    test("parse multiple comparisons, invalid", () => {
        test_parseTokens(['x'], `unexpected token 'x'`, [0, 1], allowedLefts);
        test_parseTokens(['(', 'x'], `unexpected token 'x'`, [1, 2], allowedLefts);
        test_parseTokens(['&&'], `unexpected token '&&'`, [0, 1], allowedLefts);
        test_parseTokens(['(', '&&'], `unexpected token '&&'`, [1, 2], allowedLefts);
        test_parseTokens(['a', '==', 'a1', 'x'], `unexpected token 'x'`, [3, 4], ['||', '&&']);
        test_parseTokens(['a', '==', 'a1', '&&'], `unexpected token '&&'`, [3, 4], []);
        test_parseTokens(['a', '==', 'a1', '&&', 'x'], `unexpected token 'x'`, [4, 5], allowedLefts);
        test_parseTokens(['(', 'a', '==', 'a1', ')', '&&', 'x'], `unexpected token 'x'`, [6, 7], allowedLefts);
        test_parseTokens(['(', 'a', '==', 'a1', ')', '&&', '(', 'x'], `unexpected token 'x'`, [7, 8], allowedLefts);
        test_parseTokens(['a', '==', 'a1', '||', 'b'], `invalid comparison. missing operator.`, [4, 5], []);
        test_parseTokens(['a', '==', 'a1', '&&', '(', 'b', '==', 'b1'], `unmatched opening '('`, [4, 8], []);
        test_parseTokens(['a', '==', 'a1', '||', 'b', '!=', 'a1', '&&', 'a', '!=', 'a2'], `invalid b: 'a1'`, [6, 7], ['b1', 'b2']);
        test_parseTokens(['a', '==', 'a1', '||', 'b', '!=', 'b1', '&&', '(', '(', 'c', '!=', 'a2', ')', ')'], `unexpected token 'c'`, [10, 11], allowedLefts);
        test_parseTokens(['a', 'x'], `invalid operator 'x'`, [1, 2], ['==', '!=']);
        test_parseTokens(['a', '&&'], `invalid operator '&&'`, [1, 2], ['==', '!=']);
        test_parseTokens(['a', '('], `invalid operator '('`, [1, 2], ['==', '!=']);
        test_parseTokens(['a', '!=', '('], `invalid a: '('`, [2, 3], ['a1', 'a2', 'a3']);
    });

    test("parse multiple comparisons, with parentheses, valid", () => {
        test_parseTokens_valid(['a', '==', 'a1', '&&', '(', 'b', '==', 'b1', '||', 'a', '!=', 'a1', ')']);
        test_parseTokens_valid(['a', '==', 'a1', '&&', '(', 'b', '==', 'b1', '||', 'a', '!=', 'a1', ')', '||', 'b', '!=', 'b2']);
    });

    test("parse, valid", () => {
        test_parse_valid('a==a1');
        test_parse_valid('(a==a1)');
        test_parse_valid('(a==a1)||(b==b1)');
        test_parse_valid('(a==a1&&b==b1)');
        test_parse_valid('((a==a1)||b==b1)');
        test_parse_valid('(a==a1)||b!=b1')
        test_parse_valid('a==a1&&((b==b1))');
        test_parse_valid('( a==a1 ) || b != b1 && ( a==a2 ||\n b!=b1 && (a==a1 || b == b2)) && a == a1');
        test_parse_valid('num1 > 0.3 && bool1 != true || a == a2 && num2 > 0.5 || bool1 == false');
        test_parse_valid('num1>0.3 && num2<12 || num1>=0.5 && num2<=1.3');
        test_parse_valid('num1>0.3 && in_area2 || num2<=1.3');
        test_parse_valid('num1>0.3 && bool1');
        test_parse_valid('a != a1 && (bool1 || (bool2 && in_area2))');
        test_parse_valid('a != a1 && (bool1 != false || (in_area2 && bool2))');
        test_parse_valid('a != a1 && (bool1 != false || ((in_area1 == false) && bool2))');
    });

    test("parse, invalid", () => {
        test_parse('xyz', `unexpected token 'xyz'`, [0, 3], allowedLefts);
        test_parse('a==a1( b!=b1', `unexpected token '('`, [5, 6], ['||', '&&']);
        test_parse('(a==a1)(b!=b2)', `unexpected token '('`, [7, 8], ['||', '&&']);
        test_parse('((a==&&a1)(b!=b1)', `invalid a: '&&'`, [5, 7], ['a1', 'a2', 'a3']);
        test_parse('((a!=a1||)))', `unexpected token ')'`, [9, 10], allowedLefts);
        test_parse('\na\t(||', `invalid operator '('`, [3, 4], ['==', '!=']);
        test_parse('a== a1(b!=b2||', `unexpected token '('`, [6, 7], ['||', '&&']);
        test_parse('||', `unexpected token '||'`, [0, 2], allowedLefts);
        test_parse('a==a1||', `unexpected token '||'`, [5, 7], []);
        test_parse('a==a1&&(', `empty comparison`, [8, 8], []);
        test_parse(' (', `empty comparison`, [2, 2], []);
        test_parse('a == a1 || num1 != b1', `invalid num1: 'b1'`, [19, 21], ['__hint__type a number']);
        test_parse(`a == a1 || bool2 == 'false'`, `invalid bool2: ''false''`, [20, 27], ['true', 'false']);
        test_parse(`b != b1 || num2 > 0.7 && bool1 != 'true'`, `invalid bool1: ''true''`, [34, 40], ['true', 'false']);
        test_parse('a == a2 && b <= b1', `invalid operator '<='`, [13, 15], ['==', '!=']);
        test_parse('a == a1 || area1', `area names must be prefixed with 'in_'`, [11, 16], ['in_area1', 'in_area2', 'in_area3']);
        test_parse('a == a1 || area_1', `unexpected token 'area_1'`, [11, 17], allowedLefts);
        test_parse(' == bool1', `unexpected token '=='`, [1, 3], allowedLefts);
        test_parse('bool1 != a2', `invalid bool1: 'a2'`, [9, 11], ['true', 'false']);
        test_parse('in_area1 == tru || a == a1', `invalid in_area1: 'tru'`, [12, 15], ['true', 'false']);
    });

    function test_parse_valid(expression) {
        const res = parse(expression, categories, areas);
        check(test_parse_valid, res, null, [], []);
    }

    function test_parse(expression, error, range, completions) {
        const res = parse(expression, categories, areas);
        check(test_parse, res, error, range, completions);
    }

    function test_parseTokens_valid(tokens) {
        const res = parseTokens(tokens, categories, areas);
        check(test_parseTokens_valid, res, null, [], []);
    }

    function test_parseTokens(tokens, error, range, completions) {
        const res = parseTokens(tokens, categories, areas);
        check(test_parseTokens, res, error, range, completions);
    }

    function check(fun, res, error, range, completions) {
        try {
            expect(res.error).toBe(error);
            expect(res.range).toStrictEqual(range);
            expect(res.completions).toStrictEqual(completions);
        } catch (e) {
            // this is a little trick to show a more useful stack-trace: https://kentcdodds.com/blog/improve-test-error-messages-of-your-abstractions
            Error.captureStackTrace(e, fun);
            throw e;
        }
    }
});