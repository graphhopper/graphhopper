L.Proj.WMTSCRS = L.Proj.CRS.extend({
	scale : function(zoom) {
		this.updateTransformation(zoom);
		return this._scales[zoom];
	},
	updateTransformation : function(zoom) {
		if (this.options.topLeftCorners[zoom]) {
			this.transformation._b = -this.options.topLeftCorners[zoom].lng;
			this.transformation._d = this.options.topLeftCorners[zoom].lat;
		}
	}
});