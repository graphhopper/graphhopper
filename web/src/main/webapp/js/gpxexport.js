
var ensureOneCheckboxSelected = function () {
    //Make sure that at least one of the data types remains checked for the GPX export:
    $("#gpx_route").change(function () {
        if (!$(this).is(':checked'))
        {
            if (!$("#gpx_track").is(':checked'))
                $("#gpx_waypoints").prop("disabled", true);
            else {
                if (!$("#gpx_waypoints").is(':checked'))
                    $("#gpx_track").prop("disabled", true);
            }
        } else
        {
            $("#gpx_track").prop("disabled", false);
            $("#gpx_waypoints").prop("disabled", false);
        }
    });
    $("#gpx_track").change(function () {
        if (!$(this).is(':checked'))
        {
            if (!$("#gpx_route").is(':checked'))
                $("#gpx_waypoints").prop("disabled", true);
            else {
                if (!$("#gpx_waypoints").is(':checked'))
                    $("#gpx_route").prop("disabled", true);
            }
        } else
        {
            $("#gpx_route").prop("disabled", false);
            $("#gpx_waypoints").prop("disabled", false);
        }
    });
    $("#gpx_waypoints").change(function () {
        if (!$(this).is(':checked'))
        {
            if (!$("#gpx_route").is(':checked'))
                $("#gpx_track").prop("disabled", true);
            else {
                if (!$("#gpx_track").is(':checked'))
                    $("#gpx_route").prop("disabled", true);
            }
        } else
        {
            $("#gpx_route").prop("disabled", false);
            $("#gpx_track").prop("disabled", false);
        }
    });
};

module.exports.addGpxExport = function (ghRequest) {
    var dialog;

    function exportGPX(withRoute, withTrack, withWayPoint) {
        if (ghRequest.route.isResolved())
            window.open(ghRequest.createGPXURL(withRoute, withTrack, withWayPoint));
        return false;
    }

    function exportFlaggedGPX() {
        exportGPX($("#gpx_route").is(':checked'), $("#gpx_track").is(':checked'), $("#gpx_waypoints").is(':checked'));
        dialog.dialog("close");
        return false;
    }

    $(function () {
        dialog = $("#gpx_dialog").dialog({
            width: 420,
            height: 260,
            autoOpen: false,
            resizable: false,
            draggable: false,
            buttons: {
                "Export GPX": exportFlaggedGPX,
                Cancel: function () {
                    $(this).dialog("close");
                }
            }
        });
        ensureOneCheckboxSelected();
    });

    $('#gpxExportButton a').click(function (e) {
        // no page reload
        e.preventDefault();
        $("#gpx_dialog").dialog('open');
    });
};
