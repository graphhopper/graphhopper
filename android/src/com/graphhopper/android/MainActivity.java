package com.graphhopper.android;

import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.mapsforge.android.maps.MapActivity;
import org.mapsforge.android.maps.MapView;
import org.mapsforge.android.maps.Projection;
import org.mapsforge.android.maps.overlay.ListOverlay;
import org.mapsforge.android.maps.overlay.Marker;
import org.mapsforge.android.maps.overlay.PolygonalChain;
import org.mapsforge.android.maps.overlay.Polyline;
import org.mapsforge.core.model.GeoPoint;
import org.mapsforge.map.reader.header.FileOpenResult;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.util.Constants;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;

public class MainActivity extends MapActivity {

    private MapView mapView;
    private GraphHopperAPI hopper;
    private GeoPoint start;
    private GeoPoint end;
    private Spinner localSpinner;
    private Button localButton;
    private Spinner remoteSpinner;
    private Button remoteButton;
    private ListOverlay pathOverlay = new ListOverlay();
    private volatile boolean prepareInProgress = false;
    private volatile boolean shortestPathRunning = false;
    private String currentArea = "berlin";
    private String fileListURL = "https://code.google.com/p/graphhopper/downloads/list";
    private String prefixURL = "http://graphhopper.googlecode.com/";
    private String downloadURL;
    private String mapsFolder;
    private String mapFile;
    private SimpleOnGestureListener gestureListener = new SimpleOnGestureListener() {
        // why does this fail? public boolean onDoubleTap(MotionEvent e) {};
        public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
            if (!initFiles(currentArea)) {
                return false;
            }

            if (shortestPathRunning) {
                logUser("Calculation still in progress");
                return false;
            }
            float x = motionEvent.getX();
            float y = motionEvent.getY();
            Projection p = mapView.getProjection();
            GeoPoint tmpPoint = p.fromPixels((int) x, (int) y);

            if (start != null && end == null) {
                end = tmpPoint;
                shortestPathRunning = true;
                Marker marker = createMarker(tmpPoint, R.drawable.flag_red);
                if (marker != null) {
                    pathOverlay.getOverlayItems().add(marker);
                    mapView.redraw();
                }

                calcPath(start.latitude, start.longitude, end.latitude,
                        end.longitude);
            } else {
                start = tmpPoint;
                end = null;
                pathOverlay.getOverlayItems().clear();
                Marker marker = createMarker(start, R.drawable.flag_green);
                if (marker != null) {
                    pathOverlay.getOverlayItems().add(marker);
                    mapView.redraw();
                }
            }
            return true;
        }
    };
    private GestureDetector gestureDetector = new GestureDetector(
            gestureListener);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mapView = new MapView(this) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (gestureDetector.onTouchEvent(event)) {
                    return true;
                }
                return super.onTouchEvent(event);
            }
        };
        mapView.setClickable(true);
        mapView.setBuiltInZoomControls(true);

        final EditText input = new EditText(this);
        input.setText(currentArea);
        mapsFolder = Environment.getExternalStorageDirectory()
                .getAbsolutePath() + "/graphhopper/maps/";
        if (!new File(mapsFolder).exists()) {
            new File(mapsFolder).mkdirs();
        }

        localSpinner = (Spinner) findViewById(R.id.locale_area_spinner);
        localButton = (Button) findViewById(R.id.locale_button);
        remoteSpinner = (Spinner) findViewById(R.id.remote_area_spinner);
        remoteButton = (Button) findViewById(R.id.remote_button);
        // TODO get user confirmation to download
        // if (AndroidHelper.isFastDownload(this))
        chooseAreaFromRemote();
        chooseAreaFromLocal();
    }

    private boolean initFiles(String area) {
        // only return true if already loaded
        if (hopper != null) {
            return true;
        }
        if (prepareInProgress) {
            logUser("Preparation still in progress");
            return false;
        }
        prepareInProgress = true;
        currentArea = area;
        downloadingFiles();
        return false;
    }

    private void chooseAreaFromLocal() {
        List<String> nameList = new ArrayList<String>();
        for (String file : new File(mapsFolder).list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return filename != null
                        && (filename.endsWith(".ghz") || filename
                        .endsWith("-gh"));
            }
        })) {
            nameList.add(file);
        }
        if (nameList.isEmpty()) {
            return;
        }

        chooseArea(localButton, localSpinner, nameList,
                new MySpinnerListener() {
                    @Override
                    public void onSelect(String selectedArea,
                            String selectedFile) {
                        initFiles(selectedArea);
                    }
                });
    }

    private void chooseAreaFromRemote() {
        new GHAsyncTask<Void, Void, List<String>>() {
            protected List<String> saveDoInBackground(Void... params)
                    throws Exception {
                String filesList = mapsFolder + "files.txt";
                AndroidHelper.download(fileListURL, filesList, null);
                List<String> res = new ArrayList<String>();
                for (String str : AndroidHelper.readFile(new FileReader(
                        filesList))) {
                    int index = str.indexOf("/files/");
                    if (index >= 0) {
                        int lastIndex = str.indexOf(".ghz", index);
                        if (lastIndex >= 0) {
                            res.add(prefixURL + str.substring(index, lastIndex)
                                    + ".ghz");
                        }
                    }
                }
                return res;
            }

            @Override
            protected void onPostExecute(List<String> nameList) {
                if (hasError()) {
                    logUser("To get downloadable areas restart when connected to internet. "
                            + "Problem while fetching remote area list: "
                            + getErrorMessage());
                    return;
                }
                MySpinnerListener spinnerListener = new MySpinnerListener() {
                    @Override
                    public void onSelect(String selectedArea,
                            String selectedFile) {
                        if (selectedFile == null
                                || new File(mapsFolder + selectedArea + ".ghz")
                                .exists()
                                || new File(mapsFolder + selectedArea + "-gh")
                                .exists()) {
                            downloadURL = null;
                        } else {
                            downloadURL = selectedFile;
                        }
                        initFiles(selectedArea);
                    }
                };
                chooseArea(remoteButton, remoteSpinner, nameList,
                        spinnerListener);
            }
        }.execute();
    }

    private void chooseArea(Button button, final Spinner spinner,
            List<String> nameList, final MySpinnerListener mylistener) {
        final Map<String, String> nameToFullName = new TreeMap<String, String>();
        for (String fullName : nameList) {
            String tmp = Helper.pruneFileEnd(fullName);
            if (tmp.endsWith("-gh")) {
                tmp = tmp.substring(0, tmp.length() - 3);
            }
            tmp = AndroidHelper.getFileName(tmp);
            nameToFullName.put(tmp, fullName);
        }
        nameList.clear();
        nameList.addAll(nameToFullName.keySet());
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_dropdown_item, nameList);
        spinner.setAdapter(spinnerArrayAdapter);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Object o = spinner.getSelectedItem();
                if (o != null && o.toString().length() > 0) {
                    String area = o.toString();
                    mylistener.onSelect(area, nameToFullName.get(area));
                } else {
                    mylistener.onSelect(null, null);
                }
            }
        });
    }

    public interface MySpinnerListener {

        void onSelect(String selectedArea, String selectedFile);
    }

    void downloadingFiles() {
        if (downloadURL == null) {
            unzipping();
            return;
        }

        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("Downloading " + downloadURL);
        dialog.setIndeterminate(false);
        dialog.setMax(100);
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.show();

        new GHAsyncTask<Void, Integer, Object>() {
            protected Object saveDoInBackground(Void... _ignore)
                    throws Exception {
                final String localFile = mapsFolder
                        + AndroidHelper.getFileName(downloadURL);
                log("downloading " + downloadURL + " to " + localFile);
                AndroidHelper.download(downloadURL, localFile,
                        new ProgressListener() {
                            @Override
                            public void update(int val) {
                                publishProgress(val);
                            }
                        });
                return null;
            }

            protected void onProgressUpdate(Integer... values) {
                super.onProgressUpdate(values);
                dialog.setProgress(values[0]);
            }

            protected void onPostExecute(Object _ignore) {
                dialog.hide();
                if (hasError()) {
                    String str = "An error happend while retrieving maps:" + getErrorMessage();
                    log(str, getError());
                    logUser(str);
                } else {
                    unzipping();
                }
            }
        }.execute();
    }

    void unzipping() {
        final File compressed = new File(mapsFolder + currentArea + ".ghz");
        final boolean uncompress = compressed.exists()
                && !compressed.isDirectory();
        if (!uncompress) {
            loadMap();
            return;
        }

        logUser("uncompressing file " + compressed.getAbsolutePath());
        new GHAsyncTask<Void, Void, Object>() {
            @Override
            protected Object saveDoInBackground(Void... params)
                    throws Exception {
                if (uncompress) {
                    boolean deleteZipped = true;
                    Helper.unzip(compressed.getAbsolutePath(), mapsFolder
                            + currentArea + "-gh", deleteZipped);
                }
                return null;
            }

            protected void onPostExecute(Object result) {
                if (hasError()) {
                    logUser("Error while uncompressing: " + getErrorMessage());
                } else {
                    loadMap();
                }
            }
        }.execute();
    }

    void loadMap() {
        logUser("loading map");
        mapFile = mapsFolder + currentArea + "-gh/" + currentArea + ".map";
        FileOpenResult fileOpenResult = mapView.setMapFile(new File(mapFile));
        if (!fileOpenResult.isSuccess()) {
            logUser(fileOpenResult.getErrorMessage());
            finishPrepare();
            return;
        }
        setContentView(mapView);
        // TODO sometimes the center is wrong
        mapView.getOverlays().clear();
        mapView.getOverlays().add(pathOverlay);
        prepareGraph();
    }

    void prepareGraph() {
        logUser("loading graph (" + Constants.VERSION + "|" + Constants.VERSION_FILE
                + ") ... ");
        new GHAsyncTask<Void, Void, Path>() {
            protected Path saveDoInBackground(Void... v) throws Exception {
                GraphHopper tmpHopp = new GraphHopper().forMobile();
                tmpHopp.chShortcuts(true, true);
                tmpHopp.load(mapsFolder + currentArea);
                log("found graph with " + tmpHopp.graph().nodes() + " nodes");
                hopper = tmpHopp;
                return null;
            }

            protected void onPostExecute(Path o) {
                if (hasError()) {
                    logUser("An error happend while creating graph:"
                            + getErrorMessage());
                } else {
                    logUser("Finished loading graph. Touch to route.");
                }

                finishPrepare();
            }
        }.execute();
    }

    private void finishPrepare() {
        prepareInProgress = false;
    }

    private Polyline createPolyline(GHResponse response) {
        int points = response.points().size();
        List<GeoPoint> geoPoints = new ArrayList<GeoPoint>(points);
        PointList tmp = response.points();
        for (int i = 0; i < response.points().size(); i++) {
            geoPoints.add(new GeoPoint(tmp.latitude(i), tmp.longitude(i)));
        }
        PolygonalChain polygonalChain = new PolygonalChain(geoPoints);
        Paint paintStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintStroke.setStyle(Paint.Style.STROKE);
        paintStroke.setColor(Color.BLUE);
        paintStroke.setAlpha(128);
        paintStroke.setStrokeWidth(8);
        paintStroke
                .setPathEffect(new DashPathEffect(new float[]{25, 15}, 0));

        return new Polyline(polygonalChain, paintStroke);
    }

    private Marker createMarker(GeoPoint p, int resource) {
        Drawable drawable = getResources().getDrawable(resource);
        return new Marker(p, Marker.boundCenterBottom(drawable));
    }

    public void calcPath(final double fromLat, final double fromLon,
            final double toLat, final double toLon) {

        log("calculating path ...");
        new AsyncTask<Void, Void, GHResponse>() {
            float time;

            protected GHResponse doInBackground(Void... v) {
                StopWatch sw = new StopWatch().start();
                GHRequest req = new GHRequest(fromLat, fromLon, toLat, toLon)
                        .algorithm("dijkstrabi").putHint("douglas.minprecision", 1);
                GHResponse resp = hopper.route(req);
                time = sw.stop().getSeconds();
                return resp;
            }

            protected void onPostExecute(GHResponse res) {
                if (!res.hasError()) {
                    log("from:" + fromLat + "," + fromLon + " to:" + toLat + ","
                            + toLon + " found path with distance:" + res.distance()
                            / 1000f + ", nodes:" + res.points().size() + ", time:"
                            + time + " " + res.debugInfo());
                    logUser("the route is " + (int) (res.distance() / 100) / 10f
                            + "km long, time:" + res.time() / 60f + "min, debug:" + time);

                    pathOverlay.getOverlayItems().add(createPolyline(res));
                    mapView.redraw();
                } else {
                    logUser("Error:" + res.errors());
                }
                shortestPathRunning = false;
            }
        }.execute();
    }

    private void log(String str) {
        Log.i("GH", str);
    }

    private void log(String str, Throwable t) {
        Log.i("GH", str, t);
    }

    private void logUser(String str) {
        Toast.makeText(this, str, Toast.LENGTH_LONG).show();
    }
    private static final int NEW_MENU_ID = Menu.FIRST + 1;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, NEW_MENU_ID, 0, "Google");
        // menu.add(0, NEW_MENU_ID + 1, 0, "Other");
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case NEW_MENU_ID:
                if (start == null || end == null) {
                    logUser("tap screen to set start and end of route");
                    break;
                }
                Intent intent = new Intent(Intent.ACTION_VIEW);
                // get rid of the dialog
                intent.setClassName("com.google.android.apps.maps",
                        "com.google.android.maps.MapsActivity");
                intent.setData(Uri.parse("http://maps.google.com/maps?saddr="
                        + start.latitude + "," + start.longitude + "&daddr="
                        + end.latitude + "," + end.longitude));
                startActivity(intent);
                break;
        }
        return true;
    }
}
