const path = require('path');

module.exports = {
    entry: './src/index.js',
    // with webpack 5 this is needed to transpile webpack's 'glue code' even though we set target es5 in tsconfig already
    target: 'es5',
    module: {
        rules: [
            {
                test: /\.js$/,
                use: 'ts-loader',
            }
        ]
    },
    resolve: {
        extensions: ['.js']
    },
    output: {
        filename: 'index.js',
        path: path.resolve(__dirname, 'dist'),
        library: 'GHCustomModelEditor',
        libraryTarget: 'umd'
    }
};