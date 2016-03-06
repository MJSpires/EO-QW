package com.example.eo;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.app.FragmentTransaction;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.ibm.mobilefirstplatform.clientsdk.android.core.api.BMSClient;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPush;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPushException;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPushNotificationListener;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPushResponseListener;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPSimplePushNotification;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;


//IBM Bluemix
//mqtt


public class MainActivity extends FragmentActivity implements OnMapReadyCallback {

    // IBM mobile service variables
    private TextView label_IBM = null;
    private MFPPush push;
    private MFPPushNotificationListener notificationListener;

    //mqtt
    private MqttHandler myMqttHandler;

    // may or may not be implemented
    private List<String> allTags;
    private List<String> subscribedTags;
    // app location variables and labels
    Location prev;
    TextView label_latitude;
    TextView label_longitude;
    TextView label_speed;
    TextView label_accuracy;
    TextView label_count;
    long count = 0;

    TextView addr;

    Geocoder gc;

    double latitude;
    double longitude;
    double speed;
    float acc;

    private GoogleMap mMap;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        MapFragment mMapFragment = MapFragment.newInstance();
//        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
//        fragmentTransaction.add(R.id.frag_container, mMapFragment);
//        fragmentTransaction.commit();
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // label_IBM = (TextView) findViewById(R.id.label_IBM);
        this.label_latitude = (TextView) super.findViewById(R.id.label_latitude);
        this.label_longitude = (TextView) super.findViewById(R.id.label_longitude);
        this.label_speed = (TextView) super.findViewById(R.id.label_speed);
        this.label_accuracy = (TextView) super.findViewById(R.id.label_accuracy);
        this.label_count = (TextView) super.findViewById(R.id.label_count);
        this.addr = (TextView) super.findViewById(R.id.list_addr);

        Criteria criteria = new Criteria();
        //Use FINE or COARSE (or NO_REQUIREMENT) here
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        //criteria.setPowerRequirement(Criteria.POWER_LOW);
        criteria.setAltitudeRequired(true);
        criteria.setSpeedRequired(true);
        //criteria.setCostAllowed(false);
        criteria.setBearingRequired(true);

        //API level 9 and up
        criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
        criteria.setVerticalAccuracy(Criteria.ACCURACY_HIGH);
        criteria.setBearingAccuracy(Criteria.ACCURACY_LOW);
        criteria.setSpeedAccuracy(Criteria.ACCURACY_HIGH);


        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        String prov = locationManager.getBestProvider(criteria, true);

        // set up MQTT for sending location data
        myMqttHandler = MqttHandler.getInstance(getApplicationContext());
        myMqttHandler.connect();

        // Define a listener that responds to location updates
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.
                makeUseOfNewLocation(location);

