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
        // todo: range
        test_validate(`:`, [
            `root: possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given: null`
        ]);
        test_validate(`''  :`, [
            `root: possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given: '', range: [0, 4]`
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
        // todo: range
        test_validate(`speed: \n    multiply by: 0.3\n  - if: condition`, [
            `root: possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given: null`
        ]);
    });

    test('root keys are unique', () => {
        test_validate(`speed: [abc]\npriority: [def]\nspeed: [ghi]`, [
            `root: keys must be unique. duplicate: 'speed', range: [29, 34]`
        ]);
    });

    test('root element values are not null', () => {
        // todo: range
        test_validate(`speed: \ndistance_influence: \npriority\n`, [
            `speed: must not be null`,
            `distance_influence: must not be null`,
            `priority: must not be null`
        ]);
        test_validate(`speed: \n  -`, [
            `speed[0]: every statement must be an object with a clause ['if', 'else if', 'else'] and an operator ['multiply by', 'limit to']. given type: null`
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
        // todo: range
        test_validate(`distance_influence:    \t \n`, [
            `distance_influence: must not be null`
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
            `speed[0]: every statement must be an object with a clause ['if', 'else if', 'else'] and an operator ['multiply by', 'limit to']. given type: string, range: [8, 13]`
        ]);
        test_validate(`speed: [{abc: def}]`, [
            `speed[0]: possible keys: ['if', 'else if', 'else', 'multiply by', 'limit to']. given: 'abc', range: [9, 12]`
        ]);
        // todo: range
        test_validate(`speed:\n - multiply by: 0.9\n  - ele: bla`, [
            `speed[0]: every statement must have a clause ['if', 'else if', 'else']. given: multiply by, range: [10, 27]`,
            `speed[1]: possible keys: ['if', 'else if', 'else', 'multiply by', 'limit to']. given: 'ele', range: [31, 34]`
        ]);
        test_validate(`priority: [{if: condition, else: null, multiply by: 0.3}]`, [
            // todo: maybe improve this and get range for items (not the entire list) instead
            `priority[0]: too many keys. maximum: 2. given: else,if,multiply by, range: [11, 56]`
        ])
        test_validate(`priority: [{limit to: 100, multiply by: 0.3}]`, [
            // todo: maybe improve this and get range for items (not the entire list) instead
            `priority[0]: every statement must have a clause ['if', 'else if', 'else']. given: limit to,multiply by, range: [11, 44]`
        ]);
        test_validate(`priority: [{if: condition1, else if: condition2}]`, [
            // todo: maybe improve this and get range for items (not the entire list) instead
            `priority[0]: every statement must have an operator ['multiply by', 'limit to']. given: if,else if, range: [11, 48]`
        ]);
    });

    test('speed/priority statements conditions must be strings or booleans (or null for else)', () => {
        test_validate(`speed: [{if: condition, limit to: 30}, {else: condition, multiply by: 0.4}]`, [
            `speed[1]: the value of 'else' must be null. given: 'condition', range: [46, 55]`
        ]);
        test_validate(`priority: [{if : [], multiply by: 0.4}]`, [
            `priority[0]: the value of 'if' must be a string or boolean. given type: list, range: [17, 19]`
        ])
        test_validate(`priority: [{if : {}, multiply by: 0.4}]`, [
            `priority[0]: the value of 'if' must be a string or boolean. given type: object, range: [17, 19]`
        ])
        test_validate(`priority: [{if : 35, multiply by: 0.4}]`, [
            `priority[0]: the value of 'if' must be a string or boolean. given type: number, range: [17, 19]`
        ])
        test_validate(`speed: [{if: condition, multiply by: 0.2}, {else if: 3.4, limit to: 12}]`, [
            `speed[1]: the value of 'else if' must be a string or boolean. given type: number, range: [53, 56]`
        ]);
        // todo: range
        test_validate(`speed: [{if: condition, multiply by: 0.2}, {else if: , limit to: 12}]`, [
            `speed[1]: the value of 'else if' must be a string or boolean. given type: null`
        ]);
        test_validate(`speed: [{if: true, multiply by: 0.15}]`, []);
    });

    test('speed/priority operator values must be numbers', () => {
        test_validate(`speed: [{if: condition, multiply by: []}]`, [
            `speed[0]: the value of 'multiply by' must be a number. given type: list, range: [37, 39]`
        ]);
        test_validate(`priority: [{if: condition, multiply by: {}}]`, [
            `priority[0]: the value of 'multiply by' must be a number. given type: object, range: [40, 42]`
        ]);
        test_validate(`speed: [{if: condition, limit to: abc}]`, [
            `speed[0]: the value of 'limit to' must be a number. given type: string, range: [34, 37]`
        ]);
        // todo: range
        test_validate(`speed: [{if: condition, limit to: }]`, [
            `speed[0]: the value of 'limit to' must be a number. given type: null`
        ])
    });

    test('statements must follow certain order', () => {
        test_validate(`speed: [{else: , limit to: 60}, {if: condition, multiply by: 0.9}]`, [
            `speed[0]: 'else' clause must be preceded by 'if' or 'else if', range: [8, 30]`
        ]);
        test_validate(`priority: [{else if: condition, multiply by: 0.3}, {else: , limit to: 30}]`, [
            `priority[0]: 'else if' clause must be preceded by 'if' or 'else if', range: [11, 49]`
        ]);
        // multiple else ifs are possible
        test_validate(`priority: [{if: abc, limit to: 60}, {else if: def, multiply by: 0.2}, {else if: condition, limit to: 100}]`, []);
    });
});

function test_validate(expression, errors) {
    // previously we put the path and message into one string. later we added range. this is a quick way to re-use the existing tests.
    // to clean this up we should check `path`, `message` and `range` separately...
    try {
        expect(validate(expression).errors.map(e => {
            // todo: apparently there is no range for null values, maybe we need to switch to cst nodes instead..., but let's see how far we get first
            if (e.range === null)
                return `${e.path}: ${e.message}`
            else
                return `${e.path}: ${e.message}, range: [${e.range[0]}, ${e.range[1]}]`
        })).toStrictEqual(errors);
    } catch (e) {
        Error.captureStackTrace(e, test_validate);
        throw e;
    }
}