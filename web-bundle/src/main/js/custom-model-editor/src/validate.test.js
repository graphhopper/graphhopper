import {validate} from './validate';

describe("validate", () => {
    test('empty document is valid', () => {
        expect(validate(``).errors).toStrictEqual([]);
    })

    test('root must be an object', () => {
        test_validate(`[]`, [
            `root: must be an object. possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given type: list, range: [0, 2]`
        ]);
        test_validate(`-\n-\n`, [
            `root: must be an object. possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given type: list, range: [0, 4]`
        ]);
        test_validate(`abc`, [
            `root: must be an object. possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given type: string, range: [0, 3]`
        ]);
        test_validate(`'abc'`, [
            `root: must be an object. possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given type: string, range: [0, 5]`
        ]);
        test_validate(`"abc"`, [
            `root: must be an object. possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given type: string, range: [0, 5]`
        ]);
        test_validate(`301`, [
            `root: must be an object. possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given type: number, range: [0, 3]`
        ]);
    });

    test('root keys are not empty', () => {
        test_validate(`:`, [
            `root: possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given: null, range: [0, 1]`
        ]);
        test_validate(`''  :`, [
            `root: possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given: '', range: [0, 4]`
        ]);
        test_validate(`"    " :`, [
            `root: possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given: '    ', range: [0, 7]`
        ]);
        test_validate(`"\n" :`, [
            `root: possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given: ' ', range: [0, 4]`
        ]);
        test_validate(`"" \t: \n `, [
            `root: possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given: '', range: [0, 4]`
        ]);
    });

    test('root keys are valid', () => {
        test_validate(`abc: def`, [
            `root: possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given: 'abc', range: [0, 3]`
        ]);
        test_validate(`spee: [dprio]`, [
            `root: possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given: 'spee', range: [0, 4]`
        ]);
        // todo: this error message is not very helpful unfortunately, maybe it would be better to check
        // one section after the other instead of first checking all root keys?
        test_validate(`speed: \n    multiply_by: 0.3\n  - if: condition`, [
            `root: possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given: null, range: [0, 46]`
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
            `speed[0]: every statement must be an object with a clause ['if', 'else_if', 'else'] and an operator ['multiply_by', 'limit_to']. given type: null, range: [10, 11]`
        ]);
        test_validate(`speed:\n  - {if: condition, multiply_by: 0.9}\n  -`, [
            `speed[1]: every statement must be an object with a clause ['if', 'else_if', 'else'] and an operator ['multiply_by', 'limit_to']. given type: null, range: [47, 48]`
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
            `speed[0]: every statement must be an object with a clause ['if', 'else_if', 'else'] and an operator ['multiply_by', 'limit_to']. given type: string, range: [8, 13]`
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
        ])
        test_validate(`priority: [{limit_to: 100, multiply_by: 0.3}]`, [
            `priority[0]: every statement must have a clause ['if', 'else_if', 'else']. given: limit_to,multiply_by, range: [11, 44]`
        ]);
        test_validate(`priority: [{if: condition1, else_if: condition2}]`, [
            `priority[0]: every statement must have an operator ['multiply_by', 'limit_to']. given: if,else_if, range: [11, 48]`
        ]);
        test_validate(`priority: [{if: condition1, limit_to: 100}, {if: condition2}]`, [
            `priority[1]: every statement must have an operator ['multiply_by', 'limit_to']. given: if, range: [44, 60]`
        ]);
    });

    test('speed/priority statements conditions must be strings or booleans (or null for else)', () => {
        test_validate(`speed: [{if: condition, limit_to: 30}, {else: condition, multiply_by: 0.4}]`, [
            `speed[1]: the value of 'else' must be null. given: 'condition', range: [46, 55]`
        ]);
        test_validate(`priority: [{if : [], multiply_by: 0.4}]`, [
            `priority[0]: the value of 'if' must be a string or boolean. given type: list, range: [17, 19]`
        ])
        test_validate(`priority: [{if : {}, multiply_by: 0.4}]`, [
            `priority[0]: the value of 'if' must be a string or boolean. given type: object, range: [17, 19]`
        ])
        test_validate(`priority: [{if : 35, multiply_by: 0.4}]`, [
            `priority[0]: the value of 'if' must be a string or boolean. given type: number, range: [17, 19]`
        ])
        test_validate(`speed: [{if: condition, multiply_by: 0.2}, {else_if: 3.4, limit_to: 12}]`, [
            `speed[1]: the value of 'else_if' must be a string or boolean. given type: number, range: [53, 56]`
        ]);
        test_validate(`speed:\n  - if:     \n    limit_to: 100`, [
            `speed[0]: the value of 'if' must be a string or boolean. given type: null, range: [11, 13]`
        ]);
        test_validate(`speed: [{if: condition, multiply_by: 0.2}, {else_if: , limit_to: 12}]`, [
            `speed[1]: the value of 'else_if' must be a string or boolean. given type: null, range: [44, 51]`
        ]);
        test_validate(`speed: [{limit_to: 100, if:`, [
            `speed[0]: the value of 'if' must be a string or boolean. given type: null, range: [24, 26]`
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
            `speed[0]: the value of 'multiply_by' must be a number. given type: list, range: [37, 39]`
        ]);
        test_validate(`priority: [{if: condition, multiply_by: {}}]`, [
            `priority[0]: the value of 'multiply_by' must be a number. given type: object, range: [40, 42]`
        ]);
        test_validate(`speed: [{if: condition, limit_to: abc}]`, [
            `speed[0]: the value of 'limit_to' must be a number. given type: string, range: [34, 37]`
        ]);
        test_validate(`speed: [{if: condition, limit_to: }]`, [
            `speed[0]: the value of 'limit_to' must be a number. given type: null, range: [24, 32]`
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
        test_validate(`areas: { '' : {}}`, [`areas: keys must not be empty. given: '', range: [9, 12]`]);
        test_validate(`areas: {    "   " : {}}`, [`areas: invalid area name: '   ', only a-z, digits and _ are allowed, range: [12, 18]`]);
        test_validate(`areas: {    " abc  " : {}}`, [`areas: invalid area name: ' abc  ', only a-z, digits and _ are allowed, range: [12, 21]`]);
        test_validate(`areas: {    "a bc" : {}}`, [`areas: invalid area name: 'a bc', only a-z, digits and _ are allowed, range: [12, 19]`]);
        test_validate(`areas: {    "_abc" : {}}`, [`areas: invalid area name: '_abc', only a-z, digits and _ are allowed, range: [12, 19]`]);
        test_validate(`areas: {    "9abc" : {}}`, [`areas: invalid area name: '9abc', only a-z, digits and _ are allowed, range: [12, 19]`]);
        test_validate(`areas: {    "a__9bc" : {}}`, []);
    });

    test(`area names must be unique`, () => {
        test_validate(`areas:\n  area1: {}\n  area2: {}\n  area3: {}`, []);
        test_validate(`areas:\n  area1: {}\n  area2: {}\n  area1: {}`, [
            `areas: keys must be unique. duplicate: 'area1', range: [33, 38]`
        ]);
    });

    test(`area names get returned correctly`, () => {
        expectAreas(`areas:\n  area1: {}\n  area2: {}`, ['area1', 'area2']);
        expectAreas(`areas: { x2: {}, p_qr: {}, rst6: {}}`, ['x2', 'p_qr', 'rst6']);
    });
});

function expectAreas(doc, areas) {
    try {
        const res = validate(doc);
        expect(res.errors).toStrictEqual([]);
        expect(res.areas).toStrictEqual(areas);
    } catch (e) {
        Error.captureStackTrace(e, expectAreas);
        throw e;
    }
}

function test_validate(doc, errors) {
    // previously we put the path and message into one string. later we added range. this is a quick way to re-use the existing tests.
    // to clean this up we should check `path`, `message` and `range` separately...
    try {
        expect(validate(doc).errors.map(e => `${e.path}: ${e.message}, range: [${e.range[0]}, ${e.range[1]}]`)).toStrictEqual(errors);
    } catch (e) {
        Error.captureStackTrace(e, test_validate);
        throw e;
    }
}