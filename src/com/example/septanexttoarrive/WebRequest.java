package com.example.septanexttoarrive;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;

import android.os.AsyncTask;
import android.util.Log;

class WebRequest extends AsyncTask<String, Void, String> {

	protected String doInBackground(String... request) {
		DefaultHttpClient httpClient = new DefaultHttpClient(new BasicHttpParams());
		HttpGet httpGet = null;
		InputStream inputStream = null;
		String result = null;

		try {
			httpGet = new HttpGet(request[0]);
			httpGet.setHeader("Content-type", "application/json");
			
			Log.v("Req:", request[0]);

			HttpResponse response = httpClient.execute(httpGet);
			HttpEntity entity = response.getEntity();

			inputStream = entity.getContent();

			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
			StringBuilder builder = new StringBuilder();

			String line = null;
			while ((line = reader.readLine()) != null) {
				builder.append(line + "\n");
			}
			result = builder.toString();
		} catch (Exception e) { Log.e("Exception!", e.toString()); }

		try {
			inputStream.close();
		} catch (Exception e) { Log.e("Exception!", e.toString()); }

		return result;
	}

	protected void onPostExecute(String result) { }
}
