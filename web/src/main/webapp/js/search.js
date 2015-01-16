var places = new Bloodhound(
		{
			datumTokenizer : function(d) {
				return Bloodhound.tokenizers.whitespace(d.value);
			},
			queryTokenizer : Bloodhound.tokenizers.whitespace,
			remote : {
				url : 'https://api.ordnancesurvey.co.uk/places/v1/addresses/find?query=%QUERY&key=OS_PLACES_KEY',
				filter : function(places) {
					// Map the remote source JSON array to a JavaScript object
					// array
					return $.map(places.results, function(address) {

						var epsg27700 = "+proj=tmerc +lat_0=49 +lon_0=-2 +k=0.999601 +x_0=400000 +y_0=-100000 +ellps=airy +towgs84=446.448,-125.157,542.060,0.1502,0.2470,0.8421,-20.4894 +datum=OSGB36 +units=m +no_defs";    
						var result = proj4(epsg27700).inverse([address.DPA.X_COORDINATE,address.DPA.Y_COORDINATE]);
						
						return {
							address_line : address.DPA.ADDRESS,
							X_COORDINATE : address.DPA.X_COORDINATE,
							Y_COORDINATE : address.DPA.Y_COORDINATE,
							latitude: result[1],
							longitude: result[0],
							value : address,
							UPRN : address.DPA.UPRN
						};
					});
				}
			}
		});

var nominatum = new Bloodhound(
		{
			datumTokenizer : function(d) {
				return Bloodhound.tokenizers.whitespace(d.value);
			},
			queryTokenizer : Bloodhound.tokenizers.whitespace,
			remote : {
				url : 'https://nominatim.openstreetmap.org/search?format=json&addressdetails=1&q=%QUERY&limit=10',
				filter : function(nominatum) {
					// Map the remote source JSON array to a JavaScript object
					// array
					return $.map(nominatum, function(namedplace) {
						return {
							address_line : namedplace.display_name,
							latitude : namedplace.lat,
							longitude : namedplace.lon,
							value : namedplace
						};
					});
				}
			}
		});

places.initialize();
nominatum.initialize();