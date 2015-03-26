package com.graphhopper.android;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.MapPosition;
import org.mapsforge.core.model.Point;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.Layers;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapDataStore;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.InternalRenderTheme;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.util.Constants;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import com.graphhopper.util.ProgressListener;
import com.graphhopper.util.StopWatch;

public class MainActivity extends Activity
{
    private MapView mapView;
    private GraphHopper hopper;
    private LatLong start;
    private LatLong end;
    private Spinner localSpinner;
    private Button localButton;
    private Spinner remoteSpinner;
    private Button remoteButton;
    private volatile boolean prepareInProgress = false;
    private volatile boolean shortestPathRunning = false;
    private String currentArea = "berlin";
    private String fileListURL = "https://graphhopper.com/public/maps/0.5/";
    private String prefixURL = fileListURL;
    private String downloadURL;
    private File mapsFolder;
    private TileCache tileCache;

    protected boolean onMapTap( LatLong tapLatLong, Point layerXY, Point tapXY )
    {
        if (!isReady())
            return false;

        if (shortestPathRunning)
        {
            logUser("Calculation still in progress");
            return false;
        }
        Layers layers = mapView.getLayerManager().getLayers();

        if (start != null && end == null)
        {
            end = tapLatLong;
            shortestPathRunning = true;
            Marker marker = createMarker(tapLatLong, R.drawable.flag_red);
            if (marker != null)
            {
                layers.add(marker);
            }

            calcPath(start.latitude, start.longitude, end.latitude,
                    end.longitude);
        } else
        {
            start = tapLatLong;
            end = null;
            // remove all layers but the first one, which is the map
            while (layers.size() > 1)
            {
                layers.remove(1);
            }

            Marker marker = createMarker(start, R.drawable.flag_green);
            if (marker != null)
            {
                layers.add(marker);
            }
        }
        return true;
    }

    @Override
    protected void onCreate( Bundle savedInstanceState )
    {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        AndroidGraphicFactory.createInstance(getApplication());

        mapView = new MapView(this);
        mapView.setClickable(true);
        mapView.setBuiltInZoomControls(true);

        tileCache = AndroidUtil.createTileCache(this, getClass().getSimpleName(), mapView.getModel().displayModel.getTileSize(),
                1f, mapView.getModel().frameBufferModel.getOverdrawFactor());

        final EditText input = new EditText(this);
        input.setText(currentArea);
        boolean greaterOrEqKitkat = Build.VERSION.SDK_INT >= 19;
        if (greaterOrEqKitkat)
        {
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
            {
                logUser("GraphHopper is not usable without an external storage!");
                return;
            }
            mapsFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "/graphhopper/maps/");
        } else
            mapsFolder = new File(Environment.getExternalStorageDirectory(), "/graphhopper/maps/");

        if (!mapsFolder.exists())
            mapsFolder.mkdirs();

        TextView welcome = (TextView) findViewById(R.id.welcome);
        welcome.setText("Welcome to GraphHopper " + Constants.VERSION + "!");
        welcome.setPadding(6, 3, 3, 3);
        localSpinner = (Spinner) findViewById(R.id.locale_area_spinner);
        localButton = (Button) findViewById(R.id.locale_button);
        remoteSpinner = (Spinner) findViewById(R.id.remote_area_spinner);
        remoteButton = (Button) findViewById(R.id.remote_button);
        // TODO get user confirmation to download
        // if (AndroidHelper.isFastDownload(this))
        chooseAreaFromRemote();
        chooseAreaFromLocal();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        if (hopper != null)
            hopper.close();

