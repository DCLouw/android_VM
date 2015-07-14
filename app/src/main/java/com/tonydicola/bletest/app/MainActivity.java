package com.tonydicola.bletest.app;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends Activity {

    // UUIDs for UAT service and associated characteristics.
    public static UUID UART_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID TX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID RX_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    //public static UUID RX2_UUID = UUID.fromString("6E400004-B5A3-F393-E0A9-E50E24DCCA9E");
    // UUID for the BTLE client characteristic which is necessary for notifications.
    public static UUID CLIENT_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // UI elements
    private TextView messages;

    private TextView txtCadence;
    //private TextView txtSpeed;
    private TextView txtPower;
    private ProgressBar prgPower;

    // BTLE state
    private BluetoothAdapter adapter;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic tx;
    private BluetoothGattCharacteristic rx;
    //private BluetoothGattCharacteristic rx2;



    // Main BTLE device callback where much of the logic occurs.
    private BluetoothGattCallback callback = new BluetoothGattCallback() {
        // Called whenever the device connection state changes, i.e. from disconnected to connected.
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                writeLine("Connected!");
                // Discover services.
                if (!gatt.discoverServices()) {
                    writeLine("Failed to start discovering services!");
                }
            }
            else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                writeLine("Disconnected!");
                messages.clearComposingText();

            }
            else {
                writeLine("Connection state changed.  New state: " + newState);
            }
        }

        // Called when services have been discovered on the remote device.
        // It seems to be necessary to wait for this discovery to occur before
        // manipulating any services or characteristics.
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                writeLine("Service discovery completed!");
            }
            else {
                writeLine("Service discovery failed with status: " + status);
            }
            // Save reference to each characteristic.
            tx = gatt.getService(UART_UUID).getCharacteristic(TX_UUID);
            rx = gatt.getService(UART_UUID).getCharacteristic(RX_UUID);
            //rx2 = gatt.getService(UART_UUID).getCharacteristic(RX2_UUID);

            Log.d("this", rx.getUuid().toString()) ;
            Log.d("this", "is the current rx uuid") ;


            // Setup notifications on RX characteristic changes (i.e. data received).


            Log.d("this", "notifications enabled");

            // Next update the RX characteristic's client descriptor to enable notifications.

            BluetoothGattDescriptor desc = rx.getDescriptor(CLIENT_UUID);
            desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(desc);


            //gatt.writeDescriptor(desc2);

            gatt.setCharacteristicNotification(rx, true);
            //gatt.setCharacteristicNotification(rx2, true);

            Log.d("this", rx.getDescriptor(CLIENT_UUID).getUuid().toString());
            //Log.d("this", rx2.getDescriptor(CLIENT_UUID).getUuid().toString());
            Log.d("this", "is the descriptor uuids");

        }

        // Called when a remote characteristic changes (like the RX characteristic).
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic)


        {


            Log.d("this", characteristic.getUuid().toString());
            Log.d("this", "characteristic has just changed");

            super.onCharacteristicChanged(gatt, characteristic);
            //writeLine("Received: " + characteristic.getStringValue(0));



                Log.d("this", "Updates on rx");
                updateDisp(characteristic.getStringValue(0));



        }//onCharacteristicChanged
    };

    // BTLE device scanning callback.
    private LeScanCallback scanCallback = new LeScanCallback() {
        // Called when a device is found.
        @Override
        public void onLeScan(BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
            writeLine("Found sensors: " + bluetoothDevice.getAddress());
            // Check if the device has the UART service.
            if (parseUUIDs(bytes).contains(UART_UUID)) {
                // Found a device, stop the scan.
                adapter.stopLeScan(scanCallback);
                writeLine("Found Velometrics service!");
                // Connect to the device.
                // Control flow will now go to the callback functions when BTLE events occur.
                gatt = bluetoothDevice.connectGatt(getApplicationContext(), false, callback);
            }
        }
    };

    // OnCreate, called once to initialize the activity.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Grab references to UI elements.
        messages = (TextView) findViewById(R.id.messages);


        txtCadence =(TextView) findViewById(R.id.txtCadence);
        //txtSpeed = (TextView) findViewById(R.id.txtSpeed);
        txtPower = (TextView) findViewById(R.id.txtPower);

        prgPower = (ProgressBar) findViewById(R.id.prgPower);

        adapter = BluetoothAdapter.getDefaultAdapter();
    }

    // OnResume, called right before UI is displayed.  Start the BTLE connection.
    @Override
    protected void onResume() {
        super.onResume();
        // Scan for all BTLE devices.
        // The first one with the UART service will be chosen--see the code in the scanCallback.
        Log.d("this", "scanning for sum devices");
        writeLine("Scanning for sensors...");
        adapter.startLeScan(scanCallback);
    }

    // OnStop, called right before the activity loses foreground focus.  Close the BTLE connection.
    @Override
    protected void onStop() {
        super.onStop();
        if (gatt != null) {
            // For better reliability be careful to disconnect and close the connection.
            gatt.disconnect();
            gatt.close();
            gatt = null;
            tx = null;
            rx = null;
        }
    }


    // Write some text to the messages text view.
    // Care is taken to do this on the main UI thread so writeLine can be called
    // from any thread (like the BTLE callback).
    private void writeLine(final CharSequence text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                messages.append(text);
                messages.append("\n");

            }
        });
    }

