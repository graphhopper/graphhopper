// use this in browser
import * as YAML from './node_modules/yaml/browser/dist/index.js';
// ... and this for tests
// import YAML from './node_modules/yaml/index.js';

const rootKeys = ['speed', 'priority', 'distance_influence', 'areas'];
const rootKeysString = `['speed', 'priority', 'distance_influence', 'areas']`;

const clauses = ['if', 'else if', 'else'];
const clausesString = `['if', 'else if', 'else']`;
const operators = ['multiply by', 'limit to'];
const operatorsString = `['multiply by', 'limit to']`;

const statementKeys = ['if', 'else if', 'else', 'multiply by', 'limit to'];
const statementKeysString = `['if', 'else if', 'else', 'multiply by', 'limit to']`;

// todo: made this global for quick experiment
let conditionRanges = [];

/**
 * Checks that a given yaml string follows this schema:
 *
 * speed: array<{clause: string, operator: value}>, optional, not null
 * priority: array<{clause: string, operator: value}>, optional, not null
 * distance_influence: number, optional, not null
 * areas: object, optional, not null
 *
 * the speed/priority array objects must contain a cause that can be either 'if', 'else if' or 'else' and
 * an operator that can be 'multiply by' or 'limit to'
 *
 * the clause value must be a string and the operator value must be a number
 * except when the the clause is 'else' in which case the value must be null
 *
 * 'else if' clauses must be preceded by an 'if' clause
 * 'else' clauses must be preceded by an 'if' or 'else if' clause
 *
 * This method returns an object containing:
 *
 * - errors: a list of error objects that contain a message, a (yaml) path (as string) and
 *           the character range associated with the error as array [startInclusive, endExclusive]
 * - conditionRanges: a list of character ranges in above format that indicates the positions of
 *                    the 'conditions', i.e. the values of 'if' and 'else if' clauses
 */
export function validate(yaml) {
    conditionRanges = [];
    const doc = YAML.parseDocument(yaml, { keepCstNodes: true });
    const errors = validateYamlDoc(doc);
    return {
        errors,
        conditionRanges,
    }
}

function validateYamlDoc(doc) {
    // empty docs are ok
    if (!doc.contents) return [];

    // root must be a yaml object
    if (!isYamlObject(doc.contents.type)) {
        return [
            error(`root`, `must be an object. possible keys: ${rootKeysString}. given type: ${displayType(doc.contents)}`, doc.contents.range)
        ]
    }

    // root elements must be strings with certain values
    {
        const errors = validateRootKeys(doc.contents);
        if (errors.length > 0) return errors;
    }

    // root elements must have certain types
    {
        const errors = validateRootValues(doc.contents.items);
        if (errors.length > 0) return errors;
    }

    // document is valid
    return [];
}

function validateRootKeys(rootObj) {
    return validateObjectKeys('root', rootKeys, rootKeysString, rootObj, rootKeys.length);
}

function validateRootValues(rootItems) {
    const errors = [];
    for (let i = 0; i < rootItems.length; ++i) {
        const key = rootItems[i].key.value;
        const value = rootItems[i].value;
        if (value === null) {
            // todo: range
            errors.push(error(`${key}`, `must not be null`, null));
        } else {
            if (key === 'speed' || key === 'priority') {
                if (!isYamlList(value.type)) {
                    errors.push(error(`${key}`, `must be a list. given type: ${displayType(value)}`, value.range));
                } else {
                    errors.push.apply(errors, validateStatements(key, value.items));
                }
            } else if (key === 'distance_influence') {
                if (!isYamlPlain(value.type))
                    errors.push(error(`${key}`, `must be a number. given type: ${displayType(value)}`, value.range));
                else if (!isNumber(value))
                    errors.push(error(`${key}`, `must be a number. given: '${value.value}'`, value.range));
            } else if (key === 'areas') {
                // todo: currently we are not validating areas! this could be a use-case for json schema validation
                // because we could use a ready-made geo-json schema. or maybe just not validate it at all...
            } else {
                console.error(`Unexpected root key ${key}`);
            }
        }
    }
    return errors;
}

function validateStatements(key, items) {
    const errors = [];
    for (let i = 0; i < items.length; ++i) {
        const item = items[i];
        if (item === null) {
            // todo: range
            errors.push(error(`${key}[${i}]`, `every statement must be an object with a clause ${clausesString} and an operator ${operatorsString}. given type: null`, null));
        } else if (!isYamlObject(item.type))
            errors.push(error(`${key}[${i}]`, `every statement must be an object with a clause ${clausesString} and an operator ${operatorsString}. given type: ${displayType(item)}`, item.range));
        else
            errors.push.apply(errors, validateStatement(key, i, item));
    }

    if (errors.length > 0)
        return errors;

    // statements seem to be ok, but are they in the right order?
    const clausesList = [];
    for (let i = 0; i < items.length; ++i) {
        // console.log(items[i].items);
        for (let j = 0; j < items[i].items.length; ++j) {
            const key = items[i].items[j].key.value;
            if (clauses.indexOf(key) >= 0)
                clausesList.push(key);
        }
    }
    let prev = '';
    for (let i = 0; i < clausesList.length; ++i) {
        if (clausesList[i] === 'else' && (prev !== 'else if' && prev !== 'if')) {
            errors.push(error(`${key}[${i}]`, `'else' clause must be preceded by 'if' or 'else if'`, items[i].range));
        }
        if (clausesList[i] === 'else if' && prev !== 'if') {
            errors.push(error(`${key}[${i}]`, `'else if' clause must be preceded by 'if'`, items[i].range));
        }
        prev = clausesList[i];
    }
    return errors;
}