        hopper = null;
        // necessary?
        System.gc();
    }

    boolean isReady()
    {
        // only return true if already loaded
        if (hopper != null)
            return true;

        if (prepareInProgress)
        {
            logUser("Preparation still in progress");
            return false;
        }
        logUser("Prepare finished but hopper not ready. This happens when there was an error while loading the files");
        return false;
    }

    private void initFiles( String area )
    {
        prepareInProgress = true;
        currentArea = area;
        downloadingFiles();
    }

    private void chooseAreaFromLocal()
    {
        List<String> nameList = new ArrayList<String>();
        String[] files = mapsFolder.list(new FilenameFilter()
        {
            @Override
            public boolean accept( File dir, String filename )
            {
                return filename != null
                        && (filename.endsWith(".ghz") || filename
                        .endsWith("-gh"));
            }
        });
        for (String file : files)
        {
            nameList.add(file);
        }

        if (nameList.isEmpty())
            return;

        chooseArea(localButton, localSpinner, nameList,
                new MySpinnerListener()
                {
                    @Override
                    public void onSelect( String selectedArea, String selectedFile )
                    {
                        initFiles(selectedArea);
                    }
                });
    }

    private void chooseAreaFromRemote()
    {
        new GHAsyncTask<Void, Void, List<String>>()
        {
            protected List<String> saveDoInBackground( Void... params )
                    throws Exception
            {
                String[] lines = new AndroidDownloader().downloadAsString(fileListURL).split("\n");
                List<String> res = new ArrayList<String>();
                for (String str : lines)
                {
                    int index = str.indexOf("href=\"");
                    if (index >= 0)
                    {
                        index += 6;
                        int lastIndex = str.indexOf(".ghz", index);
                        if (lastIndex >= 0)
                            res.add(prefixURL + str.substring(index, lastIndex)
                                    + ".ghz");
                    }
                }

                return res;
            }

            @Override
            protected void onPostExecute( List<String> nameList )
            {
                if(nameList.isEmpty())
                {
                    logUser("No maps created for your version!? " + fileListURL);
                    return;
                } else if (hasError())
                {
                    getError().printStackTrace();
                    logUser("Are you connected to the internet? Problem while fetching remote area list: "
                            + getErrorMessage());
                    return;
                }
                MySpinnerListener spinnerListener = new MySpinnerListener()
                {
                    @Override
                    public void onSelect( String selectedArea, String selectedFile )
                    {
                        if (selectedFile == null
                                || new File(mapsFolder, selectedArea + ".ghz").exists()
                                || new File(mapsFolder, selectedArea + "-gh").exists())
                        {
                            downloadURL = null;
                        } else
                        {
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

    private void chooseArea( Button button, final Spinner spinner,
            List<String> nameList, final MySpinnerListener mylistener )
    {
        final Map<String, String> nameToFullName = new TreeMap<String, String>();
        for (String fullName : nameList)
        {
            String tmp = Helper.pruneFileEnd(fullName);
            if (tmp.endsWith("-gh"))
                tmp = tmp.substring(0, tmp.length() - 3);

            tmp = AndroidHelper.getFileName(tmp);
            nameToFullName.put(tmp, fullName);
        }
        nameList.clear();
        nameList.addAll(nameToFullName.keySet());
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_dropdown_item, nameList);
        spinner.setAdapter(spinnerArrayAdapter);
        button.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick( View v )
            {
                Object o = spinner.getSelectedItem();
                if (o != null && o.toString().length() > 0 && !nameToFullName.isEmpty())
                {
                    String area = o.toString();
                    mylistener.onSelect(area, nameToFullName.get(area));
                } else
                {
                    mylistener.onSelect(null, null);
                }
            }
        });
    }

    public interface MySpinnerListener
    {
        void onSelect( String selectedArea, String selectedFile );
    }

    void downloadingFiles()
    {
        final File areaFolder = new File(mapsFolder, currentArea + "-gh");
        if (downloadURL == null || areaFolder.exists())
        {
            loadMap(areaFolder);
            return;
        }

        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("Downloading and uncompressing " + downloadURL);
        dialog.setIndeterminate(false);
        dialog.setMax(100);
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.show();

        new GHAsyncTask<Void, Integer, Object>()
        {
            protected Object saveDoInBackground( Void... _ignore )
                    throws Exception
            {
                String localFolder = Helper.pruneFileEnd(AndroidHelper.getFileName(downloadURL));
                localFolder = new File(mapsFolder, localFolder + "-gh").getAbsolutePath();
                log("downloading & unzipping " + downloadURL + " to " + localFolder);
                AndroidDownloader downloader = new AndroidDownloader();
                downloader.setTimeout(30000);
                downloader.downloadAndUnzip(downloadURL, localFolder,
                        new ProgressListener()
                        {
                            @Override
                            public void update( long val )
                            {
                                publishProgress((int) val);
                            }
                        });
                return null;
            }

            protected void onProgressUpdate( Integer... values )
            {
                super.onProgressUpdate(values);
                dialog.setProgress(values[0]);
            }

            protected void onPostExecute( Object _ignore )
            {
                dialog.hide();
                if (hasError())
                {
                    String str = "An error happend while retrieving maps:" + getErrorMessage();
                    log(str, getError());
                    logUser(str);
                } else
                {
                    loadMap(areaFolder);
                }
            }
        }.execute();
    }

    void loadMap( File areaFolder )
    {
        logUser("loading map");
        MapDataStore mapDataStore = new MapFile(new File(areaFolder, currentArea + ".map"));

        mapView.getLayerManager().getLayers().clear();

        TileRendererLayer tileRendererLayer = new TileRendererLayer(tileCache, mapDataStore,
        		mapView.getModel().mapViewPosition, false, true, AndroidGraphicFactory.INSTANCE)
                {
                    @Override
                    public boolean onLongPress( LatLong tapLatLong, Point layerXY, Point tapXY )
                    {
                        return onMapTap(tapLatLong, layerXY, tapXY);
                    }
                };
        tileRendererLayer.setTextScale(1.5f);
        tileRendererLayer.setXmlRenderTheme(InternalRenderTheme.OSMARENDER);
        mapView.getModel().mapViewPosition.setMapPosition(new MapPosition(mapDataStore.boundingBox().getCenterPoint(), (byte) 15));
        mapView.getLayerManager().getLayers().add(tileRendererLayer);

        setContentView(mapView);
        loadGraphStorage();
    }

    void loadGraphStorage()
    {
        logUser("loading graph (" + Constants.VERSION + ") ... ");
        new GHAsyncTask<Void, Void, Path>()
        {
            protected Path saveDoInBackground( Void... v ) throws Exception
            {
                GraphHopper tmpHopp = new GraphHopper().forMobile();
                tmpHopp.load(new File(mapsFolder, currentArea).getAbsolutePath());
                log("found graph " + tmpHopp.getGraph().toString() + ", nodes:" + tmpHopp.getGraph().getNodes());
                hopper = tmpHopp;
                return null;
            }

            protected void onPostExecute( Path o )
            {
                if (hasError())
                {
                    logUser("An error happend while creating graph:"
                            + getErrorMessage());
                } else
                {
                    logUser("Finished loading graph. Press long to define where to start and end the route.");
                }

                finishPrepare();
            }
        }.execute();
    }

    private void finishPrepare()
    {
        prepareInProgress = false;
    }

    private Polyline createPolyline( GHResponse response )
    {
        Paint paintStroke = AndroidGraphicFactory.INSTANCE.createPaint();
        paintStroke.setStyle(Style.STROKE);
        paintStroke.setColor(Color.argb(200, 0, 0xCC, 0x33));
        paintStroke.setDashPathEffect(new float[]
        {
            25, 15
        });
        paintStroke.setStrokeWidth(8);

        // TODO: new mapsforge version wants an mapsforge-paint, not an android paint.
        // This doesn't seem to support transparceny
        //paintStroke.setAlpha(128);
        Polyline line = new Polyline((org.mapsforge.core.graphics.Paint) paintStroke, AndroidGraphicFactory.INSTANCE);
        List<LatLong> geoPoints = line.getLatLongs();
        PointList tmp = response.getPoints();
        for (int i = 0; i < response.getPoints().getSize(); i++)
        {
            geoPoints.add(new LatLong(tmp.getLatitude(i), tmp.getLongitude(i)));
        }

        return line;
    }

    private Marker createMarker( LatLong p, int resource )
    {
        Drawable drawable = getResources().getDrawable(resource);
        Bitmap bitmap = AndroidGraphicFactory.convertToBitmap(drawable);
        return new Marker(p, bitmap, 0, -bitmap.getHeight() / 2);
    }

    public void calcPath( final double fromLat, final double fromLon,
            final double toLat, final double toLon )
    {

        log("calculating path ...");
        new AsyncTask<Void, Void, GHResponse>()
        {
            float time;

            protected GHResponse doInBackground( Void... v )
            {
                StopWatch sw = new StopWatch().start();
                GHRequest req = new GHRequest(fromLat, fromLon, toLat, toLon).
                        setAlgorithm(AlgorithmOptions.DIJKSTRA_BI);
                req.getHints().
                        put("instructions", "false");
                GHResponse resp = hopper.route(req);
                time = sw.stop().getSeconds();
                return resp;
            }

            protected void onPostExecute( GHResponse resp )
            {
                if (!resp.hasErrors())
                {
                    log("from:" + fromLat + "," + fromLon + " to:" + toLat + ","
                            + toLon + " found path with distance:" + resp.getDistance()
                            / 1000f + ", nodes:" + resp.getPoints().getSize() + ", time:"
                            + time + " " + resp.getDebugInfo());
                    logUser("the route is " + (int) (resp.getDistance() / 100) / 10f
                            + "km long, time:" + resp.getTime() / 60000f + "min, debug:" + time);

                    mapView.getLayerManager().getLayers().add(createPolyline(resp));
                    //mapView.redraw();
                } else
                {
                    logUser("Error:" + resp.getErrors());
                }
                shortestPathRunning = false;
            }
        }.execute();
    }

    private void log( String str )
    {
        Log.i("GH", str);
    }

    private void log( String str, Throwable t )
    {
        Log.i("GH", str, t);
    }

    private void logUser( String str )
    {
        log(str);
        Toast.makeText(this, str, Toast.LENGTH_LONG).show();
    }
    private static final int NEW_MENU_ID = Menu.FIRST + 1;

    @Override
    public boolean onCreateOptionsMenu( Menu menu )
    {
        super.onCreateOptionsMenu(menu);
        menu.add(0, NEW_MENU_ID, 0, "Google");
        // menu.add(0, NEW_MENU_ID + 1, 0, "Other");
        return true;
    }

    public boolean onOptionsItemSelected( MenuItem item )
    {
        switch (item.getItemId())
        {
            case NEW_MENU_ID:
                if (start == null || end == null)
                {
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
