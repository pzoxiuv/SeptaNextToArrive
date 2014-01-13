package com.example.septanexttoarrive;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;

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
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class MainActivity extends Activity {
	
	class GetNetworkTask extends AsyncTask<String, Void, ArrayList<HashMap<String, String>>> {
		protected ArrayList<HashMap<String, String>> doInBackground(String... stations) {
			ArrayList<HashMap<String, String>> resultsList = new ArrayList<HashMap<String, String>>();
			HashMap<String, String> resultsMap;

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

					resultsMap = new HashMap<String, String>();
					resultsMap.put("train", train.getString("orig_line"));
					resultsMap.put("departure time", train.getString("orig_departure_time"));
					resultsList.add(resultsMap);
				}
			} catch (Exception e) { Log.e("Exception!", e.toString()); }
			
			return resultsList;
		}
		
		protected void onPostExecute(String result) { }
	}

	AutoCompleteTextView fromTextView, toTextView;
	ArrayList<HashMap<String, String>> resultsList = new ArrayList<HashMap<String, String>>();

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
		
		ListView resultsListView = (ListView)findViewById(R.id.resultsListView);
		final SimpleAdapter resultsListAdapter = new SimpleAdapter(this, resultsList, R.layout.results_row,
				new String[] {"train", "departure time"}, new int [] {R.id.rowTrainName, R.id.rowDepartureTime});
		resultsListView.setAdapter(resultsListAdapter);

		toTextView.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				boolean handled = false;
				
				if (actionId == EditorInfo.IME_ACTION_SEARCH) {
					resultsList.clear();
					try {
						resultsList.addAll(new GetNetworkTask().execute(fromTextView.getText().toString(), toTextView.getText().toString()).get());
					} catch (Exception e) { Log.e("Exception!", e.toString()); }
					resultsListAdapter.notifyDataSetChanged();

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
