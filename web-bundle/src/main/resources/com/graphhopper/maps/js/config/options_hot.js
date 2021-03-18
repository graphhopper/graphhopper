// see comments in options.js
exports.options = {
    with_tiles: true,
    environment: "development",
    // points to the GH server's default port. this is needed when we do not serve GH maps from the same folder
    // and use the live-server instead
    routing: {host: 'http://localhost:8989', api_key: ''},
    geocoding: {host: '', api_key: ''},
    thunderforest: {api_key: ''},
    omniscale: {api_key: ''},
    mapilion: {api_key: ''}
};