                if (myMqttHandler.getConnectionStatus()) {
                    myMqttHandler.publish(latitude, longitude, speed);
                    Log.d("Main activity", "Just published");
                } 
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }

        };

        // Register the listener with the Location Manager to receive location updates
        locationManager.requestLocationUpdates(prov, 500, 0, locationListener);
        gc = new Geocoder(this);

        // initialize mobile client
        BMSClient client = BMSClient.getInstance();
        try {
            client.initialize(getApplicationContext(), "http://eo-qw-iot.mybluemix.net"
                    , "c8cb12e4-b959-4234-ad67-4be6ca372e4f");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        // Initialize Push client
        MFPPush.getInstance().initialize(getApplicationContext());

        //Initialize client Push SDK for Java
        push = MFPPush.getInstance();

        //Register Android device
        push.register(new MFPPushResponseListener<String>() {
            @Override
            public void onSuccess(String deviceId) {
                updateTextView("Device is registered with Push Service.");
               // displayTags();
            }

            @Override
            public void onFailure(MFPPushException ex) {
                updateTextView("Error registering with Push Service...\n"
                        + "Push notifications will not be received.");
            }
        });
        final FragmentActivity activity = this;
        //Handles the notification when it arrives

        notificationListener = new MFPPushNotificationListener() {
            @Override
            public void onReceive (final MFPSimplePushNotification message){
                // Handle Push Notification
                showNotification(activity, message);
            }
        };

        // Register the listener with the Location Manager to receive location updates
        //locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);

    }

    @Override
    protected void onStart() {
        super.onStart();
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (push != null) {
            push.listen(notificationListener);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        MqttHandler.getInstance(this).disconnect(new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken iMqttToken) {
            }

            @Override
            public void onFailure(IMqttToken iMqttToken, Throwable throwable) {
            }
        });
    }

    void showNotification(FragmentActivity activity, MFPSimplePushNotification message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage("Notification Received : " + message.toString());
        builder.setCancelable(true);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {

            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    void displayTags() {
        push.getTags(new MFPPushResponseListener<List<String>>() {
            @Override
            public void onSuccess(List<String> tags) {
                updateTextView("Retrieved Tags : " + tags);
                allTags = tags;
                displayTagSubscriptions();
            }

            @Override
            public void onFailure(MFPPushException ex) {
                updateTextView("Error getting tags..." + ex.getMessage());
            }
        });
    }


    void unregisterDevice() {
        push.unregister(new MFPPushResponseListener<String>() {
            @Override
            public void onSuccess(String s) {
                updateTextView("Device is successfully unregistered. Success response is: " + s);
            }

            @Override
            public void onFailure(MFPPushException e) {
                updateTextView("Device unregistration failure. Failure response is: " + e);
            }
        });
    }

    void unsubscribeFromTags(final String tag) {
        push.unsubscribe(tag, new MFPPushResponseListener<String>() {

            @Override
            public void onSuccess(String s) {
                updateTextView("Unsubscribing from tag");
                updateTextView("Successfully unsubscribed from tag . " + tag);
            }

            @Override
            public void onFailure(MFPPushException e) {
                updateTextView("Error while unsubscribing from tags. " + e.getMessage());
            }

        });
    }

    void displayTagSubscriptions() {
        push.getSubscriptions(new MFPPushResponseListener<List<String>>() {
            @Override
            public void onSuccess(List<String> tags) {
                updateTextView("Retrieved subscriptions : " + tags);
                System.out.println("Subscribed tags are: " + tags);
                subscribedTags = tags;
                subscribeToTag();
            }

            @Override
            public void onFailure(MFPPushException ex) {
                updateTextView("Error getting subscriptions.. "
                        + ex.getMessage());
            }
        });
    }

    void subscribeToTag() {
        System.out.println("subscribedTags: " + subscribedTags + "size is: " + subscribedTags.size());
        System.out.println("allTags: " + allTags + "Size is: " + allTags.size());

        if ((allTags.size() != 0)) {
            push.subscribe(allTags.get(0),
                    new MFPPushResponseListener<String>() {
                        @Override
                        public void onFailure(MFPPushException ex) {
                            updateTextView("Error subscribing to Tag1.."
                                    + ex.getMessage());
                        }

                        @Override
                        public void onSuccess(String arg0) {
                            updateTextView("Succesfully Subscribed to: " + arg0);
                            unsubscribeFromTags(arg0);
                        }
                    });
        } else {
            updateTextView("Not subscribing to any more tags.");
        }

    }

    public void updateTextView(final String str) {
        return;
        /*
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                label_IBM.append(str);
                label_IBM.append("\n");
            }
        }); */
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (push != null) {
            push.hold();
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    // This method changes the private variable values for latitude, longitude, speed, and acc
    public void makeUseOfNewLocation(Location location) {

        LatLng sydney = new LatLng(location.getLatitude(), location.getLongitude());
        mMap.clear();
        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
        mMap.moveCamera(CameraUpdateFactory.zoomTo(15));

        try {
            List<Address> list = gc.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            this.addr.setText("Address:\n");
            for (int i = 0; i < list.get(0).getMaxAddressLineIndex(); i++) {
                this.addr.setText(this.addr.getText() + "\n" + list.get(0).getAddressLine(i));
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        latitude = location.getLatitude();
        longitude = location.getLongitude();
        //double speed = location.getSpeed();
        if (prev != null) {
            speed = getSpeed(location, prev);
        }
        acc = location.getAccuracy();
        count++;

        this.label_latitude.setText("Latitude: " + latitude);
        this.label_longitude.setText("Longitude: " + longitude);
        //if (prev != null)
            this.label_speed.setText("Speed: " + /*location.getSpeed());// + " " +*/ location.getSpeed());//getSpeed(location, prev));
        this.label_accuracy.setText("Accuracy: " + acc);
        this.label_count.setText("Count: " + count);

        prev = location;
    }


    public double getSpeed(Location loc_cur, Location loc_prev) {
        long time = loc_cur.getElapsedRealtimeNanos() - loc_prev.getElapsedRealtimeNanos();
        return loc_cur.distanceTo(loc_prev) / time * 1000000000;

    }



    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Add a marker in Sydney and move the camera
        LatLng sydney = new LatLng(-34, 151);
        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
    }
}
