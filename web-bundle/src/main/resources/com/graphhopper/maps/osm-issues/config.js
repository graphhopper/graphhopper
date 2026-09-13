// Deployment specific settings, so that nobody has to type them into the settings panel.
// Everything here is public: the page ships it to every visitor. The OAuth client id is meant to be
// public (PKCE, no secret), but never put the client *secret* of a confidential application here.
// A user can still override each value in the settings panel, that choice then wins.
const osmIssuesConfig = {
    // where GraphHopper runs. Empty means the same server that serves this page.
    graphhopperUrl: '',
    // client id of an OAuth 2 application registered at https://www.openstreetmap.org/oauth2/applications
    // (not confidential, redirect uri = the url of this page, permission "modify the map")
    osmClientId: '',
    // the OSM instance to edit. Use https://master.apis.dev.openstreetmap.org to try things out,
    // but note that it has its own database without the real ways
    osmApi: 'https://www.openstreetmap.org',
    // optional, enables the height sign layer and direct links to Mapillary images.
    // A client token of an app registered at https://www.mapillary.com/dashboard/developers,
    // it is meant to be used from the browser like the OAuth client id above.
    mapillaryToken: ''
};
