package com.example.nikunjjain.sunshine;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;


public class ForecastFragment extends Fragment {

    private ArrayAdapter<String> mAdapter;

    public ForecastFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        updateWeather();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        mAdapter = new ArrayAdapter<>(getActivity(), R.layout.list_item_forecast, R.id.list_item_forecast_textview, new ArrayList<String>());

        ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(mAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent detailIntent = new Intent(getActivity(), DetailActivity.class);
                detailIntent.putExtra(Intent.EXTRA_TEXT, mAdapter.getItem(position));
                startActivity(detailIntent);
            }
        });

        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            updateWeather();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateWeather() {
        FetchWeatherTask fetchWeatherTask = new FetchWeatherTask();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String location = preferences.getString(getString(R.string.pref_location_key), getString(R.string.pref_location_default));
        fetchWeatherTask.execute(location);
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String[]> {

        private String getReadableDateString(long time) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEE MMM dd");
            return simpleDateFormat.format(time);
        }

        private String formatHighLows(double high, double low, String unitType) {

            if (unitType.equals(getString(R.string.pref_temperature_imperial))) {
                high = (high * 1.8) + 32;
                low = (low * 1.8) + 32;
            }

            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);

            String highLowStr = roundedHigh + "/" + roundedLow;
            return highLowStr;
        }

        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays) throws JSONException {
            final String KEY_LIST = "list";
            final String KEY_WEATHER = "weather";
            final String KEY_TEMPERATURE = "temp";
            final String KEY_MAX = "max";
            final String KEY_MIN = "min";
            final String KEY_MAIN_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(KEY_LIST);

            Time dayTime = new Time();
            dayTime.setToNow();

            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

            dayTime = new Time();
            String resultStrs[] = new String[numDays];
            for (int i = 0; i < weatherArray.length(); i++) {
                String day, description, highAndLow;

                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                String unitType = sharedPreferences.getString(getString(R.string.pref_temperature_key), getString(R.string.pref_temperature_default));

                JSONObject dayForecast = weatherArray.getJSONObject(i);

                long dateTime = dayTime.setJulianDay(julianStartDay + i);
                day = getReadableDateString(dateTime);

                JSONObject weatherObject = dayForecast.getJSONArray(KEY_WEATHER).getJSONObject(0);
                description = weatherObject.getString(KEY_MAIN_DESCRIPTION);

                JSONObject temperatureObject = dayForecast.getJSONObject(KEY_TEMPERATURE);

                highAndLow = formatHighLows(temperatureObject.getDouble(KEY_MAX), temperatureObject.getDouble(KEY_MIN), unitType);
                resultStrs[i] = day + " - " + description + " - " + highAndLow;
            }
            return resultStrs;
        }

        @Override
        protected String[] doInBackground(String... params) {
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;
            String forecastJsonStr = null;

            if (params.length == 0) {
                return null;
            }
            try {

                final String API_KEY = "b3bd28aa4074843c07857524c160d234";

                Uri.Builder builder = new Uri.Builder();
                builder.scheme("http")
                        .authority("api.openweathermap.org")
                        .appendPath("data")
                        .appendPath("2.5")
                        .appendPath("forecast")
                        .appendPath("daily")
                        .appendQueryParameter("appid", API_KEY)
                        .appendQueryParameter("q", params[0])
                        .appendQueryParameter("mode", "json")
                        .appendQueryParameter("units", "metric")
                        .appendQueryParameter("cnt", "10");

                URL url = new URL(builder.build().toString());

                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    forecastJsonStr = null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    forecastJsonStr = null;
                }
                forecastJsonStr = buffer.toString();
            } catch (IOException e) {
                Log.e("PlaceholderFragment", "Error ", e);
                forecastJsonStr = null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e("PlaceholderFragment", "Error closing stream", e);
                    }
                }
                try {
                    return getWeatherDataFromJson(forecastJsonStr, 10);
                } catch (JSONException e) {
                    Log.e("Nikunj", e.getMessage(), e);
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(String[] strings) {
            if (strings != null) {
                mAdapter.clear();
                mAdapter.addAll(strings);
            }
        }
    }
}