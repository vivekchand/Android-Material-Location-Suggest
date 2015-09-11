package com.eugene.fithealth.Activities;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.eugene.fithealth.FatSecretSearchAndGet.FatSecretSearchItem;
import com.eugene.fithealth.LogQuickSearchData.LogQuickSearch;
import com.eugene.fithealth.LogQuickSearchData.LogQuickSearchAdapter;
import com.eugene.fithealth.R;
import com.eugene.fithealth.SearchListView.Item;
import com.eugene.fithealth.SearchListView.SearchAdapter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.location.places.AutocompletePrediction;
import com.google.android.gms.location.places.AutocompletePredictionBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener {
    private Resources res;
    private Toolbar toolbar;
    private TabLayout tabs;
    private View line_divider, toolbar_shadow;
    private RelativeLayout view_search;
    private CardView card_search;
    private ImageView image_search_back, clearSearch;
    private EditText edit_text_search;
    private ListView searchHistoryListView, searchResultListView;
    private LogQuickSearchAdapter logQuickSearchAdapter;
    private Set<String> searchHistoryCacheSet;
    private ArrayList<Item> searchResults;
    private FatSecretSearchItem mFatSecretSearch;
    private SearchAdapter searchAdapter;
    private ProgressBar marker_progress;
    private String brand;
    private AsyncTask<String, String, String> mAsyncTask;
    private GoogleApiClient mGoogleApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        res = this.getResources();

        // Construct a GoogleApiClient for the {@link Places#GEO_DATA_API} using AutoManage
        // functionality, which automatically sets up the API client to handle Activity lifecycle
        // events. If your activity does not extend FragmentActivity, make sure to call connect()
        // and disconnect() explicitly.
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, 0 /* clientId */, this)
                .addApi(Places.GEO_DATA_API)
                .build();


        findViews();

        marker_progress.getIndeterminateDrawable().setColorFilter(Color.parseColor("#FFFFFF"),//Pink color
                android.graphics.PorterDuff.Mode.MULTIPLY);

        // Adapter for storing search history
        logQuickSearchAdapter = new LogQuickSearchAdapter(this, 0, LogQuickSearch.all());
        searchHistoryListView.setAdapter(logQuickSearchAdapter);

        // Adapter for showing search results
        searchResults = new ArrayList<>();
        searchAdapter = new SearchAdapter(this, searchResults);
        searchResultListView.setAdapter(searchAdapter);

        // Cache for storing search history
        searchHistoryCacheSet = new HashSet<>();

        // Makes the actual search
        mFatSecretSearch = new FatSecretSearchItem();

        setupTypeFace();
        setupSearchActionHandlers();
        handleSearch();
        showLineDividerIfSearchHistoryIsNotEmpty();
    }

    private void findViews() {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        tabs = (TabLayout) findViewById(R.id.tabs);
        view_search = (RelativeLayout) findViewById(R.id.view_search);
        line_divider = findViewById(R.id.line_divider);
        toolbar_shadow = findViewById(R.id.toolbar_shadow);
        edit_text_search = (EditText) findViewById(R.id.edit_text_search);
        card_search = (CardView) findViewById(R.id.card_search);
        image_search_back = (ImageView) findViewById(R.id.image_search_back);
        clearSearch = (ImageView) findViewById(R.id.clearSearch);
        searchHistoryListView = (ListView) findViewById(R.id.listView);
        searchResultListView = (ListView) findViewById(R.id.listContainer);
        marker_progress = (ProgressBar) findViewById(R.id.marker_progress);
    }

    private void setupSearchActionHandlers() {
        searchResultListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                updateSearchHistory(searchResults.get(position).getTitle());
            }
        });

        searchHistoryListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                LogQuickSearch logQuickSearch = logQuickSearchAdapter.getItem(position);
                edit_text_search.setText(logQuickSearch.getName());
                searchHistoryListView.setVisibility(View.GONE);

                ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(edit_text_search.getWindowToken(), 0);

                toolbar_shadow.setVisibility(View.GONE);
                searchPlaces(logQuickSearch.getName(), 0);
            }
        });
        edit_text_search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            // Decides to whether show the close button or not
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (edit_text_search.getText().toString().length() == 0) {
                    logQuickSearchAdapter = new LogQuickSearchAdapter(MainActivity.this, 0, LogQuickSearch.all());
                    searchHistoryListView.setAdapter(logQuickSearchAdapter);

                    clearItems();
                    // no button
                    clearSearch.setImageBitmap(null);

                    showLineDividerIfSearchHistoryIsNotEmpty();
                } else {
                    if(edit_text_search.getText().toString().length() > 2 ) {
                        searchHistoryListView.setVisibility(View.GONE);
                        // Make the actual search
                        searchPlaces(edit_text_search.getText().toString(), 0);
                    }

                    // set close button
                    clearSearch.setImageResource(R.mipmap.ic_close);

                    showLineDividerIfSearchHistoryIsNotEmpty();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        clearSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (edit_text_search.getText().toString().length() == 0) {
                    clearItems();
                } else {
                    mAsyncTask.cancel(true);
                    edit_text_search.setText("");
                    searchHistoryListView.setVisibility(View.VISIBLE);

                    clearItems();

                    ((InputMethodManager) MainActivity.this.getSystemService(Context.INPUT_METHOD_SERVICE)).toggleSoftInput(
                            InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
                    showLineDividerIfSearchHistoryIsNotEmpty();
                }
            }
        });
    }


    private void updateSearchHistory(String item) {
        for (int i = 0; i < logQuickSearchAdapter.getCount(); i++) {
            LogQuickSearch ls = logQuickSearchAdapter.getItem(i);
            String name = ls.getName();
            searchHistoryCacheSet.add(name.toUpperCase());
        }
        if (searchHistoryCacheSet.add(item.toUpperCase())) {
            LogQuickSearch recentLog = new LogQuickSearch();
            recentLog.setName(item);
            recentLog.setDate(new Date());
            recentLog.save();
            logQuickSearchAdapter.add(recentLog);
            logQuickSearchAdapter.notifyDataSetChanged();
        }
    }

    private void handleSearch() {
        image_search_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        edit_text_search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    if (edit_text_search.getText().toString().trim().length() > 0) {
                        clearItems();
                        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(edit_text_search.getWindowToken(), 0);
                        updateSearchHistory(edit_text_search.getText().toString());
                        searchHistoryListView.setVisibility(View.GONE);

                        // Make the actual search
                        searchPlaces(edit_text_search.getText().toString(), 0);

                        toolbar_shadow.setVisibility(View.GONE);
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void showLineDividerIfSearchHistoryIsNotEmpty() {
        if (logQuickSearchAdapter.getCount() == 0) {
            line_divider.setVisibility(View.GONE);
        } else {
            line_divider.setVisibility(View.VISIBLE);
        }
    }

    private void setupTypeFace() {
        Typeface roboto_regular = Typeface.createFromAsset(getAssets(), "fonts/Roboto-Regular.ttf");
        edit_text_search.setTypeface(roboto_regular);
    }

    /**
     * Handle FatSecret Search
     */

    private void clearItems() {
        searchResultListView.setVisibility(View.GONE);
        searchResults.clear();
        searchAdapter.notifyDataSetChanged();
    }

    private void searchPlaces(final String item, final int page_num) {
        mAsyncTask = new AsyncTask<String, String, String>() {
            @Override
            protected void onPreExecute() {
                marker_progress.setVisibility(View.VISIBLE);
            }

            @Override
            protected String doInBackground(String... arg0) {
                if (mGoogleApiClient.isConnected()) {
                    String query = item;
                    LatLngBounds BOUNDS_GREATER_SYDNEY = new LatLngBounds(
                            new LatLng(-34.041458, 150.790100), new LatLng(-33.682247, 151.383362));
                    // Submit the query to the autocomplete API and retrieve a PendingResult that will
                    // contain the results when the query completes.
                    PendingResult<AutocompletePredictionBuffer> results =
                            Places.GeoDataApi
                                    .getAutocompletePredictions(mGoogleApiClient, query,
                                            BOUNDS_GREATER_SYDNEY, null);

                    // This method should have been called off the main UI thread. Block and wait for at most 60s
                    // for a result from the API.
                    AutocompletePredictionBuffer autocompletePredictions = results
                            .await(60, TimeUnit.SECONDS);

                    // Confirm that the query completed successfully, otherwise return null
                    final com.google.android.gms.common.api.Status status = autocompletePredictions.getStatus();
                    if (!status.isSuccess()) {
                        return "";
                    }
                    Log.e("Dwellbird", "Received " + autocompletePredictions.getCount());

                    Iterator<AutocompletePrediction> iterator = autocompletePredictions.iterator();
                    while (iterator.hasNext()) {
                        AutocompletePrediction prediction = iterator.next();
                        Log.e("Dwellbird", "place_id" + prediction.getPlaceId() + " place desc:" + prediction.getDescription());
                        searchResults.add(new Item(prediction.getDescription(), "",
                                "" + brand, prediction.getPlaceId()));
                    }

                }
                return "";
            }

            @Override
            protected void onPostExecute(String result) {
                super.onPostExecute(result);
                marker_progress.setVisibility(View.GONE);
                searchAdapter.notifyDataSetChanged();
                searchResultListView.setVisibility(View.VISIBLE);
            }

            @Override
            protected void onCancelled() {
                marker_progress.setVisibility(View.GONE);
            }

        };
        mAsyncTask.execute();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }
}
