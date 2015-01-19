package com.graphhopper.android;

import com.graphhopper.util.Downloader;
import com.graphhopper.util.Helper;
import com.graphhopper.util.ProgressListener;
import com.graphhopper.util.Unzipper;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class AndroidDownloader extends Downloader
{

    public AndroidDownloader()
    {
        super("GraphHopper Android");
    }

    @Override
    public void downloadAndUnzip( String url, String toFolder, final ProgressListener progressListener ) throws IOException
    {
        HttpEntity entity = getEntity(url);
        InputStream iStream = entity.getContent();
        final long length = entity.getContentLength();

        new Unzipper().unzip(iStream, new File(toFolder), new ProgressListener()
        {
            @Override
            public void update( long sumBytes )
            {
                progressListener.update((int) (100 * sumBytes / length));
            }
        });
    }

    @Override
    public String downloadAsString( String url ) throws IOException
    {
        return Helper.isToString(getEntity(url).getContent());
    }

    // There is something broken on Android with HTTPS and Android HttpURLConnection.
    // Probably for 4.* only? See #251 for discussion and https://developer.android.com/training/articles/security-ssl.html
    private HttpEntity getEntity( String url )
    {        
        HttpClient httpclient = new DefaultHttpClient();
        HttpGet httpget = new HttpGet(url);
        try
        {
            HttpResponse response = httpclient.execute(httpget);
            HttpEntity entity = response.getEntity();
            if (entity == null)
                throw new RuntimeException("no entity for URL " + url);

            return entity;

        } catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }
}
