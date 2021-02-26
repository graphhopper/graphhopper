import {CustomModelEditor} from './custom_model_editor.js';

function create(categories, callback) {
    return new CustomModelEditor(categories, callback);
}

export {create};