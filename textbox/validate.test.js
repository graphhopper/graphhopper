import {validate} from './validate';

describe("validate", () => {
    test('empty document is valid', () => {
        expect(validate(``).errors).toStrictEqual([]);
    })

    test('root must be an object', () => {
        expect(validate(`[]`).errors).toStrictEqual([
            `root: must be an object. possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given type: list`
        ]);
        expect(validate(`-\n-\n`).errors).toStrictEqual([
            `root: must be an object. possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given type: list`
        ]);
        expect(validate(`abc`).errors).toStrictEqual([
            `root: must be an object. possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given type: string`
        ]);
        expect(validate(`'abc'`).errors).toStrictEqual([
            `root: must be an object. possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given type: string`
        ]);
        expect(validate(`"abc"`).errors).toStrictEqual([
            `root: must be an object. possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given type: string`
        ]);
        expect(validate(`301`).errors).toStrictEqual([
            `root: must be an object. possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given type: number`
        ]);
    });

    test('root keys are not empty', () => {
        expect(validate(`:`).errors).toStrictEqual([
            `root: possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given: null`
        ]);
        expect(validate(`''  :`).errors).toStrictEqual([
            `root: possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given: ''`
        ]);
        expect(validate(`"" \t: \n `).errors).toStrictEqual([
            `root: possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given: ''`
        ]);
    });

    test('root keys are valid', () => {
        expect(validate(`abc: def`).errors).toStrictEqual([
            `root: possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given: 'abc'`
        ]);
        expect(validate(`spee: [dprio]`).errors).toStrictEqual([
            `root: possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given: 'spee'`
        ]);
        // todo: this error message is not very helpful unfortunately, maybe it would be better to check
        // one section after the other instead of first checking all root keys?
        expect(validate(`speed: \n    multiply by: 0.3\n  - if: condition`).errors).toStrictEqual([
            `root: possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given: null`
        ]);
    });

    test('root keys are unique', () => {
        expect(validate(`speed: [abc]\npriority: [def]\nspeed: [ghi]`).errors).toStrictEqual([
            `root: keys must be unique. duplicate: 'speed'`
        ]);
    });

    test('root element values are not null', () => {
        expect(validate(`speed: \ndistance_influence: \npriority\n`).errors).toStrictEqual([
            `speed: must not be null`,
            `distance_influence: must not be null`,
            `priority: must not be null`
        ]);
        expect(validate(`speed: \n  -`).errors).toStrictEqual([
            `speed[0]: every statement must be an object with a clause ['if', 'else if', 'else'] and an operator ['multiply by', 'limit to']. given type: null`
        ]);
    });

    test('speed and priority are lists', () => {
        expect(validate(`speed: no_list`).errors).toStrictEqual([
            `speed: must be a list. given type: string`
        ])
        expect(validate(`speed: \tabc: def`).errors).toStrictEqual([
            `speed: must be a list. given type: object`
        ])
        expect(validate(`priority: {}`).errors).toStrictEqual([
            `priority: must be a list. given type: object`
        ])
    });

    test('distance_influence is a number', () => {
        expect(validate(`distance_influence:    \t \n`).errors).toStrictEqual([
            `distance_influence: must not be null`
        ])
        expect(validate(`distance_influence: []`).errors).toStrictEqual([
            `distance_influence: must be a number. given type: list`
        ]);
        expect(validate(`distance_influence: abc`).errors).toStrictEqual([
            `distance_influence: must be a number. given: 'abc'`
        ])
        expect(validate(`distance_influence: 3.4abc`).errors).toStrictEqual([
            `distance_influence: must be a number. given: '3.4abc'`
        ])
        expect(validate(`distance_influence: 86`).errors).toStrictEqual([])
    });

    test('speed/priority statements keys are valid', () => {
        expect(validate(`speed: ['abc']`).errors).toStrictEqual([
            `speed[0]: every statement must be an object with a clause ['if', 'else if', 'else'] and an operator ['multiply by', 'limit to']. given type: string`
        ]);
        expect(validate(`speed: [{abc: def}]`).errors).toStrictEqual([
            `speed[0]: possible keys: ['if', 'else if', 'else', 'multiply by', 'limit to']. given: 'abc'`
        ]);
        expect(validate(`speed:\n - multiply by: 0.9\n  - ele: bla`).errors).toStrictEqual([
            `speed[0]: every statement must have a clause ['if', 'else if', 'else']. given: multiply by`,
            `speed[1]: possible keys: ['if', 'else if', 'else', 'multiply by', 'limit to']. given: 'ele'`
        ]);
        expect(validate(`priority: [{if: condition, else: null, multiply by: 0.3}]`).errors).toStrictEqual([
            `priority[0]: too many keys. maximum: 2. given: else,if,multiply by`
        ])
        expect(validate(`priority: [{limit to: 100, multiply by: 0.3}]`).errors).toStrictEqual([
            `priority[0]: every statement must have a clause ['if', 'else if', 'else']. given: limit to,multiply by`
        ]);
        expect(validate(`priority: [{if: condition1, else if: condition2}]`).errors).toStrictEqual([
            `priority[0]: every statement must have an operator ['multiply by', 'limit to']. given: if,else if`
        ]);
    });

    test('speed/priority statements conditions must be strings or booleans (or null for else)', () => {
        expect(validate(`speed: [{if: condition, limit to: 30}, {else: condition, multiply by: 0.4}]`).errors).toStrictEqual([
            `speed[1]: the value of 'else' must be null. given: 'condition'`
        ]);
        expect(validate(`priority: [{if : [], multiply by: 0.4}]`).errors).toStrictEqual([
            `priority[0]: the value of 'if' must be a string or boolean. given type: list`
        ])
        expect(validate(`priority: [{if : {}, multiply by: 0.4}]`).errors).toStrictEqual([
            `priority[0]: the value of 'if' must be a string or boolean. given type: object`
        ])
        expect(validate(`priority: [{if : 35, multiply by: 0.4}]`).errors).toStrictEqual([
            `priority[0]: the value of 'if' must be a string or boolean. given type: number`
        ])
        expect(validate(`speed: [{if: condition, multiply by: 0.2}, {else if: 3.4, limit to: 12}]`).errors).toStrictEqual([
            `speed[1]: the value of 'else if' must be a string or boolean. given type: number`
        ]);
        expect(validate(`speed: [{if: condition, multiply by: 0.2}, {else if: , limit to: 12}]`).errors).toStrictEqual([
            `speed[1]: the value of 'else if' must be a string or boolean. given type: null`
        ]);
        expect(validate(`speed: [{if: true, multiply by: 0.15}]`).errors).toStrictEqual([]);
    });

    test('speed/priority operator values must be numbers', () => {
        expect(validate(`speed: [{if: condition, multiply by: []}]`).errors).toStrictEqual([
            `speed[0]: the value of 'multiply by' must be a number. given type: list`
        ]);
        expect(validate(`priority: [{if: condition, multiply by: {}}]`).errors).toStrictEqual([
            `priority[0]: the value of 'multiply by' must be a number. given type: object`
        ]);
        expect(validate(`speed: [{if: condition, limit to: abc}]`).errors).toStrictEqual([
            `speed[0]: the value of 'limit to' must be a number. given type: string`
        ]);
        expect(validate(`speed: [{if: condition, limit to: }]`).errors).toStrictEqual([
            `speed[0]: the value of 'limit to' must be a number. given type: null`
        ])
    });

    test('statements must follow certain order', () => {
        expect(validate(`speed: [{else: , limit to: 60}, {if: condition, multiply by: 0.9}]`).errors).toStrictEqual([
            `speed[0]: 'else' clause must be preceded by 'if' or 'else if'`
        ]);
        expect(validate(`priority: [{else if: condition, multiply by: 0.3}, {else: , limit to: 30}]`).errors).toStrictEqual([
            `priority[0]: 'else if' clause must be preceded by 'if'`
        ]);
    })
})