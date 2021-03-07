import {completeYaml, getYamlPath} from "./yaml_complete";

const rootElements = [`speed`, `priority`, `distance_influence`, `areas`];
const statementElements = [`if`, `else_if`, `else`, `limit_to`, `multiply_by`];

describe('complete_yaml', () => {
    test('root elements', () => {
        test_complete(``, 0, rootElements, [0, 1]);
        test_complete(` `, 0, rootElements, [0, 2]);
        test_complete(`x`, 0, rootElements, [0, 1]);
        test_complete(`speed:`, 6, rootElements, [0, 7]);
        test_complete(`speed: `, 6, [], []);
        test_complete(`speed: []`, 5, rootElements, [0, 5]);
        test_complete(`speed: []`, 6, [], []);
        // only if our cursor is separated by at least a space the current token is not the key but the (speed) value
        test_complete(`speed:  `, 7, [], []);
        test_complete(`speed`, 5, rootElements, [0, 6]);
        test_complete(`speed `, 5, rootElements, [0, 7]);
        test_complete(`priority: []`, 2, rootElements, [0, 8]);
        test_complete(`speed: []\n\n\ndistance_influence: 10`, 10, rootElements.filter(s => s !== 'speed' && s !== 'distance_influence'), [10, 11]);
        test_complete(`speed: []\nx\ndistance_influence: 10`, 10, rootElements.filter(s => s !== 'speed' && s !== 'distance_influence'), [10, 11]);
        test_complete(`{\nspeed: [], x`, 13, rootElements.filter(s => s !== 'speed'), [13, 14]);
        // range can exceed the current line, but this is handled later in custom model editor, note how the 'key'
        // can span multiple lines in yaml
        test_complete(` \nareas: []`, 0, rootElements, [0, 8]);
        test_complete(` \nareas: []`, 1, rootElements, [1, 8]);
        test_complete(` \nareas: []`, 2, rootElements, [2, 7]);
        test_complete(`\nareas: []`, 0, rootElements, [0, 7]);
    });

    test('statements', () => {
        test_complete(`speed: [ { x`, 11, statementElements, [11, 12]);
        test_complete(`priority: [ {}, { x`, 18, statementElements, [18, 19]);
        test_complete(`speed: [ { a: b, x`, 17, statementElements, [17, 18]);
        // this one is a bit unfortunate, it is correct yaml for a list even without the newline and ` - `,
        // but really it is not very helpful
        // todo: fix this by differentiating between seq and flow_seq?
        test_complete(`speed: [ x`, 9, statementElements, [9, 10]);
        // ... like this we need it
        test_complete(`speed:\n  - x`, 11, statementElements, [11, 12]);
        test_complete(`priority:\n  - if: abc\n    limit_to: 30\n  - x`, 43, statementElements, [43, 44]);
        test_complete(`priority:\n  - if: abc\n    xyz123`, 27, [`limit_to`, `multiply_by`], [26, 32]);
        test_complete(`priority:\n  - limit_to: 100\n    xyz123`, 33, [`if`, `else_if`, `else`], [32, 38]);
        test_complete(`speed:\n  - if: abc\n    x`, 23, [`limit_to`, `multiply_by`], [23, 24]);
        test_complete(`speed:\n  - if: abc\n     `, 23, [`limit_to`, `multiply_by`], [23, 25]);
        test_complete(`speed:\n  - if: abc\n    `, 23, [`limit_to`, `multiply_by`], [23, 24]);
        // conditions
        test_complete(`speed: [ {if: x`, 14, [`__hint__type a condition`], [14, 15]);
        test_complete(`speed:\n  - if: x`, 15, [`__hint__type a condition`], [15, 16]);
        test_complete(`speed:\n  - if: abc\n    limit_to: 30\n  - else_if: my_condition`, 51, [`__hint__type a condition`], [49, 61]);
        // operator values
        test_complete(`speed: [{if: abc, limit_to:  x`, 29, [`__hint__type a number`], [29, 30]);
        test_complete(`priority:\n  - if: abc\n    multiply_by: x`, 39, [`__hint__type a number`], [39, 40]);
    });

    test(`distance_influence`, () => {
        test_complete(`distance_influence: x`, 20, [`__hint__type a number`], [20, 21]);
        test_complete(`distance_influence: x\nspeed: []`, 20, [`__hint__type a number`], [20, 21]);
        test_complete(`distance_influence: 123\nspeed: []`, 21, [`__hint__type a number`], [20, 23]);
    });

    test(`areas`, () => {
        test_complete(`areas:\n  x`, 9, [`__hint__type an area name`], [9, 10]);
        test_complete(`areas:\n  berlin: {}\n  x`, 22, [`__hint__type an area name`], [22, 23]);
        test_complete(`areas:\n  berlin:\n    x`, 21, [`geometry`, `type`], [21, 22]);
        test_complete(`areas:\n  berlin: {}\n  munich:\n    x`, 36, [`geometry`, `type`], [34, 36]);
        test_complete(`areas:\n  berlin:\n    type: x`, 27, [`Feature`], [27, 28])
        test_complete(`areas:\n  berlin:\n    type: Feature\n    x`, 39, [`geometry`], [39, 40]);
    });

    test(`areas geometry`, () => {
        test_complete(`areas:\n berlin:\n  geometry:\n   x`, 32, [`type`, `coordinates`], [31, 33]);
        test_complete(`areas:\n berlin:\n  geometry:\n   type: x`, 37, [`Polygon`], [37, 38]);
        test_complete(`areas:\n berlin:\n  geometry:\n   type: Polygon\n   x`, 49, [`coordinates`], [48, 50]);
    });

    test(`handle pair`, () => {
        test_complete(`areas:\n hanau: {\n   geometry: coordinates\n   type:  \n}`, 52, [], []);
    });
});

