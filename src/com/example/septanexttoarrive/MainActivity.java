package com.example.septanexttoarrive;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.Iterator;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class MainActivity extends Activity {
	
	class GetNetworkTask extends AsyncTask<String, Void, String> {
		protected String doInBackground(String... stations) {
			DefaultHttpClient httpClient = new DefaultHttpClient(new BasicHttpParams());
			HttpGet httpGet = null;
			InputStream inputStream = null;
			String result = null;
			
			try {
				httpGet = new HttpGet("http://www3.septa.org/hackathon/NextToArrive/" + stations[0].replace(" ", "%20") + "/" 
						+ stations[1].replace(" ", "%20") + "/10");
				httpGet.setHeader("Content-type", "application/json");
				
				Log.v("Req:", "http://www3.septa.org/hackathon/NextToArrive/" + stations[0].replace(" ", "%20") + "/" 
						+ stations[1].replace(" ", "%20") + "/10");
				
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
			
			JSONArray trainArray = null;
			
			try {
				trainArray = new JSONArray(result);
				for (int i=0; i<trainArray.length(); i++) {
					JSONObject train = (JSONObject)trainArray.get(i);
					Log.v("Train: ", train.getString("orig_line"));
					Log.v("Departure Time:", train.getString("orig_departure_time"));
				}
			} catch (Exception e) { Log.e("Exception!", e.toString()); }
			
			return result;
		}
		
		protected void onPostExecute(String result) { }
	}

	AutoCompleteTextView fromTextView, toTextView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		fromTextView = (AutoCompleteTextView) findViewById(R.id.fromTextField);
		toTextView = (AutoCompleteTextView) findViewById(R.id.toTextField);
		
		String stations_list[] = getResources().getStringArray(R.array.station_names);
		
		ArrayAdapter<String> stationsAdapter = 
				new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, stations_list);
		
		fromTextView.setAdapter(stationsAdapter);
		toTextView.setAdapter(stationsAdapter);
		
		fromTextView.requestFocus();
		
		toTextView.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				boolean handled = false;
				
				if (actionId == EditorInfo.IME_ACTION_SEARCH) {
					new GetNetworkTask().execute(fromTextView.getText().toString(), toTextView.getText().toString());
					InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(toTextView.getWindowToken(), 0);
					handled = true;
				}
				
				return handled;
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
}
