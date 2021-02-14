import {tokenize} from './tokenize';

describe("tokenize", () => {
    test("extract token", () => {
        expect(tokenize('\n\ta ==(a1 ').tokens).toStrictEqual(['a', '==', '(', 'a1']);
        expect(tokenize(' (a==a1 )&&').tokens).toStrictEqual(['(', 'a', '==', 'a1', ')', '&&']);
        expect(tokenize(' ( a&&== a1)) ').tokens).toStrictEqual(['(', 'a', '&&', '==', 'a1', ')', ')']);
        expect(tokenize('(a==a1)||(b==b1)').tokens).toStrictEqual(['(', 'a', '==', 'a1', ')', '||', '(', 'b', '==', 'b1', ')']);
    });

    test("extract tokens and ranges", () => {
        test_tokenize('abc', ['abc'], [[0, 3]]);
        test_tokenize('a bc', ['a', 'bc'], [[0, 1], [2, 4]]);
        test_tokenize('a b\tc', ['a', 'b', 'c'], [[0, 1], [2, 3], [4, 5]]);
        test_tokenize('\na b\tc', ['a', 'b', 'c'], [[1, 2], [3, 4], [5, 6]]);
        test_tokenize('==a', ['==', 'a'], [[0, 2], [2, 3]]);
        test_tokenize('==(a', ['==', '(', 'a'], [[0, 2], [2, 3], [3, 4]]);
        test_tokenize(' a!=)b', ['a', '!=', ')', 'b'], [[1, 2], [2, 4], [4, 5], [5, 6]])
        test_tokenize('ab   xy \n \t zz', ['ab', 'xy', 'zz'], [[0, 2], [5, 7], [12, 14]]);
        test_tokenize('   abc   def\t\n\n', ['abc', 'def'], [[3, 6], [9, 12]]);
        test_tokenize('ab( c)(  (a ) ', ['ab', '(', 'c', ')', '(', '(', 'a', ')'], [[0, 2], [2, 3], [4, 5], [5, 6], [6, 7], [9, 10], [10, 11], [12, 13]]);
    });

    function test_tokenize(expression, token, ranges) {
        const res = tokenize(expression, token);
        try {
            expect(res.tokens).toStrictEqual(token);
            expect(res.ranges).toStrictEqual(ranges);
        } catch (e) {
            Error.captureStackTrace(e, test_tokenize);
            throw e;
        }
    }
});