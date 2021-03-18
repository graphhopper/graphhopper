// points to the GH server's default port. this is needed when we do not serve GH maps from the same folder
// and use the live-server instead
require('./config/options').options.routing.host = 'http://localhost:8989'
require('./main-template');