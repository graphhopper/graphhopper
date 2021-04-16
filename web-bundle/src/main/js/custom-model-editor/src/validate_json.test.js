import {validateJson} from './validate_json';

describe('validate_json', () => {
    test('root must be an object', () => {
        test_validate(``, [
            `root: must be an object, range: [0, 0]`
        ]);
        test_validate(` `, [
            `root: must be an object, range: [0, 1]`
        ]);
        test_validate(`null`, [
            `root: must be an object. given type: null, range: [0, 4]`
        ]);
        test_validate(`[]`, [
            `root: must be an object. given type: array, range: [0, 2]`
        ]);
        test_validate(`"abc"`, [
            `root: must be an object. given type: string, range: [0, 5]`
        ]);
        test_validate(`123`, [
            `root: must be an object. given type: number, range: [0, 3]`
        ]);
        test_validate(`"speed": []"`, [
            `root: must be an object. given type: string, range: [0, 7]`
        ]);
        test_validate(`{}`, []);
    });

    test('root keys are not empty', () => {
        test_validate(`{ "": []}`, [
            `root: keys must be non-empty and must not only consist of whitespace. given: '', range: [2, 4]`
        ]);
        test_validate(`{""  :`, [
            `root: keys must be non-empty and must not only consist of whitespace. given: '', range: [1, 3]`
        ]);
        test_validate(`{"    " :}`, [
            `root: keys must be non-empty and must not only consist of whitespace. given: '    ', range: [1, 7]`
        ]);
        test_validate(`{"\n" :}`, [
            `root: keys must be non-empty and must not only consist of whitespace. given: '', range: [1, 2]`
        ]);
        test_validate(`{"" \t: \n }`, [
            `root: keys must be non-empty and must not only consist of whitespace. given: '', range: [1, 3]`
        ]);
    });

    test('root keys are valid', () => {
        test_validate(`{"abc": "def"}`, [
            `root: possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given: 'abc', range: [1, 6]`
        ]);
        test_validate(`{"spee": []}`, [
            `root: possible keys: ['speed', 'priority', 'distance_influence', 'areas']. given: 'spee', range: [1, 7]`
        ]);
    });

    test('root keys are unique', () => {
        test_validate(`{"speed": [], "priority": [], "speed": []}`, [
            `root: keys must be unique. duplicate: 'speed', range: [30, 37]`
        ]);
    });

    test('root element values are not null', () => {
        test_validate(`{"speed": null,\n"distance_influence":   null\n, "priority": null}`, [
            `speed: must not be null, range: [10, 14]`,
            `distance_influence: must not be null, range: [40, 44]`,
            `priority: must not be null, range: [59, 63]`
        ]);
    });

    test('speed and priority are arrays', () => {
        test_validate(`{"speed"}`, [
            `speed: missing value, range: [1, 8]`
        ]);
        test_validate(`{"speed": }`, [
            `speed: missing value, range: [1, 8]`
        ]);
        test_validate(`{"speed": [], "priority": []}`, []);
        test_validate(`{"speed": "no_array"}`, [
            `speed: must be an array. given type: string, range: [10, 20]`
        ]);
        test_validate(`{"speed": {"abc": "def"}}`, [
            `speed: must be an array. given type: object, range: [10, 24]`
        ]);
        test_validate(`{"priority": {}}`, [
            `priority: must be an array. given type: object, range: [13, 15]`
        ]);
    });

    test('distance_influence is a number', () => {
        test_validate(`{"distance_influence":  null}`, [
            `distance_influence: must not be null, range: [24, 28]`
        ])
        test_validate(`{"distance_influence": []}`, [
            `distance_influence: must be a number. given type: array, range: [23, 25]`
        ]);
        test_validate(`{"distance_influence": "abc"}`, [
            `distance_influence: must be a number. given type: string, range: [23, 28]`
        ]);
        // this is not a valid number but the parser recognizes 3.4 correctly and adds a syntax
        // error for the invalid value abc
        test_validate(`{"distance_influence": 3.4abc}`, []);
        test_validate(`{"distance_influence": 86}`, []);
    });

    test('speed/priority statements keys are valid', () => {
        test_validate(`{"speed": ["abc"]}`, [
            `speed[0]: must be an object. given type: string, range: [11, 16]`
        ]);
        test_validate(`{"speed": \n[{"abc": "def"}]}`, [
            `speed[0]: possible keys: ['if', 'else_if', 'else', 'multiply_by', 'limit_to']. given: 'abc', range: [13, 18]`
        ]);
        test_validate(`{"speed": [{"multiply_by": 0.9, "ele": "bla"}]}`, [
            `speed[0]: possible keys: ['if', 'else_if', 'else', 'multiply_by', 'limit_to']. given: 'ele', range: [32, 37]`
        ]);
        test_validate(`{"priority": [{"if": "condition", "else": null, "multiply_by": 0.3}]}`, [
            `priority[0]: too many keys. maximum: 2. given: else,if,multiply_by, range: [14, 67]`
        ]);
        test_validate(`{"priority": [{"if": "condition", "limit_to": 100}, {"if": "condition", "else": null, "multiply_by": 0.3}]}`, [
            `priority[1]: too many keys. maximum: 2. given: else,if,multiply_by, range: [52, 105]`
        ]);
        test_validate(`{"priority": [{"limit_to": 100, "multiply_by": 0.3}]}`, [
            `priority[0]: every statement must have a clause ['if', 'else_if', 'else']. given: limit_to,multiply_by, range: [14, 51]`
        ]);
        test_validate(`{"priority": [{"if": "condition1", "else_if": "condition2"}]}`, [
            `priority[0]: every statement must have an operator ['multiply_by', 'limit_to']. given: if,else_if, range: [14, 59]`
        ]);
        test_validate(`{"priority": [{"if": "condition1", "limit_to": 100}, {"if": "condition2"}]}`, [
            `priority[1]: every statement must have an operator ['multiply_by', 'limit_to']. given: if, range: [53, 73]`
        ]);
        test_validate(`{"speed": [ "if": "condition", "limit_to": 100 ]}`, [
            `speed[0]: must be an object. given type: string, range: [12, 16]`,
            `speed[1]: must be an object. given type: string, range: [31, 41]`,
        ]);
    });

    test('speed/priority statements conditions must be strings or booleans (or null or empty string for else)', () => {
        test_validate(`{"speed": [{"if": , "limit_to": 30}]}`, [
            `speed[0][if]: missing value, range: [12, 16]`
        ]);
        test_validate(`{"speed": [{"if": "condition", "limit_to": }]}`, [
            `speed[0][limit_to]: missing value, range: [31, 41]`
        ]);
        test_validate(`{"speed": [{"if": "condition", "limit_to": 30}, {"else": "condition", "multiply_by": 0.4}]}`, [
            `speed[1][else]: must be null or empty. given: 'condition', range: [57, 68]`
        ]);
        test_validate(`{"speed": [{"if": "condition", "limit_to": 30}, {"else": "   ", "multiply_by": 0.4}]}`, [
            `speed[1][else]: must be null or empty. given: '   ', range: [57, 62]`
        ]);
        test_validate(`{"speed": [{"if": "condition", "limit_to": 30}, {"else": null, "multiply_by": 0.4}]}`, [
        ]);
        test_validate(`{"priority": [{"if" : [], "multiply_by": 0.4}]}`, [
            `priority[0][if]: must be a string or boolean. given type: array, range: [22, 24]`
        ])
        test_validate(`{"priority": [{"if" : {}, "multiply_by": 0.4}]}`, [
            `priority[0][if]: must be a string or boolean. given type: object, range: [22, 24]`
        ])
        test_validate(`{"priority": [{"if" : 35, "multiply_by": 0.4}]}`, [
            `priority[0][if]: must be a string or boolean. given type: number, range: [22, 24]`
        ])
        test_validate(`{"speed": [{"if": "condition", "multiply_by": 0.2}, {"else_if": 3.4, "limit_to": 12}]}`, [
            `speed[1][else_if]: must be a string or boolean. given type: number, range: [64, 67]`
        ]);
        test_validate(`{"speed": [{"limit_to": 100, "if": true}`, [
        ]);
        test_validate(`{"speed": [ "if":`, [
            `speed[0]: must be an object. given type: string, range: [12, 16]`
        ]);
        test_validate(`{"speed": [ "if": "if": `, [
            `speed[0]: must be an object. given type: string, range: [12, 16]`
        ]);
        test_validate(`{"speed": [{"if": tru, "multiply_by": 0.15}]`, [
            `speed[0][if]: missing value, range: [12, 16]`
        ]);
        test_validate(`{"speed": [ {"if":  "abc", "limit_to": 100    }]`, [
        ]);
    });

    test('get condition ranges', () => {
        const res = validateJson(`{"speed": [{"if": "cond1", "limit_to": 50}, {"else_if": "cond2", "multiply_by": 0.3}],
         "priority": [{"if": "cond3", "multiply_by": 0.3}]}`);
        expect(res.errors).toStrictEqual([]);
        expect(res.conditionRanges).toStrictEqual([[18, 25], [56, 63], [116, 123]]);
    });

    test('speed/priority operator values must be numbers', () => {
        test_validate(`{"speed": [{"if": "condition", "multiply_by": []}]}`, [
            `speed[0][multiply_by]: must be a number. given type: array, range: [46, 48]`
        ]);
        test_validate(`{"priority": [{"if": "condition", "multiply_by": {}}]}`, [
            `priority[0][multiply_by]: must be a number. given type: object, range: [49, 51]`
        ]);
        test_validate(`{"speed": [{"if": "condition", "limit_to": "abc"}]}`, [
            `speed[0][limit_to]: must be a number. given type: string, range: [43, 48]`
        ]);
    });

    test('statements must follow certain order', () => {
        test_validate(`{"speed": [{"else": "", "limit_to": 60}, {"if": "condition", "multiply_by": 0.9}]}`, [
            `speed[0]: 'else' clause must be preceded by 'if' or 'else_if', range: [11, 39]`
        ]);
        test_validate(`{"priority": [{"else_if": "condition", "multiply_by": 0.3}, {"else": "", "limit_to": 30}]}`, [
            `priority[0]: 'else_if' clause must be preceded by 'if' or 'else_if', range: [14, 58]`
        ]);
        // multiple else_ifs are possible
        test_validate(`{"priority": [{"if": "abc", "limit_to": 60}, {"else_if": "def", "multiply_by": 0.2}, {"else_if": "condition", "limit_to": 100}]}`, []);
    });

    test('areas is an object', () => {
        test_validate(`{"areas": []}`, [`areas: must be an object. given type: array, range: [10, 12]`]);
        test_validate(`{"areas": "not_an_object"}`, [`areas: must be an object. given type: string, range: [10, 25]`]);
    });

    test('area names must be strings', () => {
        test_validate(`{"areas": {    "   " : {}}}`, [`areas: keys must be non-empty and must not only consist of whitespace. given: '   ', range: [15, 20]`]);
        test_validate(`{"areas": {    " abc  " : {}}}`, [`areas: names may only contain a-z, digits and _. given: ' abc  ', range: [15, 23]`]);
        test_validate(`{"areas": {    "a bc" : {}}}`, [`areas: names may only contain a-z, digits and _. given: 'a bc', range: [15, 21]`]);
        test_validate(`{"areas": {    "_abc" : {}}}`, [`areas: names may only contain a-z, digits and _. given: '_abc', range: [15, 21]`]);
        test_validate(`{"areas": {    "9abc" : {}}}`, [`areas: names may only contain a-z, digits and _. given: '9abc', range: [15, 21]`]);
    });

    test(`area names must be unique`, () => {
        test_validate(`{"areas": { "area1": {}, "area2": {}, "area1": {}}}`, [
            `areas: keys must be unique. duplicate: 'area1', range: [38, 45]`
        ]);
    });

    test(`area names get returned correctly`, () => {
        expectAreaNames(`{"areas": { "area1": {},   "area2": {}}}`, ['area1', 'area2']);
        expectAreaNames(`{"areas": { "x2": {}, "p_qr": {}, "rst6": {}}}`, ['x2', 'p_qr', 'rst6']);
    });

    test('areas must be objects', () => {
        test_validate(`{"areas": {"area1": }}`, [
            `areas[area1]: missing value, range: [11, 18]`
        ]);
        test_validate(`{"areas": {"area1": null}}`, [
            `areas[area1]: must be an object. given type: null, range: [20, 24]`
        ]);
        test_validate(`{"areas": {  "area1": [],   "area2": "abc",  "area3": 100}}`, [
            `areas[area1]: must be an object. given type: array, range: [22, 24]`,
            `areas[area2]: must be an object. given type: string, range: [37, 42]`,
            `areas[area3]: must be an object. given type: number, range: [54, 57]`
        ]);
    });

    test('area keys', () => {
        test_validate(`{"areas": {  "area1":  {  "x": "y", "p": "q"}}}`, [
            `areas[area1]: possible keys: ['type', 'geometry', 'id', 'properties']. given: 'x', range: [26, 29]`,
            `areas[area1]: possible keys: ['type', 'geometry', 'id', 'properties']. given: 'p', range: [36, 39]`
        ]);

        test_validate(`{"areas": { "area1": {  "type": "y", "id": "q" }}}`, [
            `areas[area1]: missing 'geometry'. given: ['type', 'id'], range: [21, 48]`
        ]);

        test_validate(`{"areas": { "area1": { "geometry": {}}}}`, [
            `areas[area1]: missing 'type'. given: ['geometry'], range: [21, 38]`
        ]);
    });

    test('area values', () => {
        test_validate(`{"areas": {"area1": { "type": "Future", "geometry": }}}`, [
            `areas[area1][type]: must be "Feature". given: "Future", range: [30, 38]`,
            `areas[area1][geometry]: missing value, range: [40, 50]`
        ]);
        test_validate(`{"areas": {"area1": { "type": "Feature", "geometry": []}}}`, [
            `areas[area1][geometry]: must be an object. given type: array, range: [53, 55]`
        ]);
        test_validate(`{"areas": {"area1": { "type": "Feature", "geometry": {}, "properties": }}}`, [
            `areas[area1][geometry]: missing 'type'. given: [], range: [53, 55]`,
            `areas[area1][geometry]: missing 'coordinates'. given: [], range: [53, 55]`,
            `areas[area1][properties]: missing value, range: [57, 69]`
        ]);
        const validGeometry = `{"type": "Polygon", "coordinates": [[[1,1], [2,2], [3,3], [1,1]]]}`;
        test_validate(`{"areas": {"area1": { "type": "Feature", "geometry": ${validGeometry}, "properties": []}}}`, [
            `areas[area1][properties]: must be an object. given type: array, range: [135, 137]`
        ]);
        test_validate(`{"areas": {"area1": { "type": "Feature", "geometry": ${validGeometry}, "id": 123}}`, [
            `areas[area1][id]: must be a string. given type: number, range: [127, 130]`
        ]);
        test_validate(`{"areas": {"area1": { "type": "Feature", "geometry": ${validGeometry}, "id": "xyz", properties: {"abc": "def"}}}`, [
        ]);
    });

    test('area geometry', () => {
        test_validate(`{"areas": {"area1": { "type": "Feature", "geometry": {"type": "bla"} }}}`, [
            `areas[area1][geometry]: missing 'coordinates'. given: ['type'], range: [53, 68]`
        ]);
        test_validate(`{"areas": {"area1": { "type": "Feature", "geometry": {"teip": "bla"} }}}`, [
            `areas[area1][geometry]: possible keys: ['type', 'coordinates']. given: 'teip', range: [54, 60]`
        ]);
        test_validate(`{"areas": {"area1": { "type": "Feature", "geometry": {"coordinates": [[[1,1], [2,2], [3,3], [1,1]]]} }}}`, [
            `areas[area1][geometry]: missing 'type'. given: ['coordinates'], range: [53, 100]`
        ]);
        test_validate(`{"areas": {"area1": { "type": "Feature", "geometry": {"type": , "coordinates": [[[1,1], [2,2], [3,3], [1,1]]]} }}}`, [
            `areas[area1][geometry][type]: missing value, range: [54, 60]`
        ]);
        test_validate(`{"areas": {"area1": { "type": "Feature", "geometry": {"type": "bla", "coordinates": [[[1,1], [2,2], [3,3], [1,1]]]} }}}`, [
            `areas[area1][geometry][type]: must be "Polygon". given: 'bla', range: [62, 67]`
        ]);
        test_validate(`{"areas": {"area1": { "type": "Feature", "geometry": {"type": "Polygon", "coordinates": } }}}`, [
            `areas[area1][geometry][coordinates]: missing value, range: [73, 86]`
        ]);
        test_validate(`{"areas": {"area1": { "type": "Feature", "geometry": {"type": "Polygon", "coordinates": "abc"} }}}`, [
            `areas[area1][geometry][coordinates]: must be an array. given type: string, range: [88, 93]`
        ]);
    });

    test('area geometry coordinates list', () => {
        test_validate(`{"areas":  {"area1":  { "type":  "Feature" , "geometry":  {"type":  "Polygon" , "coordinates":  []} }}}`, [
            `areas[area1][geometry][coordinates]: minimum length: 1, given: 0, range: [96, 98]`
        ]);
        test_validate(`{"areas":  {"area1":  { "type":  "Feature" , "geometry":  {"type":  "Polygon" , "coordinates":  ["abc"]} }}}`, [
            `areas[area1][geometry][coordinates][0]: must be an array. given type: string, range: [97, 102]`
        ]);
        test_validate(`{"areas":  {"area1":  { "type":  "Feature" , "geometry":  {"type":  "Polygon" , "coordinates":  [{}, "abc"]} }}}`, [
            `areas[area1][geometry][coordinates][0]: must be an array. given type: object, range: [97, 99]`,
            `areas[area1][geometry][coordinates][1]: must be an array. given type: string, range: [101, 106]`
        ]);
        test_validate(`{"areas":  {"area1":  { "type":  "Feature" , "geometry":  {"type":  "Polygon" , "coordinates":  [[]]} }}}`, [
            `areas[area1][geometry][coordinates][0]: minimum length: 4, given: 0, range: [97, 99]`
        ]);
        test_validate(`{"areas":  {"area1":  { "type":  "Feature" , "geometry":  {"type":  "Polygon" , "coordinates":  [[], [], [], []]} }}}`, [
            `areas[area1][geometry][coordinates][0]: minimum length: 4, given: 0, range: [97, 99]`,
            `areas[area1][geometry][coordinates][1]: minimum length: 4, given: 0, range: [101, 103]`,
            `areas[area1][geometry][coordinates][2]: minimum length: 4, given: 0, range: [105, 107]`,
            `areas[area1][geometry][coordinates][3]: minimum length: 4, given: 0, range: [109, 111]`
        ]);
        test_validate(`{"areas":  {"area1":  { "type":  "Feature" , "geometry":  {"type":  "Polygon" , "coordinates":  [[[0, 0], [1, "p"], [2, 2], ["x", 0]]]} }}}`, [
            `areas[area1][geometry][coordinates][0][1][1]: must be a number, range: [110, 113]`,
            `areas[area1][geometry][coordinates][0][3][0]: must be a number, range: [125, 128]`,
        ]);
        test_validate(`{"areas":  {"area1":  { "type":  "Feature" , "geometry":  {"type":  "Polygon" , "coordinates":  [[[0, 0], [1, 1], [2, 2], [0, 0, "p"]]]} }}}`, [
            `areas[area1][geometry][coordinates][0][3]: maximum length: 2, given: 3, range: [122, 133]`
        ]);
        test_validate(`{"areas":  {"area1":  { "type":  "Feature" , "geometry":  {"type":  "Polygon" , "coordinates":  [[[1, 2], [3, 4], [5, 6], [1.1, 2.2]]]} }}}`, [
            `areas[area1][geometry][coordinates][0]: the last point must be equal to the first, range: [122, 132]`
        ]);
        test_validate(`{"areas":  {"area1":  { "type":  "Feature" , "geometry":  {"type":  "Polygon" , "coordinates":  [[[100, 200], [300, 40], [5, 6], [1.1, 2.2]]]} }}}`, [
            `areas[area1][geometry][coordinates][0][0][1]: latitude must be in [-90, +90], range: [104, 107]`,
            `areas[area1][geometry][coordinates][0][1][0]: longitude must be in [-180, +180], range: [111, 114]`
        ]);
        test_validate(`{"areas":  {"area1":  { "type":  "Feature" , "geometry":  {"type":  "Polygon" , "coordinates":  [[[1, 2], [3, 4], [5, 6], [1, 2]]]} }}}`, []);
    });

    function expectAreaNames(doc, areas) {
        try {
            const res = validateJson(doc);
            expect(res.areas).toStrictEqual(areas);
        } catch (e) {
            Error.captureStackTrace(e, expectAreaNames);
            throw e;
        }
    }

    test(`includes parser errors`, () => {
        // these errors depend on the underlying third party parser and not our primary
        // output. we keep around a few tests, but the most important part is that they are
        // actually added to our output.
        test_validate_parser_error(`{"speed"}`, [
           `syntax: ColonExpected, range: [8, 9]`
        ]);
        test_validate_parser_error(`{   :  []}`, [
            `syntax: PropertyNameExpected, range: [4, 5]`,
            `syntax: ValueExpected, range: [9, 10]`
        ]);
        test_validate_parser_error(`{}[]`, [
            `syntax: EndOfFileExpected, range: [2, 3]`,
        ]);
        test_validate_parser_error(`{"speed: []}`, [
            `syntax: UnexpectedEndOfString, range: [1, 12]`,
            `syntax: ColonExpected, range: [12, 13]`,
            `syntax: CloseBraceExpected, range: [12, 13]`,
        ]);
        test_validate_parser_error(`{"speed": []`, [
            `syntax: CloseBraceExpected, range: [12, 13]`
        ]);
        test_validate_parser_error(`"speed": []`, [
            `syntax: EndOfFileExpected, range: [7, 8]`
        ]);
        test_validate_parser_error(`- "abc"\n- "def"\n- "ghi"`, [
            `syntax: InvalidSymbol, range: [0, 1]`,
            `syntax: InvalidSymbol, range: [8, 9]`,
            `syntax: EndOfFileExpected, range: [10, 15]`,
        ]);
        test_validate_parser_error(`   :  `, [
            `syntax: ValueExpected, range: [3, 4]`
        ]);
        test_validate_parser_error(`{[]: "abc"}`, [
            `syntax: PropertyNameExpected, range: [1, 2]`,
            `syntax: ValueExpected, range: [10, 11]`
        ]);
        test_validate_parser_error(`{{}: "abc"}`, [
            `syntax: PropertyNameExpected, range: [1, 2]`,
            `syntax: ValueExpected, range: [2, 3]`,
            `syntax: EndOfFileExpected, range: [3, 4]`
        ]);
        test_validate_parser_error(`{null: []}`, [
            `syntax: PropertyNameExpected, range: [1, 5]`,
            `syntax: ValueExpected, range: [9, 10]`
        ])
    })
});

function test_validate(doc, errors) {
    const res = validateJson(doc);
    const errorStrings = res.errors.map(e => `${e.path}: ${e.message}, range: [${e.range.join(', ')}]`);
    // console.log(res.jsonErrors);
    try {
        expect(errorStrings).toStrictEqual(errors);
    } catch (e) {
        Error.captureStackTrace(e, test_validate);
        throw e;
    }
}

function test_validate_parser_error(doc, errors) {
    const res = validateJson(doc);
    const errorStrings = res.jsonErrors.map(e => `${e.path}: ${e.message}, range: [${e.range.join(', ')}]`);
    try {
        expect(errorStrings).toStrictEqual(errors);
    } catch (e) {
        Error.captureStackTrace(e, test_validate_parser_error);
        throw e;
    }
}
