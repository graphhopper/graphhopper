////////////////////////////////////////////////////////////////////////////////////////////////////////
// We know that you love 'free', we love it too :)! And so the entire GraphHopper routing engine is not 
// only free but even Open Source! The GraphHopper Directions API is also free for development. 
// Grab an API key and have fun with installing anything: https://graphhopper.com/#directions-api
// Misuse of API keys that you don't own is prohibited and you'll be blocked.
////////////////////////////////////////////////////////////////////////////////////////////////////////

// Easily replace this options.js with an additional file that you prodive as options_prod.js activate via:
// BROWSERIFYSWAP_ENV='production' npm run watch
// see also package.json and https://github.com/thlorenz/browserify-swap
exports.options = {
    environment: "development",
    routing: {host: '', api_key: ''},
    geocoding: {host: '', api_key: ''},
    thunderforest: {api_key: ''},
    omniscale: {api_key: ''}
};