function test_complete(content, pos, suggestions, range) {
    try {
        const result = completeYaml(content, pos);
        expect(result.suggestions).toStrictEqual(suggestions);
        expect(result.range).toStrictEqual(range);
    } catch (e) {
        Error.captureStackTrace(e, test_complete);
        throw e;
    }
}

describe("get_yaml_path", () => {
    test('root elements', () => {
        test_path(`x`, 0, [`root`, `x`], [0, 1]);
        test_path(`x`, 1, [`root`, `x`], [0, 1]);
        test_path(`x `, 1, [`root`, `x`], [0, 2]);
        test_path(`{  x `, 3, [`root`, `map`, `pair`, `x`], [3, 5]);
        test_path(`speed: [ x`, 9, [`root`, `map`, `pair[speed]`, `list[0]`, `x`], [9, 10]);
        test_path(`speed: []\nx`, 10, [`root`, `map`, `pair`, `x`], [10, 11]);
        test_path(`speed: []\nx\ndistance_influence: 10`, 10, [`root`, `map`, `pair`, `x`], [10, 11]);
        test_path(`{\nspeed: [], x`, 13, [`root`, `map`, `pair`, `x`], [13, 14]);
    });

    test('statements', () => {
        test_path(`speed: [ { x`, 11, [`root`, `map`, `pair[speed]`, `list[0]`, `map`, `pair`, `x`], [11, 12]);
        test_path(`priority: [ {}, { x`, 18, [`root`, `map`, `pair[priority]`, `list[1]`, `map`, `pair`, `x`], [18, 19]);
        test_path(`speed: [ { a: b, x`, 17, [`root`, `map`, `pair[speed]`, `list[0]`, `map`, `pair`, `x`], [17, 18]);
        test_path(`priority:\n  - if: abc\n    limit_to: 30\n  - x`, 43, [`root`, `map`, `pair[priority]`, `list[1]`, `x`], [43, 44]);
        test_path(`priority:\n  - if: abc\n    xyz`, 27, [`root`, `map`, `pair[priority]`, `list[0]`, `map`, `pair`, `xyz`], [26, 29]);
        // conditions
        test_path(`speed: [ {if: x`, 14, [`root`, `map`, `pair[speed]`, `list[0]`, `map`, `pair[if]`, `x`], [14, 15]);
        test_path(`speed:\n  - if: x`, 15, [`root`, `map`, `pair[speed]`, `list[0]`, `map`, `pair[if]`, `x`], [15, 16]);
        test_path(`speed:\n  - if: abc\n    limit_to: 30\n  - else_if: my_condition`, 51, [`root`, `map`, `pair[speed]`, `list[1]`, `map`, `pair[else_if]`, `my_condition`], [49, 61]);
        // operator values
        test_path(`speed: [{if: abc, limit_to:  x`, 29, [`root`, `map`, `pair[speed]`, `list[0]`, `map`, `pair[limit_to]`, `x`], [29, 30]);
        test_path(`priority:\n  - if: abc\n    multiply_by: x`, 39, [`root`, `map`, `pair[priority]`, `list[0]`, `map`, `pair[multiply_by]`, `x`], [39, 40]);
    });

    test(`distance_influence`, () => {
        test_path(`distance_influence: x`, 20, [`root`, `map`, `pair[distance_influence]`, `x`], [20, 21]);
    });

    test(`areas`, () => {
        test_path(`areas:\n  x`, 9, [`root`, `map`, `pair[areas]`, `x`], [9, 10]);
        test_path(`areas:\n  berlin:\n    x`, 21, [`root`, `map`, `pair[areas]`, `map`, `pair[berlin]`, `x`], [21, 22]);
        test_path(`areas:\n  berlin:\n    type: Feature\n    x`, 39, [`root`, `map`, `pair[areas]`, `map`, `pair[berlin]`, `map`, `pair`, `x`], [39, 40]);
        test_path(`areas:\n  berlin:\n    type: Feature\n    geometry: []\n  x`, 54, [`root`, `map`, `pair[areas]`, `map`, `pair`, `x`], [54, 55]);
    });
});

function test_path(content, pos, expectedSignature, expectedTokenRange) {
    try {
        const yamlPath = getYamlPath(content, pos);
        expect(yamlPath.signature).toStrictEqual(expectedSignature);
        expect(yamlPath.tokenRange).toStrictEqual(expectedTokenRange);
    } catch (e) {
        Error.captureStackTrace(e, test_path);
        throw e;
    }
}