function validateStatement(statementKey, statementIndex, statementItem) {
    const statementEntries = statementItem.items;
    const errors = validateObjectKeys(`${statementKey}[${statementIndex}]`, statementKeys, statementKeysString, statementItem, 2);
    if (errors.length > 0) return errors;

    let hasClause = false;
    let hasOperator = false;
    const keys = [];
    for (let i = 0; i < statementEntries.length; ++i) {
        const entry = statementEntries[i];
        const key = entry.key.value;
        keys.push(key);
        const isClause = clauses.indexOf(key) >= 0;
        const isOperator = operators.indexOf(key) >= 0;
        if (isClause) {
            hasClause = true;
            if (key === 'else') {
                if (entry.value !== null) {
                    errors.push(error(`${statementKey}[${statementIndex}]`, `the value of 'else' must be null. given: '${entry.value}'`, entry.value.range));
                }
            } else {
                if (entry.value === null) {
                    // todo: no range
                    errors.push(error(`${statementKey}[${statementIndex}]`, `the value of '${key}' must be a string or boolean. given type: null`, null));
                } else if (!isString(entry.value) && !isBoolean(entry.value)) {
                    errors.push(error(`${statementKey}[${statementIndex}]`, `the value of '${key}' must be a string or boolean. given type: ${displayType(entry.value)}`, entry.value.range));
                } else {
                    conditionRanges.push(entry.value.range);
                }
            }
        }
        if (isOperator) {
            hasOperator = true;
            if (entry.value === null) {
                // todo: range
                errors.push(error(`${statementKey}[${statementIndex}]`, `the value of '${key}' must be a number. given type: null`, null));
            } else if (!isNumber(entry.value)) {
                errors.push(error(`${statementKey}[${statementIndex}]`, `the value of '${key}' must be a number. given type: ${displayType(entry.value)}`, entry.value.range));
            }
        }
        if (isClause === isOperator) {
            console.error(`Unexpected statement: ${isClause} ${isOperator} ${key} ${clauses.indexOf(key)} ${entry}`);
        }
    }
    if (!hasClause) {
        errors.push(error(`${statementKey}[${statementIndex}]`, `every statement must have a clause ${clausesString}. given: ${keys}`, statementItem.range));
    }
    if (!hasOperator) {
        errors.push(error(`${statementKey}[${statementIndex}]`, `every statement must have an operator ${operatorsString}. given: ${keys}`, statementItem.range));
    }
    return errors;
}

function validateObjectKeys(objectKey, legalKeys, legalKeysString, obj, maxKeys) {
    const errors = [];
    const keys = new Set();
    const entries = obj.items;
    for (let i = 0; i < entries.length; ++i) {
        const key = entries[i].key;
        if (key === undefined || key === null) {
            // todo: range
            errors.push(error(`${objectKey}`, `possible keys: ${legalKeysString}. given: ${key}`, null));
        } else if (!isString(key) || key.value.length === 0 || legalKeys.indexOf(key.value) < 0) {
            errors.push(error(`${objectKey}`, `possible keys: ${legalKeysString}. given: '${key}'`, key.range));
        } else if (keys.has(key.value)) {
            errors.push(error(`${objectKey}`, `keys must be unique. duplicate: '${key.value}'`, key.range))
        } else {
            keys.add(key.value);
        }
    }
    if (keys.size > maxKeys) {
        errors.push(error(`${objectKey}`, `too many keys. maximum: ${maxKeys}. given: ${Array.from(keys).sort()}`, obj.range));
    }
    return errors;
}

function error(path, message, range) {
    return { path, message, range };
}

function isYamlObject(yamlParserType) {
    return yamlParserType === 'MAP' || yamlParserType === 'FLOW_MAP';
}

function isYamlList(yamlParserType) {
    return yamlParserType === 'SEQ' || yamlParserType === 'FLOW_SEQ';
}

function isYamlString(yamlParserType) {
    return yamlParserType === 'PLAIN' || yamlParserType === 'QUOTE_SINGLE' || yamlParserType === 'QUOTE_DOUBLE';
    // todo: support BLOCK_LITERAL etc.?
}

function isYamlPlain(yamlParserType) {
    return yamlParserType === 'PLAIN';
}

function displayType(node) {
    if (isYamlObject(node.type)) {
        return tr['object'];
    } else if (isYamlList(node.type)) {
        return tr['list'];
    } else if (isString(node)) {
        return tr['string'];
    } else if (isNumber(node)) {
        return tr['number'];
    } else if (isBoolean(node)) {
        return tr['boolean'];
    } else {
        console.error(`Unknown yaml parser type ${node} ${node.type}`);
    }
}

function isString(node) {
    return isYamlString(node.type) && typeof node.value === 'string';
}

function isNumber(node) {
    return isYamlPlain(node.type) && typeof node.value === "number" && !isNaN(node.value);
}

function isBoolean(node) {
    return isYamlPlain(node.type) && typeof node.value === 'boolean';
}

// translations (english only atm)
// todo: extract error messages as well
const tr = {
    'object': 'object',
    'list': 'list',
    'string': 'string',
    'number': 'number',
    'boolean': 'boolean'
}
