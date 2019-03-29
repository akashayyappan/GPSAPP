package com.akashayyappan.gpsapp;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private InputStream inputStream;
    private OutputStream outputStream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Context context = getApplicationContext();

        //Check Sensors
        checkBT();
        checkGPS(context);
    }

    private void checkBT() {
        if (bluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "Device doesn't Support Bluetooth", Toast.LENGTH_SHORT).show();
        }
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(getApplicationContext(), "Turn ON Bluetooth!", Toast.LENGTH_SHORT).show();
            Intent enableAdapter = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableAdapter, 0);
        }
    }

    private void checkGPS(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }
        turnGPSOn();
    }

    private void turnGPSOn() {
        String provider = Settings.Secure.getString(getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);

        if (!provider.contains("gps")) { //if gps is disabled
            final Intent poke = new Intent();
            poke.setClassName("com.android.settings", "com.android.settings.widget.SettingsAppWidgetProvider");
            poke.addCategory(Intent.CATEGORY_ALTERNATIVE);
            poke.setData(Uri.parse("3"));
            sendBroadcast(poke);
        }
    }

    public void onTestClick(View view) {
        final TextView latitude = findViewById(R.id.latitude);
        final TextView longitude = findViewById(R.id.longitude);
        latitude.setText("WAIT FOR GPS...");
        longitude.setText("");
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(final Location location) {
                latitude.setText("Latitude:\n" + location.getLatitude());
                longitude.setText("Longitude:\n" + location.getLongitude());
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }
        assert locationManager != null;
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
    }

    public void onConnect(View view){
        new ConnectBT().execute();
    }

    private void init() throws IOException {
        BluetoothDevice device = null;
        if (bluetoothAdapter != null) {
            if (bluetoothAdapter.isEnabled()) {
                Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

                if(bondedDevices.size() > 0) {
                    for (BluetoothDevice iterator : bondedDevices) {
                        if (iterator.getName().equals("HC-05")) {
                            device = iterator;
                            break;
                        }
                    }
                    ParcelUuid[] uuids = device.getUuids();
                    BluetoothSocket socket = device.createRfcommSocketToServiceRecord(uuids[0].getUuid());
                    socket.connect();
                    outputStream = socket.getOutputStream();
                    inputStream = socket.getInputStream();
                }

                Log.e("error", "No appropriate paired devices.");
            } else {
                Log.e("error", "Bluetooth is disabled.");
            }
        }
    }

    private class ConnectBT extends AsyncTask<Void, Void, Void> {

        ProgressDialog progress;

        @Override
        protected void onPreExecute() {
            progress = ProgressDialog.show(MainActivity.this, "Connecting...", "Please Wait!");
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                init();
            } catch (IOException e) {
                Log.e("error","Cannot connect");
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            progress.dismiss();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final int BUFFER_SIZE = 1024;
                    final TextView terminal = findViewById(R.id.terminal);
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytes = 0;
                    int b = BUFFER_SIZE;

                    while (true) {
                        try {
                            bytes = inputStream.read(buffer, bytes, BUFFER_SIZE - bytes);
                            terminal.setText(String.valueOf(bytes));
                        } catch (IOException e) {
                            Log.e("error","Cannot Read");
                            e.printStackTrace();
                        }
                    }
                }
            }).start();
        }
    }

    public void write(String s) throws IOException {
        outputStream.write(s.getBytes());
    }

    public void onSendData(View view) {

        EditText dataText = findViewById(R.id.data);
        try {
            write(String.valueOf(dataText.getText()));
            dataText.setText("");
        } catch (IOException e) {
            Log.e("error","send error");
            e.printStackTrace();
        }
        Toast.makeText(this, "Msg Sent...", Toast.LENGTH_SHORT).show();
    }
}
