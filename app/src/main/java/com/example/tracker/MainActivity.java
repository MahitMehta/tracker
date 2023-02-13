package com.example.tracker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private static final long MIN_TIME_BW_UPDATES_MS = 1000;
    private static final float MIN_DISTANCE_CHANGE_FOR_UPDATES = 0;

    private static final String DISTANCE_TRAVELED_KEY = "distanceTraveled";
    private static final String LAST_SAVED_TIME_KEY = "lastSavedTime";
    private static final String APP_START_TIME_KEY = "appStartTime";
    private static final String CHANGES_KEY = "changes";

    private static final int DISTANCE_COUNTER_DELAY = 500; // ms

    private LocationManager mLocationManager;
    private Geocoder mGeocoder;

    private TextView currentLat, currentLon, currentAddress, distanceTraveledTV, favoriteLocation;

    private List<Address> addresses;
    private Location previousLocation;

    private float distanceTraveled;
    private long appStartTime;
    private ArrayList<LocationChange> changes;
    private ListView changesLV;
    private ChangesAdapter adapter;

    private long lastSavedTime;

    private static class LocationChange {
        public @Nullable Location previousLocation;
        public Location currentLocation;
        public float distanceTraveled;
        public long timeElapsed;

        public LocationChange(@Nullable  Location previousLocation, Location currentLocation, float distanceTraveled, long timeElapsed) {
            this.previousLocation = previousLocation;
            this.currentLocation = currentLocation;
            this.distanceTraveled = distanceTraveled;
            this.timeElapsed = timeElapsed;
        }
    }

    public class ChangesAdapter extends ArrayAdapter<LocationChange> {
        int xmlResource;
        List<LocationChange> changes;
        Context ctx;

        public ChangesAdapter(@NonNull Context ctx, int resource, @NonNull List<LocationChange> changes) {
            super(ctx, resource, changes);
            this.xmlResource = resource;
            this.changes = changes;
            this.ctx = ctx;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            LayoutInflater layoutInflater = (LayoutInflater) ctx.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
            View adapterLayout = layoutInflater.inflate(xmlResource, null);

            LocationChange change = changes.get(position);
            TextView distanceTraveledTV = adapterLayout.findViewById(R.id.distanceTraveled);
            TextView elapsedTime = adapterLayout.findViewById(R.id.elapsedTime);
            distanceTraveledTV.setText(String.format("Traveled: %s", change.distanceTraveled));
            elapsedTime.setText((change.timeElapsed / 1000d) + "s");
            return adapterLayout;
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState != null) restoreAppState(savedInstanceState);
        else {
            distanceTraveled = 0;
            appStartTime = SystemClock.elapsedRealtime();
            changes = new ArrayList<>();
        }

        currentLat = findViewById(R.id.currentLat);
        currentLon = findViewById(R.id.currentLon);
        currentAddress = findViewById(R.id.currentAddress);
        distanceTraveledTV = findViewById(R.id.distanceTraveled);
        changesLV = findViewById(R.id.changes);
        favoriteLocation = findViewById(R.id.favoriteLocation);

        Button resetDistance = findViewById(R.id.resetDistance);
        resetDistance.setOnClickListener((View v) -> {
            distanceTraveled = 0;
            setDistanceTraveled();
            adapter.clear();
        });

        mLocationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
        mGeocoder = new Geocoder(this, Locale.US);

        mLocationManager.getCurrentLocation(
                LocationManager.GPS_PROVIDER,
                null,
                this.getMainExecutor(),
                this::setLocation);


        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,MIN_TIME_BW_UPDATES_MS,MIN_DISTANCE_CHANGE_FOR_UPDATES, this);

        setDistanceTraveled();

        adapter = new ChangesAdapter(this, R.layout.change, changes);
        changesLV.setAdapter(adapter);



        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                favoriteLocation.post(() -> {
                    LocationChange fav = getFavoriteLocation();
                    if (fav != null) {
                        favoriteLocation.setText((fav.timeElapsed / 1000d) + "s");

                    }
                });
            }
        }, 0, 1000);
        }

    @Override
    protected void onPause() {
        super.onPause();
        mLocationManager.removeUpdates(this);
    }

    public void restoreAppState(@NonNull Bundle savedInstanceState) {
        if (savedInstanceState.getSerializable(CHANGES_KEY, ArrayList.class) != null) {
            changes = savedInstanceState.getSerializable(CHANGES_KEY, ArrayList.class);
        }

        if (savedInstanceState.getFloat(DISTANCE_TRAVELED_KEY) != 0.0) {
            distanceTraveled = savedInstanceState.getFloat(DISTANCE_TRAVELED_KEY);
        }

        if (savedInstanceState.getLong(APP_START_TIME_KEY) != 0L) {
            appStartTime = savedInstanceState.getLong(APP_START_TIME_KEY);
        }

        if (savedInstanceState.getLong(LAST_SAVED_TIME_KEY) != 0L) {
            lastSavedTime = savedInstanceState.getLong(LAST_SAVED_TIME_KEY);
        }
    }

    private LocationChange getFavoriteLocation() {
        if (previousLocation == null || lastSavedTime == 0) return null;

        long currentDelta = SystemClock.elapsedRealtime() - lastSavedTime;
        LocationChange savedDelta = adapter.getCount() > 0 ? adapter.getItem(0) : null;
        for (int i = 1; i < adapter.getCount(); i++) {
            if (savedDelta.timeElapsed < adapter.getItem(i).timeElapsed) {
                savedDelta = adapter.getItem(i);
            }
        }
        if (savedDelta != null && savedDelta.timeElapsed >= currentDelta) return savedDelta;

        return new LocationChange(
            previousLocation,
            null,
                -1,
                currentDelta
        );
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putLong(LAST_SAVED_TIME_KEY,lastSavedTime);
        outState.putFloat(DISTANCE_TRAVELED_KEY, distanceTraveled);
        outState.putLong(APP_START_TIME_KEY, appStartTime);
        outState.putSerializable(CHANGES_KEY, new ArrayList<LocationChange>(adapter.changes));

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    public void setDistanceTraveled() {
        distanceTraveledTV.setText(String.format("Distance Traveled: %s meters", distanceTraveled));
    }

    public void setAddress() {
        Address address = addresses.get(0);
        currentAddress.post(() -> {
            currentAddress.setText(address.getAddressLine(0));
        });
    }

    public void setLocation(Location location) {
        if (distanceTraveled != 0.0 && SystemClock.elapsedRealtime() - appStartTime < DISTANCE_COUNTER_DELAY) return;

        mGeocoder.getFromLocation(
                location.getLatitude(),
                location.getLongitude(),
                1,
                (List<Address> retrievedAddresses) -> {
                    addresses = retrievedAddresses;
                    if (addresses.size() > 0) setAddress();
                }
        );

        currentLat.setText(String.format("Lat: %s", location.getLatitude()));
        currentLon.setText(String.format("Lat: %s", location.getLongitude()));

        if (previousLocation != null && previousLocation.distanceTo(location) != 0.0) {
            distanceTraveled += previousLocation.distanceTo(location);

            adapter.insert(new LocationChange(
                    previousLocation,
                    location,
                    previousLocation.distanceTo(location),
                    SystemClock.elapsedRealtime() - lastSavedTime
            ), 0);
            if (adapter.getCount() >= 5 ) adapter.remove(adapter.getItem(4));
            setDistanceTraveled();

            lastSavedTime = SystemClock.elapsedRealtime();
            previousLocation = location;
        } else if (previousLocation == null) {
            lastSavedTime = SystemClock.elapsedRealtime();
            previousLocation = location;
        }


        if (adapter.getCount() != 0) {
            LocationChange curr = adapter.getItem(adapter.getCount() - 1);
            Log.d("Change: ", + curr.distanceTraveled + " meters, Time Elapsed: " + curr.timeElapsed + " ms" + " size: " + adapter.getCount());
        }

    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        setLocation(location);
    }
}