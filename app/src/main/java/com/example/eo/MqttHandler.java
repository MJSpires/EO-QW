package com.example.eo;
//https://developer.ibm.com/recipes/tutorials/android-wear-iot-bluemix/

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.example.eo.utils.ActionListener;
import com.example.eo.utils.Constants;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;

import java.sql.BatchUpdateException;
import java.util.Arrays;

public class MqttHandler implements MqttCallback {

    private final static String TAG = MqttHandler.class.getName();
    private static MqttHandler instance;
    private MqttAndroidClient mqttClient;
    Context context;


    private static String ORG = "";
    private static String DEVICE_TYPE = "";
    private static String DEVICE_ID = Build.SERIAL;
    private static String TOKEN = "";
    private static String TOPIC = "";


    private MqttHandler(Context context) {
        this.context = context;
    }

    static void setToken(String tok){
        TOKEN = tok;
    }

    public static MqttHandler getInstance(Context context) {
        if (instance == null) {
            instance = new MqttHandler(context);
        }
        return instance;
    }

    public void connect() {
        Log.d(TAG, ".connect() entered");

        if (!isConnected()) {
            String iotPort = "1883";
            String iotHost = ORG+".messaging.internetofthings.ibmcloud.com";
            String iotClientId = "d:"+ORG+":"+DEVICE_TYPE+":"+DEVICE_ID;

            String connectionUri = "tcp://" + iotHost + ":" + iotPort;

            if (mqttClient != null) {
                mqttClient.unregisterResources();
                mqttClient = null;
            }

            // create ActionListener to handle connection results
            ActionListener listener = new ActionListener(context, Constants.ActionStateStatus.CONNECTING);

            mqttClient = new MqttAndroidClient(context, connectionUri, iotClientId);
            mqttClient.setCallback(this);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setUserName("use-token-auth");
            options.setPassword(TOKEN.toCharArray());

            try {
                mqttClient.connect(options, context, listener);
            } catch (MqttException e) {
                Log.e(TAG, "Exception caught while attempting to connect to server", e.getCause());
            }
        }
    }

    public void disconnect(IMqttActionListener listener) {
        if (isConnected()) {
            try {
                mqttClient.disconnect(context, listener);
                mqttClient = null;
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }

    public void publish(double latitude, double longitude, double speed, String roadName, float bearing, float[] accel) {
        if (isConnected()) {

            Long tsLong = System.currentTimeMillis()/1000;
            String ts = tsLong.toString();
            String msg = "{\"timestamp\":" + ts + ",\"latitude\":"+latitude+"," +
                    "\"longitude\":"+longitude+ ", \"speed\":"+speed+", \"roadName\":"+roadName+"}" +
                    "\"bearing\":"+bearing+ ", \"accel\":" + Arrays.toString(accel)+"}";
            Log.d(TAG, ".publish() - Publishing " + msg);
            MqttMessage mqttMsg = new MqttMessage(msg.getBytes());
            mqttMsg.setRetained(false);
            mqttMsg.setQos(0);
            try {
                mqttClient.publish(TOPIC, mqttMsg);
            } catch (Exception e) {
                Log.d(TAG, "Error publishing");
                Log.d(TAG, e.toString());
            }
        }
    }

    /**
     * Handle loss of connection from the MQTT server.
     * @param throwable
     */
    @Override
    public void connectionLost(Throwable throwable) {
        Log.e(TAG, ".connectionLost() entered");

        if (throwable != null) {
            throwable.printStackTrace();
        }

    }

    /**
     * Process incoming messages to the MQTT client.
     *
     * @param topic       The topic the message was received on.
     * @param mqttMessage The message that was received
     * @throws Exception  Exception that is thrown if the message is to be rejected.
     */
    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
        Log.d(TAG, ".messageArrived() entered");


        String payload = new String(mqttMessage.getPayload());
        Log.d(TAG, ".messageArrived - Message received on topic " + topic
                + ": message is " + payload);
        // TODO: Process message
        try {
            Log.d(TAG, "in messageArrived fix this");
            throw new JSONException("test");
        } catch (JSONException e) {
            Log.e(TAG, ".messageArrived() - Exception caught while steering a message", e.getCause());
            e.printStackTrace();
        }
    }

    /**
     * Handle notification that message delivery completed successfully.
     *
     * @param iMqttDeliveryToken The token corresponding to the message which was delivered.
     */
    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        Log.d(TAG, ".deliveryComplete() entered");
    }

    /**
     * Checks if the MQTT client has an active connection
     *
     * @return True if client is connected, false if not.
     */
    private boolean isConnected() {
        Log.d(TAG, ".isMqttConnected() entered");
        boolean connected = false;
        try {
            if ((mqttClient != null) && (mqttClient.isConnected())) {
                connected = true;
            }
        } catch (Exception e) {
            // swallowing the exception as it means the client is not connected
        }
        Log.d(TAG, ".isMqttConnected() - returning " + connected);
        return connected;
    }

    public boolean getConnectionStatus() {
        return isConnected();
    }

}