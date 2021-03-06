import {validate} from './validate';

describe("validate", () => {
    test('empty document is valid', () => {
        expect(validate(``).errors).toStrictEqual([]);
    })

    test('root must be an object', () => {
        test_validate(`[]`, [
            `root: must be an object. given type: list, range: [0, 2]`
        ]);
        test_validate(`-\n-\n`, [
            `root: must be an object. given type: list, range: [0, 4]`
        ]);
        test_validate(`abc`, [
            `root: must be an object. given type: string, range: [0, 3]`
        ]);
        test_validate(`'abc'`, [
            `root: must be an object. given type: string, range: [0, 5]`
        ]);
        test_validate(`"abc"`, [
            `root: must be an object. given type: string, range: [0, 5]`
        ]);
        test_validate(`301`, [
            `root: must be an object. given type: number, range: [0, 3]`
        ]);
    });

    test('root keys are not empty', () => {
        test_validate(`:`, [
            `root: keys must not be null, range: [0, 1]`
        ]);
        test_validate(`''  :`, [
            `root: keys must be non-empty and must not only consist of whitespace. given: '', range: [0, 4]`
        ]);
        test_validate(`"    " :`, [
            `root: keys must be non-empty and must not only consist of whitespace. given: '    ', range: [0, 7]`
        ]);
        test_validate(`"\n" :`, [
            `root: keys must be non-empty and must not only consist of whitespace. given: ' ', range: [0, 4]`
        ]);
        test_validate(`"" \t: \n `, [
            `root: keys must be non-empty and must not only consist of whitespace. given: '', range: [0, 4]`
        ]);
    });

    test('root keys are valid', () => {
        test_validate(`abc: def`, [
            `root: possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given: 'abc', range: [0, 3]`
        ]);
        test_validate(`spee: [dprio]`, [
            `root: possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given: 'spee', range: [0, 4]`
        ]);
        test_validate(`[]: abc`, [
            `root: keys must be strings. given type: list, range: [0, 2]`
        ]);
        // todo: this error message is not very helpful unfortunately, maybe it would be better to check
        // one section after the other instead of first checking all root keys?
        test_validate(`speed: \n    multiply_by: 0.3\n  - if: condition`, [
            `root: keys must not be null, range: [0, 46]`
        ]);
    });

    test('root keys are unique', () => {
        test_validate(`speed: [abc]\npriority: [def]\nspeed: [ghi]`, [
            `root: keys must be unique. duplicate: 'speed', range: [29, 34]`
        ]);
    });

    test('root element values are not null', () => {
        // in case values are null we use the key range as error range, because the yaml parser makes it hard for us
        // to obtain the value range in this case
        test_validate(`speed: \ndistance_influence: \npriority\n`, [
            `speed: must not be null, range: [0, 5]`,
            `distance_influence: must not be null, range: [8, 26]`,
            `priority: must not be null, range: [29, 37]`
        ]);
        test_validate(`speed: \n  -`, [
            `speed[0]: must not be null, range: [10, 11]`
        ]);
        test_validate(`speed:\n  - {if: condition, multiply_by: 0.9}\n  -`, [
            `speed[1]: must not be null, range: [47, 48]`
        ]);
    });

    test('speed and priority are lists', () => {
        test_validate(`speed: no_list`, [
            `speed: must be a list. given type: string, range: [7, 14]`
        ])
        test_validate(`speed: \tabc: def`, [
            `speed: must be a list. given type: object, range: [8, 16]`
        ])
        test_validate(`priority: {}`, [
            `priority: must be a list. given type: object, range: [10, 12]`
        ])
    });

    test('distance_influence is a number', () => {
        test_validate(`distance_influence:    \t \n`, [
            `distance_influence: must not be null, range: [0, 18]`
        ])
        test_validate(`distance_influence: []`, [
            `distance_influence: must be a number. given type: list, range: [20, 22]`
        ]);
        test_validate(`distance_influence: abc`, [
            `distance_influence: must be a number. given: 'abc', range: [20, 23]`
        ])
        test_validate(`distance_influence: 3.4abc`, [
            `distance_influence: must be a number. given: '3.4abc', range: [20, 26]`
        ])
        test_validate(`distance_influence: 86`, [])
    });

    test('speed/priority statements keys are valid', () => {
        test_validate(`speed: ['abc']`, [
            `speed[0]: must be an object. given type: string, range: [8, 13]`
        ]);
        test_validate(`speed: [{abc: def}]`, [
            `speed[0]: possible keys: ['if', 'else_if', 'else', 'multiply_by', 'limit_to']. given: 'abc', range: [9, 12]`
        ]);
        test_validate(`speed:\n - multiply_by: 0.9\n  - ele: bla`, [
            `speed[0]: every statement must have a clause ['if', 'else_if', 'else']. given: multiply_by, range: [10, 27]`,
            `speed[1]: possible keys: ['if', 'else_if', 'else', 'multiply_by', 'limit_to']. given: 'ele', range: [31, 34]`
        ]);
        test_validate(`priority: [{if: condition, else: null, multiply_by: 0.3}]`, [
            `priority[0]: too many keys. maximum: 2. given: else,if,multiply_by, range: [11, 56]`
        ]);
        test_validate(`priority: [{if: condition, limit_to: 100}, {if: condition, else: null, multiply_by: 0.3}]`, [
            `priority[1]: too many keys. maximum: 2. given: else,if,multiply_by, range: [43, 88]`
        ]);
        test_validate(`priority: [{limit_to: 100, multiply_by: 0.3}]`, [
            `priority[0]: every statement must have a clause ['if', 'else_if', 'else']. given: limit_to,multiply_by, range: [11, 44]`
        ]);
        test_validate(`priority: [{if: condition1, else_if: condition2}]`, [
            `priority[0]: every statement must have an operator ['multiply_by', 'limit_to']. given: if,else_if, range: [11, 48]`
        ]);
        test_validate(`priority: [{if: condition1, limit_to: 100}, {if: condition2}]`, [
            `priority[1]: every statement must have an operator ['multiply_by', 'limit_to']. given: if, range: [44, 60]`
        ]);
        test_validate(`speed: [ if: condition, limit_to: 100 ]`, [
            `speed[0]: must be an object. given type: pair, range: [9, 22]`,
            `speed[1]: must be an object. given type: pair, range: [24, 38]`
        ]);
    });

    test('speed/priority statements conditions must be strings or booleans (or null for else)', () => {
        test_validate(`speed: [{if: condition, limit_to: 30}, {else: condition, multiply_by: 0.4}]`, [
            `speed[1][else]: must be null. given: 'condition', range: [46, 55]`
        ]);
        test_validate(`priority: [{if : [], multiply_by: 0.4}]`, [
            `priority[0][if]: must be a string or boolean. given type: list, range: [17, 19]`
        ])
        test_validate(`priority: [{if : {}, multiply_by: 0.4}]`, [
            `priority[0][if]: must be a string or boolean. given type: object, range: [17, 19]`
        ])
        test_validate(`priority: [{if : 35, multiply_by: 0.4}]`, [
            `priority[0][if]: must be a string or boolean. given type: number, range: [17, 19]`
        ])
        test_validate(`speed: [{if: condition, multiply_by: 0.2}, {else_if: 3.4, limit_to: 12}]`, [
            `speed[1][else_if]: must be a string or boolean. given type: number, range: [53, 56]`
        ]);
        test_validate(`speed:\n  - if:     \n    limit_to: 100`, [
            `speed[0][if]: must be a string or boolean. given type: null, range: [11, 13]`
        ]);
        test_validate(`speed: [{if: condition, multiply_by: 0.2}, {else_if: , limit_to: 12}]`, [
            `speed[1][else_if]: must be a string or boolean. given type: null, range: [44, 51]`
        ]);
        test_validate(`speed: [{limit_to: 100, if:`, [
            `speed[0][if]: must be a string or boolean. given type: null, range: [24, 26]`
        ]);
        test_validate(`speed: [{if: true, multiply_by: 0.15}]`, []);
    });

    test('get condition ranges', () => {
        const res = validate(`speed: [{if: cond1, limit_to: 50}, {else_if: cond2, multiply_by: 0.3}]\npriority: [{if: cond3, multiply_by: 0.3}]`);
        expect(res.errors).toStrictEqual([]);
        expect(res.conditionRanges).toStrictEqual([[13, 18], [45, 50], [87, 92]]);
    });

    test('get condition range even when if/else_if value is null', () => {
        // this is a bit tricky because normally we do not obtain a condition range for null values and we had
        // to do a little workaround that inserts a condition range also in this case
        expect(validate(`speed: [{if:   , limit_to: 100}]`).conditionRanges).toStrictEqual([[12, 13]]);
        expect(validate(`speed:\n  - if:   \n    limit_to: 100}]`).conditionRanges).toStrictEqual([[14, 15]]);
    });

    test('speed/priority operator values must be numbers', () => {
        test_validate(`speed: [{if: condition, multiply_by: []}]`, [
            `speed[0][multiply_by]: must be a number. given type: list, range: [37, 39]`
        ]);
        test_validate(`priority: [{if: condition, multiply_by: {}}]`, [
            `priority[0][multiply_by]: must be a number. given type: object, range: [40, 42]`
        ]);
        test_validate(`speed: [{if: condition, limit_to: abc}]`, [
            `speed[0][limit_to]: must be a number. given type: string, range: [34, 37]`
        ]);
        test_validate(`speed: [{if: condition, limit_to: }]`, [
            `speed[0][limit_to]: must be a number. given type: null, range: [24, 32]`
        ])
    });

    test('statements must follow certain order', () => {
        test_validate(`speed: [{else: , limit_to: 60}, {if: condition, multiply_by: 0.9}]`, [
            `speed[0]: 'else' clause must be preceded by 'if' or 'else_if', range: [8, 30]`
        ]);
        test_validate(`priority: [{else_if: condition, multiply_by: 0.3}, {else: , limit_to: 30}]`, [
            `priority[0]: 'else_if' clause must be preceded by 'if' or 'else_if', range: [11, 49]`
        ]);
        // multiple else_ifs are possible
        test_validate(`priority: [{if: abc, limit_to: 60}, {else_if: def, multiply_by: 0.2}, {else_if: condition, limit_to: 100}]`, []);
    });

    test('areas is an object', () => {
        test_validate(`areas: []`, [`areas: must be an object. given type: list, range: [7, 9]`]);
        test_validate(`areas: not_an_object`, [`areas: must be an object. given type: string, range: [7, 20]`]);
    });

    test('area names must be strings', () => {
        test_validate(`areas: { : {}}`, [`areas: keys must not be null, range: [7, 14]`]);
        test_validate(`areas: { [] : {}}`, [`areas: keys must be strings. given type: list, range: [9, 12]`]);
        test_validate(`areas: { '' : {}}`, [`areas: keys must be non-empty and must not only consist of whitespace. given: '', range: [9, 12]`]);
        test_validate(`areas: {    "   " : {}}`, [`areas: keys must be non-empty and must not only consist of whitespace. given: '   ', range: [12, 18]`]);
        test_validate(`areas: {    " abc  " : {}}`, [`areas: names may only contain a-z, digits and _. given: ' abc  ', range: [12, 21]`]);
        test_validate(`areas: {    "a bc" : {}}`, [`areas: names may only contain a-z, digits and _. given: 'a bc', range: [12, 19]`]);
        test_validate(`areas: {    "_abc" : {}}`, [`areas: names may only contain a-z, digits and _. given: '_abc', range: [12, 19]`]);
        test_validate(`areas: {    "9abc" : {}}`, [`areas: names may only contain a-z, digits and _. given: '9abc', range: [12, 19]`]);
    });

    test(`area names must be unique`, () => {
        test_validate(`areas:\n  area1: {}\n  area2: {}\n  area1: {}`, [
            `areas: keys must be unique. duplicate: 'area1', range: [33, 38]`
        ]);
    });

    test(`area names get returned correctly`, () => {
        expectAreaNames(`areas:\n  area1: {}\n  area2: {}`, ['area1', 'area2']);
        expectAreaNames(`areas: { x2: {}, p_qr: {}, rst6: {}}`, ['x2', 'p_qr', 'rst6']);
    });

    test('areas must be objects', () => {
        test_validate(`areas: {area1: `, [
            `areas[area1]: must not be null, range: [8, 13]`
        ]);

        test_validate(`areas:\n  area1: []\n  area2: abc\n  area3: 100`, [
            `areas[area1]: must be an object. given type: list, range: [16, 18]`,
            `areas[area2]: must be an object. given type: string, range: [28, 31]`,
            `areas[area3]: must be an object. given type: number, range: [41, 44]`
        ]);
    });

    test('area keys', () => {
        test_validate(`areas:\n  area1:\n    x: y\n    p: q`, [
            `areas[area1]: possible keys: ['type', 'geometry', 'id', 'properties']. given: 'x', range: [20, 21]`,
            `areas[area1]: possible keys: ['type', 'geometry', 'id', 'properties']. given: 'p', range: [29, 30]`
        ]);

        test_validate(`areas:\n  area1:\n    type: y\n    id: q`, [
            `areas[area1]: missing 'geometry'. given: ['type', 'id'], range: [20, 37]`
        ]);

        test_validate(`areas: { area1: { geometry: {}`, [
            `areas[area1]: missing 'type'. given: ['geometry'], range: [16, 30]`
        ]);
    });

    test('area values', () => {
        test_validate(`areas: {area1: { type: 'Future', geometry: }}`, [
            `areas[area1][type]: must be 'Feature'. given: 'Future', range: [23, 31]`,
            `areas[area1][geometry]: must not be null, range: [33, 41]`
        ]);
        test_validate(`areas: {area1: { type: 'Feature', geometry: []}}`, [
            `areas[area1][geometry]: must be an object. given type: list, range: [44, 46]`
        ]);
        const validGeometry = `{type: Polygon, coordinates: [[[1,1], [2,2], [3,3], [1,1]]]}`;
        test_validate(`areas: {area1: { type: 'Feature', geometry: ${validGeometry}, properties: []}}`, [
            `areas[area1][properties]: must be an object. given type: list, range: [118, 120]`
        ]);
        test_validate(`areas: {area1: { type: 'Feature', geometry: ${validGeometry}, id: 123}}`, [
            `areas[area1][id]: must be a string. given type: number, range: [110, 113]`
        ]);
    });

    test('area geometry', () => {
        test_validate(`areas: {area1: { type: 'Feature', geometry: {type: bla} }}`, [
            `areas[area1][geometry]: missing 'coordinates'. given: ['type'], range: [44, 56]`
        ]);
        test_validate(`areas: {area1: { type: 'Feature', geometry: {teip: bla} }}`, [
            `areas[area1][geometry]: possible keys: ['type', 'coordinates']. given: 'teip', range: [45, 49]`
        ]);
        test_validate(`areas: {area1: { type: 'Feature', geometry: {coordinates: [[[1,1], [2,2], [3,3], [1,1]]]} }}`, [
            `areas[area1][geometry]: missing 'type'. given: ['coordinates'], range: [44, 90]`
        ]);
        test_validate(`areas: {area1: { type: 'Feature', geometry: {type: , coordinates: [[[1,1], [2,2], [3,3], [1,1]]]} }}`, [
            `areas[area1][geometry][type]: must not be null, range: [45, 49]`
        ]);
        test_validate(`areas: {area1: { type: 'Feature', geometry: {type: bla, coordinates: [[[1,1], [2,2], [3,3], [1,1]]]} }}`, [
            `areas[area1][geometry][type]: must be 'Polygon'. given: 'bla', range: [51, 54]`
        ]);
        test_validate(`areas: {area1: { type: 'Feature', geometry: {type: 'Polygon', coordinates: } }}`, [
            `areas[area1][geometry][coordinates]: must not be null, range: [62, 73]`
        ]);
        test_validate(`areas: {area1: { type: 'Feature', geometry: {type: "Polygon", coordinates: abc} }}`, [
            `areas[area1][geometry][coordinates]: must be a list. given type: string, range: [75, 78]`
        ]);
    });

    test('area geometry coordinates list', () => {
        test_validate(`areas: {area1: { type: 'Feature', geometry: {type: Polygon, coordinates: []} }}`, [
            `areas[area1][geometry][coordinates]: minimum length: 1, given: 0, range: [73, 75]`
        ]);
        test_validate(`areas: {area1: { type: 'Feature', geometry: {type: Polygon, coordinates: [abc]} }}`, [
            `areas[area1][geometry][coordinates][0]: must be a list. given type: string, range: [74, 77]`
        ]);
        // todo: check range and whitespace again
        test_validate(`areas:\n area1:\n  type: Feature\n  geometry:\n   type: Polygon\n   coordinates: \n    - \n   `, [
            `areas[area1][geometry][coordinates][0]: must not be null, range: [81, 83]`,
        ]);
        test_validate(`areas: {area1: { type: 'Feature', geometry: {type: Polygon, coordinates: [{}, abc]} }}`, [
            `areas[area1][geometry][coordinates][0]: must be a list. given type: object, range: [74, 76]`,
            `areas[area1][geometry][coordinates][1]: must be a list. given type: string, range: [78, 81]`
        ]);
        test_validate(`areas: {area1: { type: 'Feature', geometry: {type: Polygon, coordinates: [[]]} }}`, [
            `areas[area1][geometry][coordinates][0]: minimum length: 4, given: 0, range: [74, 76]`
        ]);
        test_validate(`areas: {area1: { type: 'Feature', geometry: {type: Polygon, coordinates: [[], [], [], []]} }}`, [
            `areas[area1][geometry][coordinates][0]: minimum length: 4, given: 0, range: [74, 76]`,
            `areas[area1][geometry][coordinates][1]: minimum length: 4, given: 0, range: [78, 80]`,
            `areas[area1][geometry][coordinates][2]: minimum length: 4, given: 0, range: [82, 84]`,
            `areas[area1][geometry][coordinates][3]: minimum length: 4, given: 0, range: [86, 88]`
        ]);
        test_validate(`areas: {area1: { type: 'Feature', geometry: {type: Polygon, coordinates: [[[0, 0], [1, p], [2, 2], [x, 0]]]} }}`, [
            `areas[area1][geometry][coordinates][0][1][1]: must be a number, range: [87, 88]`,
            `areas[area1][geometry][coordinates][0][3][0]: must be a number, range: [100, 101]`,
        ]);
        test_validate(`areas: {area1: { type: 'Feature', geometry: {type: Polygon, coordinates: [[[0, 0], [1, 1], [2, 2], [0, 0, p]]]} }}`, [
            `areas[area1][geometry][coordinates][0][3]: maximum length: 2, given: 3, range: [99, 108]`
        ]);
        test_validate(`areas: {area1: { type: 'Feature', geometry: {type: Polygon, coordinates: [[[1, 2], [3, 4], [5, 6], [1.1, 2.2]]]} }}`, [
            `areas[area1][geometry][coordinates][0]: the last point must be equal to the first, range: [99, 109]`
        ]);
        test_validate(`areas: {area1: { type: 'Feature', geometry: {type: Polygon, coordinates: [[[100, 2], [3, 400], [5, 6], [1.1, 2.2]]]} }}`, [
            `areas[area1][geometry][coordinates][0][0][0]: latitude must be in [-90, +90], range: [76, 79]`,
            `areas[area1][geometry][coordinates][0][1][1]: longitude must be in [-180, +180], range: [89, 92]`
        ]);
        test_validate(`areas: {area1: { type: 'Feature', geometry: {type: Polygon, coordinates: [[[1, 2], [3, 4], [5, 6], [1, 2]]]} }}`, []);
    });

    test(`includes yaml parser errors`, () => {
        test_validate_yaml_parser_error(`{}[]`, [
            `syntax: Document contains trailing content not separated by a ... or --- line, range: [2, 4]`
        ]);
        test_validate_yaml_parser_error(`speed: [{if: abc, limit_to: 100`, [
            `syntax: Expected flow map to end with }, range: [28, 31]`,
            `syntax: Expected flow sequence to end with ], range: [8, 31]`
        ]);
    });
});

