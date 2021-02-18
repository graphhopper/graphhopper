import { tokenize, tokenAtPos } from './tokenize';

describe("tokenize", () => {
    test("extract token", () => {
        expect(tokenize('\n\ta ==(a1 ').tokens).toStrictEqual(['a', '==', '(', 'a1']);
        expect(tokenize(' (a==a1 )&&').tokens).toStrictEqual(['(', 'a', '==', 'a1', ')', '&&']);
        expect(tokenize(' ( a&&== a1)) ').tokens).toStrictEqual(['(', 'a', '&&', '==', 'a1', ')', ')']);
        expect(tokenize('(a==a1)||(b==b1)').tokens).toStrictEqual(['(', 'a', '==', 'a1', ')', '||', '(', 'b', '==', 'b1', ')']);
        expect(tokenize('(a<=a1)||(b>b1)').tokens).toStrictEqual(['(', 'a', '<=', 'a1', ')', '||', '(', 'b', '>', 'b1', ')']);
    });

    test("extract tokens and ranges", () => {
        test_tokenize('abc', ['abc'], [[0, 3]]);
        test_tokenize('a bc', ['a', 'bc'], [[0, 1], [2, 4]]);
        test_tokenize('a b\tc', ['a', 'b', 'c'], [[0, 1], [2, 3], [4, 5]]);
        test_tokenize('\na b\tc', ['a', 'b', 'c'], [[1, 2], [3, 4], [5, 6]]);
        test_tokenize('==a', ['==', 'a'], [[0, 2], [2, 3]]);
        test_tokenize('<a', ['<', 'a'], [[0, 1], [1, 2]]);
        test_tokenize('<=>=b', ['<=', '>=', 'b'], [[0, 2], [2, 4], [4, 5]]);
        test_tokenize('==(a', ['==', '(', 'a'], [[0, 2], [2, 3], [3, 4]]);
        test_tokenize(' a!=)b', ['a', '!=', ')', 'b'], [[1, 2], [2, 4], [4, 5], [5, 6]])
        test_tokenize('ab   xy \n \t zz', ['ab', 'xy', 'zz'], [[0, 2], [5, 7], [12, 14]]);
        test_tokenize('   abc   def\t\n\n', ['abc', 'def'], [[3, 6], [9, 12]]);
        test_tokenize('ab( c)(  (a ) ', ['ab', '(', 'c', ')', '(', '(', 'a', ')'], [[0, 2], [2, 3], [4, 5], [5, 6], [6, 7], [9, 10], [10, 11], [12, 13]]);
    });

    test("get token at position", () => {
        test_tokenAtPos('  ', 0, null, [0, 2]);
        test_tokenAtPos('   ', 1, null, [0, 3]);
        test_tokenAtPos('abc', 0, 'abc', [0, 3]);
        test_tokenAtPos('abc', 1, 'abc', [0, 3]);
        test_tokenAtPos('abc def', 0, 'abc', [0, 3]);
        test_tokenAtPos('abc def', 3, null, [3, 4]);
        test_tokenAtPos('abc def', 4, 'def', [4, 7]);
        test_tokenAtPos('abc def', 5, 'def', [4, 7]);
        test_tokenAtPos('  abc def  ', 0, null, [0, 2]);
        test_tokenAtPos('  abc def  ', 1, null, [0, 2]);
        test_tokenAtPos('  abc def  ', 2, 'abc', [2, 5]);
        test_tokenAtPos('  abc def  ', 5, null, [5, 6]);
        test_tokenAtPos('  abc def  ', 7, 'def', [6, 9]);
        test_tokenAtPos('  abc def  ', 9, null, [9, 11]);
        test_tokenAtPos('  abc def  ', 10, null, [9, 11]);
    });

    test("get token at position, with symbols", () => {
        test_tokenAtPos('a||c', 0, 'a', [0, 1]);
        test_tokenAtPos('a&&c', 1, '&&', [1, 3]);
        test_tokenAtPos('a!=c', 3, 'c', [3, 4]);
        test_tokenAtPos('a>=c', 2, '>=', [1, 3]);
        test_tokenAtPos('a<c', 1, '<', [1, 2]);
        test_tokenAtPos('  abc== d &&(e != xy(z)', 0, null, [0, 2]);
        test_tokenAtPos('  abc== d &&(e != xy(z)', 6, '==', [5, 7]);
        test_tokenAtPos('  abc== d &&(e != xy(z)', 12, '(', [12, 13]);
        test_tokenAtPos('  abc== d &&(e != xy(z)', 14, null, [14, 15]);
        test_tokenAtPos('  abc== d &&(e != xy(z)', 18, 'xy', [18, 20]);
        test_tokenAtPos('  abc==\n\n\tdef\n&&', 7, null, [7, 10]);
        test_tokenAtPos('  abc==\n\n\tdef\n&&', 12, 'def', [10, 13]);
    });

    function test_tokenAtPos(expression, pos, token, range) {
        const tokenPos = tokenAtPos(expression, pos);
        try {
            expect(tokenPos.token).toBe(token);
            expect(tokenPos.range).toStrictEqual(range);
        } catch (e) {
            Error.captureStackTrace(e, test_tokenAtPos);
            throw e;
        }
    }

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