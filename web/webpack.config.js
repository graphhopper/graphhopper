const path = require('path');

module.exports = {
    entry: 'F:/Marcos/repositories/graphhopper/web/src/main/resources/assets/js/index.js',
    output: {
        filename: 'main.js',
        path: path.resolve('F:/Marcos/repositories/graphhopper/web/src/main/resources/assets/dist')
    },
    devServer: {
        compress: true,
        port: 9000,
        publicPath: 'F:/Marcos/repositories/graphhopper/web/src/main/resources/assets/dist/',
        writeToDisk: true,
        hot: false
    },
    module: {
        rules: [
            {
                test: /\.sass$/,
                use: [
                    // Creates `style` nodes from JS strings
                    'style-loader',
                    // Translates CSS into CommonJS
                    'css-loader',
                    // Compiles Sass to CSS
                    'sass-loader',
                ],
            },
            {
                test: /\.html$/i,
                loader: 'html-loader',
            },
            {
                test: /\.css$/i,
                use: ['style-loader', 'css-loader'],
            },
            {
                test: /\.(png|svg|jpg|gif|eot|ttf|woff|woff2)$/,
                loader: 'url-loader',
                options: {
                    limit: 8000, // Convert images < 8kb to base64 strings
                    name: 'images/[hash]-[name].[ext]'
                }
            }
        ],
    },
};