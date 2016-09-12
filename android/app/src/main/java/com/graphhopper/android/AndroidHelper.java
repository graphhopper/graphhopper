package com.graphhopper.android;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public class AndroidHelper {
    public static List<String> readFile(Reader simpleReader) throws IOException {
        BufferedReader reader = new BufferedReader(simpleReader);
        try {
            List<String> res = new ArrayList<String>();
            String line;
            while ((line = reader.readLine()) != null) {
                res.add(line);
            }
            return res;
        } finally {
            reader.close();
        }
    }

    public static boolean isFastDownload(Context ctx) {
        ConnectivityManager mgrConn = (ConnectivityManager) ctx
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        return mgrConn.getActiveNetworkInfo() != null
                && mgrConn.getActiveNetworkInfo().getState() == NetworkInfo.State.CONNECTED;
        // TelephonyManager mgrTel = (TelephonyManager)
        // ctx.getSystemService(Context.TELEPHONY_SERVICE);
        // || mgrTel.getNetworkType() == TelephonyManager.NETWORK_TYPE_UMTS) {
    }

    public static String getFileName(String str) {
        int index = str.lastIndexOf("/");
        if (index > 0) {
            return str.substring(index + 1);
        }
        return str;
    }
}
