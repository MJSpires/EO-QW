package com.example.eo;

import android.app.Activity;
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
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

//IBM Bluemix
import com.ibm.mobilefirstplatform.clientsdk.android.core.api.BMSClient;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPush;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPushException;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPushResponseListener;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPushNotificationListener;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPSimplePushNotification;

//mqtt
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;


public class MainActivity extends Activity {

    // IBM mobile service variables
    private TextView label_IBM = null;
    private MFPPush push = null;
    private MFPPushNotificationListener notificationListener = null;

    //mqtt
    private MqttHandler myHandler;
    private boolean connectedMqtt = false;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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
        myHandler = MqttHandler.getInstance(getApplicationContext());
        myHandler.connect();
        connectedMqtt = true;

        // Define a listener that responds to location updates
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.
                makeUseOfNewLocation(location);
                if (connectedMqtt) {
                    myHandler.publish(latitude, longitude, speed);
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
        locationManager.requestLocationUpdates(prov, 2000, 0, locationListener);
        gc = new Geocoder(this);

        // initialize mobile client
        BMSClient client = BMSClient.getInstance();
        try {
            client.initialize(getApplicationContext(), "http://eo-qw-iot.mybluemix.net"
                    , "c8cb12e4-b959-4234-ad67-4be6ca372e4f");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        //Initialize client Push SDK for Java
        push = MFPPush.getInstance();
        push.initialize(getApplicationContext());


        //Register Android devices

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

        //Handles the notification when it arrives

        notificationListener = new MFPPushNotificationListener() {
            @Override
            public void onReceive (final MFPSimplePushNotification message){
                // Handle Push Notification
            }
        };


        final Activity activity = this;


        // Register the listener with the Location Manager to receive location updates
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
        // myHandler.publish(0, 0, 0);
        //updateTextView("Supposedly published");

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
                connectedMqtt = false;
            }

            @Override
            public void onFailure(IMqttToken iMqttToken, Throwable throwable) {
            }
        });
    }

    void showNotification(Activity activity, MFPSimplePushNotification message) {
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

    /*
    void unregisterDevice() {
        push.unregisterDevice(new MFPPushResponseListener<String>() {
            @Override
            public void onSuccess(String s) {
                updateTextView("Device is successfully unregistered. Success response is: " + s);
            }

            @Override
            public void onFailure(MFPPushException e) {
                updateTextView("Device unregistration failure. Failure response is: " + e);
            }
        });
    } */

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

    public void makeUseOfNewLocation(Location location) {

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


        System.out.println(location.hasSpeed());
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        //double speed = location.getSpeed();
        float acc = location.getAccuracy();
        count++;

        this.label_latitude.setText("Latitude: " + latitude);
        this.label_longitude.setText("Longitude: " + longitude);
        if (prev != null)
            this.label_speed.setText("Speed: " + /*location.getSpeed());// + " " +*/ getSpeed(location, prev));
        this.label_accuracy.setText("Accuracy: " + acc);
        this.label_count.setText("Count: " + count);

        prev = location;
    }


    public double getSpeed(Location loc_cur, Location loc_prev) {
        long time = loc_cur.getElapsedRealtimeNanos() - loc_prev.getElapsedRealtimeNanos();
        return loc_cur.distanceTo(loc_prev) / time * 1000000000;

    }
}
