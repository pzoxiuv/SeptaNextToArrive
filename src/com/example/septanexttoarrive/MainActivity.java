package com.example.septanexttoarrive;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class MainActivity extends Activity implements LocationListener, OnItemSelectedListener {

	private String transportationMethod = "walking";	// Either walking or driving

	HashMap<String, String> stationsMap = null;			// HashMap, keys=station name values=latitude,longitude coordinates of station

	/* List of results.  Each result is stored in a HashMap, with the keys "train" (for train name) and "departure time". */
	private ArrayList<HashMap<String, String>> resultsList = new ArrayList<HashMap<String, String>>();
	private AutoCompleteTextView fromTextView, toTextView;
	private ResultsAdapter resultsListAdapter = null;

	private LocationManager locManager = null;
	private Location currentLoc = null;
	private int locUpdatesReceived = 0;	// We only need enough location updates to get a fairly accurate reading of where we are,
										// so keep track of how many updates we get and stop keeping track after a few

	private long secsToStation = 0;		// How many seconds Google Maps says we are from the station

	/* AsyncTask for querying Google Maps and getting how many seconds it'll take to get from our current location to the starting station */
	class GetTravelTimes extends AsyncTask<String, Void, String> {

		protected String doInBackground(String... destination) {
			DefaultHttpClient httpClient = new DefaultHttpClient(new BasicHttpParams());
			HttpGet httpGet = null;
			InputStream inputStream = null;
			String result = null;

			while (currentLoc == null);	/* Wait to figure out where we are */

			try {
				httpGet = new HttpGet(("http://maps.googleapis.com/maps/api/directions/json?"
						+ "origin=" + currentLoc.getLatitude() + "," + currentLoc.getLongitude()
						+ "&destination=" + destination[0] + "&sensor=true&mode=" + transportationMethod).replace(" ", "%20"));
				httpGet.setHeader("Content-type", "application/json");

				Log.v("Req:", ("http://maps.googleapis.com/maps/api/directions/json?"
						+ "origin=" + currentLoc.getLatitude() + "," + currentLoc.getLongitude()
						+ "&destination=" + destination[0] + "&sensor=true&mode=" + transportationMethod).replace(" ", "%20"));

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

			JSONObject directionsObject = null;
			String timeValue = null;

			try {
				/* See <Google Maps API url> for sample results */
				directionsObject = new JSONObject(result);
				JSONArray routesArray = directionsObject.getJSONArray("routes");
				JSONObject route = (JSONObject)routesArray.get(0);	// Assume first route is the one taken (should be approx. same time anyway)
				JSONArray legsArray = route.getJSONArray("legs");
				JSONObject leg = (JSONObject)legsArray.get(0);		// Assume just one leg in the trip
				JSONObject distance = leg.getJSONObject("duration");

				timeValue = distance.getString("text");
				secsToStation = Integer.parseInt(distance.getString("value"));
			} catch (Exception e) { Log.e("Exception!", e.toString()); }

			return timeValue;
		}

		protected void onPostExecute(String result) { }
	}

	/* AsyncTask for querying SEPTA to get NextToArrive data */
	class GetTrainTimes extends AsyncTask<String, Void, ArrayList<HashMap<String, String>>> {

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
				/* See e.g. www3.septa.org/hackathon/NextToArrive/Doylestown/Suburban%20Station/10 for sample results */
				trainArray = new JSONArray(result);
				for (int i=0; i<trainArray.length(); i++) {
					JSONObject train = (JSONObject)trainArray.get(i);
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

	/* ResultsAdapter for ListView of results.  Only difference from SimpleAdapter is ResultsAdapter
	 * prints the results in different colors based on the likelihood the user can make it to the
	 * station in time.
	 */
	public class ResultsAdapter extends SimpleAdapter {
		public ResultsAdapter(Context context, ArrayList list, int id, String keys[], int ids[]) {
			super(context, list, id, keys, ids);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			LinearLayout layout = (LinearLayout) super.getView(position, convertView, parent);

			long secsToSpare = getSecsToSpare(((TextView)(layout.getChildAt(1))).getText().toString());

			if (secsToSpare < 2*60) {			// Under 2 minutes to spare, good chance of missing train
				((TextView)(layout.getChildAt(0))).setTextColor(getResources().getColor(R.color.lowChanceColor));
				((TextView)(layout.getChildAt(1))).setTextColor(getResources().getColor(R.color.lowChanceColor));
			} else if (secsToSpare < 10*60) {	// More than 2, but less than 7 minutes to spare - probably make it...
				((TextView)(layout.getChildAt(0))).setTextColor(getResources().getColor(R.color.medChanceColor));
				((TextView)(layout.getChildAt(1))).setTextColor(getResources().getColor(R.color.medChanceColor));
			} else {							// More than 7 minutes, pretty safe
				((TextView)(layout.getChildAt(0))).setTextColor(getResources().getColor(R.color.highChanceColor));
				((TextView)(layout.getChildAt(1))).setTextColor(getResources().getColor(R.color.highChanceColor));
			}

			return layout;
		}

		/* Calculates with how many seconds to spare the user will have when they arrive at the station.  The time parameter
		 * is what time the train departs the station.
		 */
		private	long getSecsToSpare(String time) {
			long secs = 0;

			/* To figure out seconds to spare, get current seconds since midnight, and add how
			 * long Google says it'll take to get to the station.  Subtract from that secs since midnight
			 * that the train will depart at, and there you go.
			 */

			Calendar cal = Calendar.getInstance();
			long now = cal.getTimeInMillis();
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);

			/* Get departure time of train, in secs since midnight */
			time = time.trim();
			String splitTime[] = time.split(":|[AP]");	// Split time into hours and minutes. Time format is HH:MM[A|P]M
			secs = 3600*Integer.parseInt(splitTime[0]);	// Hours
			if (time.charAt(time.length()-2) == 'P' && Integer.parseInt(splitTime[0]) != 12)
				secs += 12*3600;						// PM, add seconds for first 12 hours of the day
			else if (time.charAt(time.length()-2) == 'A' && (now/1000) > 12*3600)	// If it's after 12PM, assume any "AM" departure...
				secs += 24*3600;						//  times are for the "next day", so add seconds for previous 24 hours

			secs += 60*Integer.parseInt(splitTime[1]);	// Minutes

			return (secs - (((now - cal.getTimeInMillis())/1000) + secsToStation));
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		locManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
		locManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);

		Spinner transportationSpinner = (Spinner)findViewById(R.id.transportationMethod);
		ArrayAdapter<CharSequence> transportationOptionsAdapter = ArrayAdapter.createFromResource(this, R.array.transportation_options,
				android.R.layout.simple_spinner_item);
		transportationOptionsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		transportationSpinner.setAdapter(transportationOptionsAdapter);
		transportationSpinner.setOnItemSelectedListener(this);

		fromTextView = (AutoCompleteTextView) findViewById(R.id.fromTextField);
		toTextView = (AutoCompleteTextView) findViewById(R.id.toTextField);

		loadStationsMap();
		/* For autocomplete in the to/from station text fields */
		ArrayAdapter<String> stationsAdapter = 
				new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
				stationsMap.keySet().toArray(new String[stationsMap.keySet().size()]));

		fromTextView.setAdapter(stationsAdapter);
		toTextView.setAdapter(stationsAdapter);

		fromTextView.requestFocus();

		ListView resultsListView = (ListView)findViewById(R.id.resultsListView);
		resultsListAdapter = new ResultsAdapter(this, resultsList, R.layout.results_row,
				new String[] {"train", "departure time"}, new int [] {R.id.rowTrainName, R.id.rowDepartureTime});
		resultsListView.setAdapter(resultsListAdapter);

		toTextView.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				boolean handled = false;

				if (actionId == EditorInfo.IME_ACTION_SEARCH) {
					try {
						TextView transportationTime = (TextView)findViewById(R.id.transportationTime);
						transportationTime.setText(new GetTravelTimes().execute(stationsMap.get(fromTextView.getText().toString())).get());
					} catch (Exception e) { Log.e("Exception", e.toString()); }

					resultsList.clear();
					try {
						resultsList.addAll(new GetTrainTimes().execute(fromTextView.getText().toString(), toTextView.getText().toString()).get());
					} catch (Exception e) { Log.e("Exception!", e.toString()); }
					resultsListAdapter.notifyDataSetChanged();

					/* Make sure the keyboard goes away after displaying results */
					InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(toTextView.getWindowToken(), InputMethodManager.RESULT_UNCHANGED_SHOWN);
					handled = true;
				}

				return handled;
			}
		});
	}

	/* Splits each String in the stations_name string-array into it's two parts, the station name and the station coordinates,
	 * and fills the stationsMap HashMap with them.
	 */
	private void loadStationsMap() {
		stationsMap = new HashMap<String, String>();

		String combinedNameCoor[] = getResources().getStringArray(R.array.station_names);
		for (String s : combinedNameCoor) {
			stationsMap.put(s.split("\\|")[0], s.split("\\|")[1]);
		}
	}

	/* Called when user presses the refresh button on the Action Bar, basically re-get everything and update results */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_refresh) {
			try {
				TextView transportationTime = (TextView)findViewById(R.id.transportationTime);
				transportationTime.setText(new GetTravelTimes().execute(stationsMap.get(fromTextView.getText().toString())).get());
			} catch (Exception e) { Log.e("Exception", e.toString()); }

			resultsList.clear();
			try {
				resultsList.addAll(new GetTrainTimes().execute(fromTextView.getText().toString(), toTextView.getText().toString()).get());
			} catch (Exception e) { Log.e("Exception!", e.toString()); }
			resultsListAdapter.notifyDataSetChanged();

			InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(toTextView.getWindowToken(), InputMethodManager.RESULT_UNCHANGED_SHOWN);

			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	/* Transportation method spinner changed */
	public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
		transportationMethod = (String)parent.getItemAtPosition(pos);
		if (fromTextView.getText().toString().trim().length() != 0) {
			try {
				/* Get new directions from Google and update the label with how long it'll take now to get to the station */
				TextView transportationTime = (TextView)findViewById(R.id.transportationTime);
				transportationTime.setText(new GetTravelTimes().execute(stationsMap.get(fromTextView.getText().toString())).get());
			} catch (Exception e) { Log.e("Exception", e.toString()); }

			/* If there are results displayed already, redraw them so the colors reflect the current travel time to the station */
			if (resultsListAdapter != null)
				resultsListAdapter.notifyDataSetChanged();
		}
	}

	/* If somehow there's nothing selected by the spinner, default to walking to the station */
	public void onNothingSelected(AdapterView<?> parent) {
		transportationMethod = "walking";
	}

	public void onLocationChanged(Location location) {
		currentLoc = location;
		locUpdatesReceived++;
		if (locUpdatesReceived > 3) {	// Assume that after a few updates, we have a pretty good location
			locManager.removeUpdates(this);
		}
	}

	/* Needed for LocationListener */
	public void onProviderDisabled(String arg) {}
	public void onProviderEnabled(String arg) {}
	public void onStatusChanged(String arg1, int arg2, Bundle arg3) {}
}
