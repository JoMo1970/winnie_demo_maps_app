package com.winnie.demo;

import android.*;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.net.ssl.HttpsURLConnection;

public class WinnieMapsActivity extends FragmentActivity implements LocationListener, OnMapReadyCallback, ActivityCompat.OnRequestPermissionsResultCallback {

    private GoogleMap mMap;
    private LocationManager locationManager;
    private int HAS_PERMISSIONS = 1;
    private Location currentLocation;
    private JSONObject fourSquareJsonResponse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_winnie_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        /*//init a temporary location object -- debug
        currentLocation = new Location("test location");
        currentLocation.setLatitude(32.8010232);
        currentLocation.setLongitude(-97.4297449);*/
    }


    //this interface listener will render when the map has been rendered
    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.i("onMapReady", "Google Map has rendered. Getting content");
        mMap = googleMap;

        //init location manager
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

        //check if gps is enabled
        if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            //init permissions check
            initLocationPermissionsCheck();
        }
        else {
            //prompt user if they would like to enable gps
            initGpsPromptDialog();
        }
    }

    //this function will init a dialog asking the user if they would like to enable gps
    private void initGpsPromptDialog() {
        final AlertDialog.Builder gpsDialogBuilder = new AlertDialog.Builder(this);
        gpsDialogBuilder.setMessage("Hang on there tough guy...It looks like your GPS is taking a siesta. Would you like to wake it up?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //render the gps settings view
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        System.exit(0);
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //close the dialog and kill the app
                        dialog.cancel();
                        System.exit(0);
                    }
                });
        final AlertDialog gpsDialog = gpsDialogBuilder.create();
        gpsDialog.show();
    }

    //this function will init a prompt that explains permissions
    private void initPermissionsErrorDialog() {
        final AlertDialog.Builder permissionsBuilder = new AlertDialog.Builder(this);
        permissionsBuilder.setMessage("We understand. Unfortunately, we have to have GPS permissions to run. Please come back later if you change your mind.")
                .setCancelable(false)
                .setNegativeButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //close the dialog and kill the app
                        dialog.cancel();
                        System.exit(0);
                    }
                });
        final AlertDialog permissionsErroDialog = permissionsBuilder.create();
        permissionsErroDialog.show();
    }

    //this function will check if permissions are required. If so, will prompt the user. If not, will proceed with location lookup
    @SuppressLint("LongLogTag")
    private void initLocationPermissionsCheck() {
        if(Build.VERSION.SDK_INT>=23 && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //prompt user
            Log.i("initLocationPermissionsCheck()", "Found newer version of Android that requires permissions. Requesting permissions");
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, HAS_PERMISSIONS);
        }
        else {
            //proceed with the location lookup
            Log.i("initLocationPermissionsCheck()", "Permissions not required. Proceeding with location lookup");
            initLocationLookup();
        }
    }

    //this function will render the location lookup and proceed into the google map population
    private void initLocationLookup() {

        try {
            currentLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Log.i("initLocationLookup()", "Location obtained: \n Latitude: " + currentLocation.getLatitude() + "\nLongitude: " +  currentLocation.getLongitude() + ". Launching FourSquare Lookup Async Task");
            //perform async task
            new FourSquareLookup().execute();
        }
        catch (SecurityException se) {
            Log.e("initLocationLookup()", se.toString());
        }
        catch (Exception ex) {
            Log.e("initLocationLookup()", ex.toString());
        }
    }

    //this function will render content onto the google map
    @SuppressLint("LongLogTag")
    private void renderGoogleMapContent() {
        Log.i("renderGoogleMapContent()", "Writing out map content");

        // add the current user centered marker
        LatLng userLatLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
        mMap.addMarker(new MarkerOptions().position(userLatLng ).title("YOU").icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_media_play)));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(userLatLng));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(12), 2000, null);

        try {
            //get the response object from the newly created json object
            JSONObject response = fourSquareJsonResponse.getJSONObject("response");

            //get the list of groups and items from the response and cycle through them
            JSONArray items = (JSONArray)((JSONObject)response.getJSONArray("groups").get(0)).getJSONArray("items");
            for(int i=0;i<items.length();i++) {
                //parse the item object
                JSONObject item = (JSONObject)items.get(i);

                //grab the venue and location child object
                JSONObject venueObject = (JSONObject)item.getJSONObject("venue");
                JSONObject location = venueObject.getJSONObject("location");

                //compile a description string
                String locationDescription = location.getString("city") + "," + location.getString("state");

                //using the location data, populate a map marker
                LatLng locationMarker = new LatLng(Double.parseDouble(location.getString("lat")), Double.parseDouble(location.getString("lng")));
                mMap.addMarker(new MarkerOptions().position(locationMarker).title(venueObject.getString("name")).snippet(locationDescription));
            }

        }
        catch (JSONException je) {
            je.printStackTrace();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }


    }

    //this function will render when the user makes a selection on if they grant permissions or not
    @Override
    @SuppressLint("LongLogTag")
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        Log.i("onRequestPermissionsResult()", "User permissions result captured. Checking what user selected");
        if(grantResults[0]==0 && grantResults[1]==0) {
            Log.i("onRequestPermissionsResult()", "User has provided permissions. Performing location lookup");
            initLocationLookup();
        }
        else {
            Log.i("onRequestPermissionsResult()", "User had denied permissions. Prompting user");
            initPermissionsErrorDialog();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.i("onLocationChanged()", "Location has changed");
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.i("onStatusChanged()", provider + " status has changed");
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.i("onProviderEnabled()", provider + " has been enabled");
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.i("onProviderDisabled()", provider + " has been disabled");
    }

    //init asynclistener to run a gps look NOT on the UI thread
    private class FourSquareLookup extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {

            //init a request variables
            String version = new SimpleDateFormat("yyyyMMdd").format(new Date());
            String clientId = "KB45V00TQB1PCVWOWRWQP3VPYAAB15BEG5VCZVGA3LADGA4B";
            String clientSecret = "J3R4VAADPG4N4IATDCA2NVOU1Q0FQ5LQ0ZS3TBNTFM2DVKFT";
            String foursquareEndpoint = "https://api.foursquare.com/v2/venues/explore?ll=" + currentLocation.getLatitude() + "," +
                    currentLocation.getLongitude() + "&client_id=" + clientId + "&client_secret=" + clientSecret + "&v=" + version;

            String result = null;
            int resCode;
            InputStream in;
            try {
                URL url = new URL(foursquareEndpoint);
                URLConnection urlConn = url.openConnection();

                HttpsURLConnection httpsConn = (HttpsURLConnection) urlConn;
                httpsConn.setAllowUserInteraction(false);
                httpsConn.setInstanceFollowRedirects(true);
                httpsConn.setRequestMethod("GET");
                httpsConn.connect();
                resCode = httpsConn.getResponseCode();

                if (resCode == HttpURLConnection.HTTP_OK) {
                    in = httpsConn.getInputStream();

                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            in, "iso-8859-1"), 8);
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    in.close();
                    result = sb.toString();
                    Log.i("FourSquareLookup", "FourSquare Api Response: " + result);
                    //cast the json string to the global json object
                    fourSquareJsonResponse = new JSONObject(result);
                } else {
                    //error += resCode;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            catch (JSONException je) {
                je.printStackTrace();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void res) {
            Log.i("FourSquareLookup", "FourSquare Api has completed. Returning thread back to UI");
            renderGoogleMapContent();
        }

        @Override
        protected  void onPreExecute() {
            Log.i("initGPSLookup", "Launching initGPSLookup thread");
        }
    }
 }
