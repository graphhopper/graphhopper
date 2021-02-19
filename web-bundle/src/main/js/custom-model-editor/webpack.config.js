const path = require('path');

module.exports = {
    entry: './src/index.js',
    devtool: 'inline-source-map',
    module: {
        rules: [
            {
                test: /\.js$/,
                use: 'ts-loader',
                exclude: /node_modules/,
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