package com.graphhopper.android;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

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

	public static void download(String from, String to,
			ProgressListener progressListener) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) new URL(from)
				.openConnection();
		conn.setDoInput(true);
		conn.setConnectTimeout(10000); // timeout 10 secs
		conn.connect();
		int length = conn.getContentLength();
		InputStream input = conn.getInputStream();
		FileOutputStream out = new FileOutputStream(to);
		try {
			byte[] buffer = new byte[4096];
			int bytesRead = -1;
			long sum = 0;
			while ((bytesRead = input.read(buffer)) != -1) {
				sum += bytesRead;
				out.write(buffer, 0, bytesRead);
				if (progressListener != null)
					progressListener.update((int) (100 * sum / length));
			}
		} finally {
			input.close();
			out.flush();
			out.close();
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
		if (index > 0)
			return str.substring(index + 1);
		return str;
	}
}
