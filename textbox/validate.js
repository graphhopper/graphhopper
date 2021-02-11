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

export function validate(yaml) {
    const doc = YAML.parseDocument(yaml);
    const errors = validateYamlDoc(doc);
    return {
        errors
    }
}

function validateYamlDoc(doc) {
    // empty docs are ok
    if (!doc.contents) return [];

    // root must be a yaml object
    if (!isYamlObject(doc.contents.type)) {
        return [
            `root: must be an object. possible keys: ${rootKeysString}. given type: ${displayType(doc.contents)}`
        ]
    }

    // root elements must be strings with certain values
    {
        const errors = validateRootKeys(doc.contents.items);
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

function validateRootKeys(rootItems) {
    return validateObjectKeys('root', rootKeys, rootKeysString, rootItems, rootKeys.length);
}

function validateRootValues(rootItems) {
    const errors = [];
    for (let i = 0; i < rootItems.length; ++i) {
        const key = rootItems[i].key.value;
        const value = rootItems[i].value;
        if (value === null) {
            errors.push(`${key}: must not be null`);
        } else {
            if (key === 'speed' || key === 'priority') {
                if (!isYamlList(value.type)) {
                    errors.push(`${key}: must be a list. given type: ${displayType(value)}`);
                } else {
                    errors.push.apply(errors, validateStatements(key, value.items));
                }
            } else if (key === 'distance_influence') {
                if (!isYamlPlain(value.type))
                    errors.push(`${key}: must be a number. given type: ${displayType(value)}`);
                else if (!isNumber(value))
                    errors.push(`${key}: must be a number. given: '${value.value}'`);
            } else if (key === 'areas') {
                // todo: currently we are not validating areas!
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
            errors.push(`${key}[${i}]: every statement must be an object with a clause ${clausesString} and an operator ${operatorsString}. given type: null`);
        } else if (!isYamlObject(item.type))
            errors.push(`${key}[${i}]: every statement must be an object with a clause ${clausesString} and an operator ${operatorsString}. given type: ${displayType(item)}`);
        else
            errors.push.apply(errors, validateStatement(key, i, item.items));
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
            errors.push(`${key}[${i}]: 'else' clause must be preceded by 'if' or 'else if'`);
        }
        if (clausesList[i] === 'else if' && prev !== 'if') {
            errors.push(`${key}[${i}]: 'else if' clause must be preceded by 'if'`);
        }
        prev = clausesList[i];
    }
    return errors;
}

function validateStatement(statementKey, statementIndex, statementEntries) {
    const errors = validateObjectKeys(`${statementKey}[${statementIndex}]`, statementKeys, statementKeysString, statementEntries, 2);
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
                    errors.push(`${statementKey}[${statementIndex}]: the value of 'else' must be null. given: '${entry.value}'`);
                }
            } else {
                if (entry.value === null) {
                    errors.push(`${statementKey}[${statementIndex}]: the value of '${key}' must be a string or boolean. given type: null`);
                } else if (!isString(entry.value) && !isBoolean(entry.value)) {
                    errors.push(`${statementKey}[${statementIndex}]: the value of '${key}' must be a string or boolean. given type: ${displayType(entry.value)}`);
                }
            }
        }
        if (isOperator) {
            hasOperator = true;
            if (entry.value === null) {
                errors.push(`${statementKey}[${statementIndex}]: the value of '${key}' must be a number. given type: null`);
            } else if (!isNumber(entry.value)) {
                errors.push(`${statementKey}[${statementIndex}]: the value of '${key}' must be a number. given type: ${displayType(entry.value)}`);
            }
        }
        if (isClause === isOperator) {
            console.error(`Unexpected statement: ${isClause} ${isOperator} ${key} ${clauses.indexOf(key)} ${entry}`);
        }
    }
    if (!hasClause) {
        errors.push(`${statementKey}[${statementIndex}]: every statement must have a clause ${clausesString}. given: ${keys}`);
    }
    if (!hasOperator) {
        errors.push(`${statementKey}[${statementIndex}]: every statement must have an operator ${operatorsString}. given: ${keys}`);
    }
    return errors;
}

function validateObjectKeys(objectKey, legalKeys, legalKeysString, entries, maxKeys) {
    const errors = [];
    const keys = new Set();
    for (let i = 0; i < entries.length; ++i) {
        const key = entries[i].key;
        if (key === undefined || key === null) {
            errors.push(`${objectKey}: possible keys: ${legalKeysString}. given: ${key}`);
        } else if (!isString(key) || key.value.length === 0 || legalKeys.indexOf(key.value) < 0) {
            errors.push(`${objectKey}: possible keys: ${legalKeysString}. given: '${key}'`);
        } else if (keys.has(key.value)) {
            errors.push(`${objectKey}: keys must be unique. duplicate: '${key.value}'`)
        } else {
            keys.add(key.value);
        }
    }
    if (keys.size > maxKeys)
        errors.push(`${objectKey}: too many keys. maximum: ${maxKeys}. given: ${Array.from(keys).sort()}`);
    return errors;
}

function isYamlObject(yamlParserType) {
    return yamlParserType === 'MAP' || yamlParserType === 'FLOW_MAP';
}

function isYamlList(yamlParserType) {
    return yamlParserType === 'SEQ' || yamlParserType === 'FLOW_SEQ';
}

function isYamlString(yamlParserType) {
    return yamlParserType === 'PLAIN' || yamlParserType === 'QUOTE_SINGLE' || yamlParserType === 'QUOTE_DOUBLE';
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