function expectAreaNames(doc, areas) {
    try {
        const res = validate(doc);
        expect(res.areas).toStrictEqual(areas);
    } catch (e) {
        Error.captureStackTrace(e, expectAreaNames);
        throw e;
    }
}

function test_validate(doc, errors) {
    // previously we put the path and message into one string. later we added range. this is a quick way to re-use the existing tests.
    // to clean this up we should check `path`, `message` and `range` separately...
    try {
        expect(validate(doc).errors.map(e => {
            if (!e.range) {
                fail(`test_validate: undefined error.range for error: ${e.path}: ${e.message}`);
                return;
            }
            return `${e.path}: ${e.message}, range: [${e.range[0]}, ${e.range[1]}]`;
        })).toStrictEqual(errors);
    } catch (e) {
        Error.captureStackTrace(e, test_validate);
        throw e;
    }
}

function test_validate_yaml_parser_error(doc, errors) {
    // these are syntax errors that are caught by the yaml parser, but are not handled explicitly by our code
    try {
        expect(validate(doc).yamlErrors.map(e => {
            if (!e.range) {
                fail(`test_validate_yaml_parser_error: undefined error.range for error: ${e.path}: ${e.message}`);
                return;
            }
            return `${e.path}: ${e.message}, range: [${e.range[0]}, ${e.range[1]}]`;
        })).toStrictEqual(errors);
    } catch (e) {
        Error.captureStackTrace(e, test_validate_yaml_parser_error);
        throw e;
    }
}