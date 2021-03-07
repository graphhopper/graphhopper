import YAML from "yaml";

/**
 * Returns auto-complete suggestions for a yaml string and a given character position
 */
export function completeYaml(content, pos) {
    // if the cursor is not positioned on top of a token we insert a dummy character
    const yamlString = (pos >= content.length || (isWhitespace(content[pos]) && (pos === 0 || content[pos - 1] !== ':')))
        ? content.substring(0, pos) + '…' + content.substring(pos)
        : content;
    const yamlPath = getYamlPath(yamlString, pos);

    // remove the last element (the actual node) and only consider the ancestors
    const ancestorSignature = yamlPath.signature;
    ancestorSignature.pop();
    const signatureString = ancestorSignature.join('-');
    const ancestorPath = yamlPath.path;
    ancestorPath.pop();
    if (
        // the signature of root elements is root-x or root-map-pair-x
        /^root$/.test(signatureString) ||
        /^root-map-pair$/.test(signatureString)
    ) {
        const suggestions = ['speed', 'priority', 'distance_influence', 'areas']
            .filter(s => ancestorPath.length === 1 || !keyAlreadyExistsInOtherPairs(ancestorPath[1].items, ancestorPath[2], s));
        return {
            suggestions,
            range: yamlPath.tokenRange
        }
    } else if (
        /^root-map-pair\[distance_influence]$/.test(signatureString)
    ) {
        return {
            suggestions: [`__hint__type a number`],
            range: yamlPath.tokenRange
        }
    } else if (
        // the signature of statements is root-map-pair[speed/priority]-list[i]-x or root-map-pair[speed/priority]-list[i]-map-pair-x
        /^root-map-pair\[(speed|priority)]-list\[[0-9]+]$/.test(signatureString) ||
        /^root-map-pair\[(speed|priority)]-list\[[0-9]+]-map-pair$/.test(signatureString)
    ) {
        const clauses = [`if`, `else_if`, `else`];
        const operators = [`limit_to`, `multiply_by`];
        const hasClause = ancestorPath.length === 6 && keysAlreadyExistInOtherPairs(ancestorPath[4].items, ancestorPath[5], clauses);
        const hasOperator = ancestorPath.length === 6 && keysAlreadyExistInOtherPairs(ancestorPath[4].items, ancestorPath[5], operators);
        let suggestions = [];
        if (!hasClause)
            suggestions.push(...clauses);
        if (!hasOperator)
            suggestions.push(...operators);
        return {
            suggestions,
            range: yamlPath.tokenRange
        }
    } else if (
        /^root-map-pair\[(speed|priority)]-list\[[0-9]+]-map-pair\[(if|else_if|else)]$/.test(signatureString)
    ) {
        return {
            suggestions: [`__hint__type a condition`],
            range: yamlPath.tokenRange
        }
    } else if (
        /^root-map-pair\[(speed|priority)]-list\[[0-9]+]-map-pair\[(limit_to|multiply_by)]$/.test(signatureString)
    ) {
        return {
            suggestions: [`__hint__type a number`],
            range: yamlPath.tokenRange
        }
    } else if (
        /^root-map-pair\[areas]$/.test(signatureString) ||
        /^root-map-pair\[areas]-map-pair$/.test(signatureString)
    ) {
        return {
            suggestions: [`__hint__type an area name`],
            range: yamlPath.tokenRange
        }
    } else if (
        /^root-map-pair\[areas]-map-pair\[[a-zA-Z0-9_]*]$/.test(signatureString) ||
        /^root-map-pair\[areas]-map-pair\[[a-zA-Z0-9_]*]-map-pair$/.test(signatureString)
    ) {
        const suggestions = [`geometry`, `type`]
            .filter(s => !(ancestorPath.length === 7 && keyAlreadyExistsInOtherPairs(ancestorPath[5].items, ancestorPath[6], s)));
        return {
            suggestions,
            range: yamlPath.tokenRange
        }
    } else if (
        /^root-map-pair\[areas]-map-pair\[[a-zA-Z0-9_]*]-map-pair\[type]$/.test(signatureString)
    ) {
        return {
            suggestions: [`Feature`],
            range: yamlPath.tokenRange
        }
    } else if (
        /^root-map-pair\[areas]-map-pair\[[a-zA-Z0-9_]*]-map-pair\[geometry]$/.test(signatureString) ||
        /^root-map-pair\[areas]-map-pair\[[a-zA-Z0-9_]*]-map-pair\[geometry]-map-pair$/.test(signatureString)
    ) {
        const suggestions = [`type`, `coordinates`]
            .filter(s => !(ancestorPath.length === 9 && keyAlreadyExistsInOtherPairs(ancestorPath[7].items, ancestorPath[8], s)));
        return {
            suggestions,
            range: yamlPath.tokenRange
        }
    } else if (
        /^root-map-pair\[areas]-map-pair\[[a-zA-Z0-9_]*]-map-pair\[geometry]-map-pair\[type]$/.test(signatureString)
    ) {
        return {
            suggestions: [`Polygon`],
            range: yamlPath.tokenRange
        }
    } else {
        return {
            suggestions: [],
            range: []
        }
    }
}

/**
 * Returns the YAML path and a special string representation (the 'signature') for the given yaml string and position.
 * The returned object contains the path as array, its 'signature' as string array and the token range of the token at
 * pos.
 */
export function getYamlPath(content, pos) {
    const doc = YAML.parseDocument(content);
    const result = {
        path: [],
        signature: [],
        tokenRange: []
    };
    YAML.visit(doc, {
        Scalar(key, node, path) {
            // we use the end of the range inclusively!
            if (pos >= node.range[0] && pos <= node.range[1]) {
                result.path = path.concat([node]);
                result.signature = path.map((n, i) => nodeToPathElement(n, i + 1 < path.length ? path[i + 1] : node));
                result.signature.push(nodeToPathElement(node, null));
                result.tokenRange = node.range;
                return YAML.visit.BREAK;
            }
        }
    });
    return result;
}

function nodeToPathElement(node, child) {
    if (node.type === 'DOCUMENT') {
        return 'root';
    } else if (node.type === 'MAP' || node.type === 'FLOW_MAP') {
        return 'map';
    } else if (node.type === 'PAIR') {
        if (child !== null && node.value === child) {
            return `pair[${node.key ? node.key.value : node.key}]`
        } else {
            return `pair`;
        }
    } else if (node.type === 'SEQ' || node.type === 'FLOW_SEQ') {
        if (child !== null) {
            return `list[${node.items.indexOf(child)}]`;
        } else {
            return `list`;
        }
    } else if (node.type === 'PLAIN') {
        return node.value;
    } else {
        return `unknown[${node.type}]`;
    }
}

function keyAlreadyExistsInOtherPairs(allPairs, thisPair, key) {
    return allPairs.some(p => p !== thisPair && p.key && p.key.value === key);
}

function keysAlreadyExistInOtherPairs(allPairs, thisPair, keys) {
    return allPairs.some(p => p !== thisPair && p.key && keys.indexOf(p.key.value) >= 0);
}

function isWhitespace(str) {
    return str.trim() === '';
}