///Method to handle changes to the UI

    private void updateDisp(final String text)

    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                Log.d("this", "now inside the updateDisplay method");

                String delims = "[ ]+";
                String[] tokens = text.split(delims);

                Log.d("this", "Firts token");

                Log.d("this", tokens[0]);

                Log.d("this", "Second token");

                Log.d("this", tokens[1]);
                txtPower.setText(tokens[0]);
                txtCadence.setText(tokens[1]);

                int prog = (33120-Integer.parseInt(tokens[0]));
                prgPower.setProgress(prog*2);
//txtPower.setText(text);

            }
        });
    }


    // Filtering by custom UUID is broken in Android 4.3 and 4.4, see:
    //   http://stackoverflow.com/questions/18019161/startlescan-with-128-bit-uuids-doesnt-work-on-native-android-ble-implementation?noredirect=1#comment27879874_18019161
    // This is a workaround function from the SO thread to manually parse advertisement data.
    private List<UUID> parseUUIDs(final byte[] advertisedData) {
        List<UUID> uuids = new ArrayList<UUID>();

        int offset = 0;
        while (offset < (advertisedData.length - 2)) {
            int len = advertisedData[offset++];
            if (len == 0)
                break;

            int type = advertisedData[offset++];
            switch (type) {
                case 0x02: // Partial list of 16-bit UUIDs
                case 0x03: // Complete list of 16-bit UUIDs
                    while (len > 1) {
                        int uuid16 = advertisedData[offset++];
                        uuid16 += (advertisedData[offset++] << 8);
                        len -= 2;
                        uuids.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", uuid16)));
                    }
                    break;
                case 0x06:// Partial list of 128-bit UUIDs
                case 0x07:// Complete list of 128-bit UUIDs
                    // Loop through the advertised 128-bit UUID's.
                    while (len >= 16) {
                        try {
                            // Wrap the advertised bits and order them.
                            ByteBuffer buffer = ByteBuffer.wrap(advertisedData, offset++, 16).order(ByteOrder.LITTLE_ENDIAN);
                            long mostSignificantBit = buffer.getLong();
                            long leastSignificantBit = buffer.getLong();
                            uuids.add(new UUID(leastSignificantBit,
                                    mostSignificantBit));
                        } catch (IndexOutOfBoundsException e) {
                            // Defensive programming.
                            //Log.e(LOG_TAG, e.toString());
                            continue;
                        } finally {
                            // Move the offset to read the next uuid.
                            offset += 15;
                            len -= 16;
                        }
                    }
                    break;
                default:
                    offset += (len - 1);
                    break;
            }
        }
        return uuids;
    }

    // Boilerplate code from the activity creation:

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

}
