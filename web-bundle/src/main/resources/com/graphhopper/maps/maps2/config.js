const config = {
    // todo: get rid of host and port here, but just '/' does not seem to work unfortunately, maybe we need to get rid
    //       of the `new URL(...)` calls in graphhopper-maps?
    // api: '/',
    api: 'http://localhost:8989/',
    defaultTiles: 'OpenStreetMap',
    keys: {
        graphhopper: "missing_api_key",
        maptiler: "missing_api_key",
        omniscale: "missing_api_key",
        thunderforest: "missing_api_key",
        kurviger: "missing_api_key"
    },
    routingGraphLayerAllowed: true,
    extraProfiles: {}
}
