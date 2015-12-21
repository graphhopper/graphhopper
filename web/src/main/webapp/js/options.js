// we use envify to pick the correct options_*.js config see https://github.com/hughsk/envify
// on command line do: export NODE_ENV=development
if (!process.env.NODE_ENV || process.env.NODE_ENV === "development") {
    console.log("running development (" + process.env.NODE_ENV + ")");
    exports.options = require("./options_dev.js").options;
} else if (process.env.NODE_ENV === "production") {
    exports.options = require("./options_prod.js").options;
}