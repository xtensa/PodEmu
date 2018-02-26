/**

 Copyright (C) 2015, Roman P., dev.roman [at] gmail

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see http://www.gnu.org/licenses/

 */

package com.rp.podemu;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Created by E-LAB on 07/22/2017.
 */

public class SerialInterface_BLE extends SerialInterface_Common implements SerialInterface
{
    public static int BLE_REQUIRED_SDK_VERSION = 21;
    public static UUID SERVICE_UUID =               UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    public static UUID CHARACTERISTIC_SERIAL_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    /*
    public final static UUID BLE_UUIDS[]=
            {
                    UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"),
                    UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"), // central
                    UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")  // periferial
            };
    */

    private static BluetoothDevice bleDevice;
    private BluetoothAdapter mBluetoothAdapter;
    private static Context baseContext;
    private static SerialInterface_BLE serialInterfaceBleInstance = null;
    private static SharedPreferences sharedPref;
    private static Thread bleScanThread;
    private Handler mHandler = null;
    private static BluetoothGatt bleGATT;
    private static final int BUFFER_SIZE = 1024;


    private static ByteFIFO mInBuffer = null;
    private ByteFIFO mOutBuffer = null;
    private boolean writeLock = false;

    private int i=0;

    private ExecutorService readExecutorService;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = SerialInterface_BT.STATE_NONE;
    public static final int STATE_SEARCHING = SerialInterface_BT.STATE_SEARCHING;
    public static final int STATE_CONNECTING = SerialInterface_BT.STATE_CONNECTING;
    public static final int STATE_CONNECTED = SerialInterface_BT.STATE_CONNECTED;
    public static final int STATE_LOST = SerialInterface_BT.STATE_LOST;
    public static final int STATE_FAILED = SerialInterface_BT.STATE_FAILED;

    private int bleState=STATE_NONE;

    //private ArrayList<BluetoothDevice> bleDevices = SettingsActivity.getBleDevices();


    public SerialInterface_BLE ()
    {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mInBuffer = new ByteFIFO(BUFFER_SIZE);
        mOutBuffer = new ByteFIFO(BUFFER_SIZE);
        readExecutorService = Executors.newSingleThreadExecutor();
    }


    private static SharedPreferences getSharedPref()
    {
        if(sharedPref == null)
            sharedPref = baseContext.getSharedPreferences("PODEMU_PREFS", Context.MODE_PRIVATE);
        return sharedPref;
    }


    public static synchronized SerialInterface_BLE getInstance(Context context)
    {
        baseContext = context;
        return getInstance();
    }

    public static synchronized SerialInterface_BLE getInstance()
    {
        int bluetoothEnabled=getSharedPref().getInt("bluetoothEnabled", 0);
        if(bluetoothEnabled==0)
        {
            serialInterfaceBleInstance = null;
            PodEmuLog.debug("SIBLE: bluetooth is disabled. Skipping BLE initialization.");
        }
        else if(Build.VERSION.SDK_INT < BLE_REQUIRED_SDK_VERSION)
        {
            PodEmuLog.error("SIBLE: bluetooth is enabled but API level is less then 18. Skipping BLE initialization.");
        }
        else if(serialInterfaceBleInstance == null)
        {
            PodEmuLog.debug("SIBLE: initializing new instance");
            serialInterfaceBleInstance = new SerialInterface_BLE();
        }

        return serialInterfaceBleInstance;
    }

    private void acquireWriteLock()
    {
        PodEmuLog.debug("SIBLE: acquireWriteLock() for thread - " + Thread.currentThread().getId());
        try
        {
            synchronized (this)
            {
                while (writeLock) Thread.sleep(10);
                writeLock = true;
                PodEmuLog.debug("SIBLE: lock acquired for thread - " + Thread.currentThread().getId());
            }
        }
        catch (InterruptedException e)
        {
            PodEmuLog.debug("SIBLE: acquiring write lock interrupted for thread - " + Thread.currentThread().getId());
        }
    }

    private void releaseWriteLock()
    {
        PodEmuLog.debug("SIBLE: releaseWriteLock() for thread - " + Thread.currentThread().getId());
        writeLock = false;
    }

    public void setHandler(Handler handler)
    {
        mHandler = handler;
    }

    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state)
    {
        PodEmuLog.debug("SIBLE: setState() " + SerialInterface_BT.getStateName(bleState) + " -> " + SerialInterface_BT.getStateName(state));
        bleState = state;
        //mHandler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
        PodEmuService.communicateSerialStatusChange();
    }

    private final BluetoothGattCallback bleGattCallback = new BluetoothGattCallback()
    {
        @Override
        @TargetApi(21)
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
        {
            PodEmuLog.debug("SIBLE: GATT onConnectionStateChange");
            switch (newState)
            {
                case BluetoothProfile.STATE_CONNECTED:
                    setState(STATE_CONNECTED);
                    gatt.discoverServices();
                    synchronized (SerialInterface_BLE.this)
                    {
                        SerialInterface_BLE.this.notifyAll();
                    }
                    break;
                case BluetoothProfile.STATE_CONNECTING:
                    setState(STATE_CONNECTING);
                    break;
                case BluetoothProfile.STATE_DISCONNECTING:
                    setState(STATE_LOST);
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    setState(STATE_NONE);
                    break;
            }
            //PodEmuService.communicateSerialStatusChange();
        }

        @Override
        @TargetApi(21)
        // New services discovered
        public void onServicesDiscovered(BluetoothGatt gatt, int status)
        {
            PodEmuLog.debug("SIBLE: GATT onServiceDiscovered with status=" + status);
            super.onServicesDiscovered(gatt, status);
            boolean isSerialSupported = false;

            for (BluetoothGattService service : gatt.getServices())
            {
                PodEmuLog.debug("SIBLE: Service: " + service.getUuid());
                //if (Arrays.asList(BLE_UUIDS).contains(service.getUuid()))
                if(SERVICE_UUID.equals(service.getUuid()))
                {
                    PodEmuLog.debug("SIBLE: BLE found serial device!");

                    //gatt.readCharacteristic(service.getCharacteristic(CHARACTERISTIC_VERSION_UUID));
                    //gatt.readCharacteristic(service.getCharacteristic(CHARACTERISTIC_DESC_UUID));
                    //gatt.setCharacteristicNotification(service.getCharacteristic(CHARACTERISTIC_MESSAGE_UUID), true);
                    //gatt.setCharacteristicNotification(service.getCharacteristic(CHARACTERISTIC_RFCOMM_TRANSFER_UUID), true);

                    BluetoothGattCharacteristic bleGattCharacteristic = service.getCharacteristic(CHARACTERISTIC_SERIAL_UUID);

                    if(bleGattCharacteristic != null)
                    {
                        isSerialSupported = true;
                        gatt.readCharacteristic(service.getCharacteristic(CHARACTERISTIC_SERIAL_UUID));
                        gatt.setCharacteristicNotification(bleGattCharacteristic, true);
                    }
                }
            }

            if(isSerialSupported)
            {
                PodEmuLog.debug("SIBLE: BLE serial supported!");
            }
            else
            {
                PodEmuLog.debug("SIBLE: BLE serial NOT supported!");
            }
        }

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
        {
            PodEmuLog.debug("SIBLE: GATT onCharacteristicRead");
        }

        @Override
        // Result of a characteristic write operation
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
        {
            PodEmuLog.debug("SIBLE: GATT onCharacteristicWrite");
            if(status == BluetoothGatt.GATT_SUCCESS)
            {
                PodEmuLog.debug("SIBLE: GATT write success");
            }
            else
            {
                PodEmuLog.debug("SIBLE: ERROR writing to GATT");
            }

            releaseWriteLock();
        }


        @Override
        @TargetApi(21)
        // Result of a characteristic read operation
        public synchronized void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic)
        {
            final UUID uuid = characteristic.getUuid();
        //    final int j=i;
        //    i++;

            if(CHARACTERISTIC_SERIAL_UUID.equals(uuid))
            {
                final byte[] bytes = characteristic.getValue();
                Runnable runnable = new Runnable()
                {
                    @Override
                    public void run()
                    {
                        //PodEmuLog.debug("SIBLE: GATT onCharacteristicChanged for " + uuid);


                        //PodEmuLog.debug("SIBLE: received: " + (new String(bytes)));
                        try
                        {
                            mInBuffer.add(bytes, bytes.length);
                        //    PodEmuLog.debug("SIBLE: GATT received " + bytes.length + " bytes: " + OAPMessenger.oap_hex_to_str(bytes, bytes.length) + " - " + j);
                        }
                        catch (InterruptedException e)
                        {
                            PodEmuLog.error("SIBLE: something wrong happen. Adding bytes to mInBuffer interrupted.");
                        }

                    }
                };

                //PodEmuLog.debug("SIBLE: GATT received " + bytes.length + " bytes: " + OAPMessenger.oap_hex_to_str(bytes, bytes.length) + " - " + j + " - PRE!!!");
                readExecutorService.execute(runnable);
            }
        }

    };

    @TargetApi(21)
    class MyScanCallback extends ScanCallback
    {
        @Override
        public void onScanResult(int callbackType, ScanResult result)
        {
            //String bleDeviceAddress = getSharedPref().getString("bluetoothDeviceAddress", "unknown");
            //String devName = device.getName() + " [" + device.getAddress() + "]";
            //PodEmuLog.debug("SIBLE: device found - " + devName);

            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothLeScanner bluetoothLeScanner = adapter.getBluetoothLeScanner();
            //Do the work below on a worker thread instead!
            bleDevice = result.getDevice();

            bluetoothLeScanner.stopScan(this);
            setState(STATE_CONNECTING);

            bleGATT = bleDevice.connectGatt(baseContext, false, bleGattCallback);
            bleGATT.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);

        }
    }



    @TargetApi(21)
    public synchronized boolean init(Context context)
    {
        PodEmuLog.debug("SIBLE: init()");

        if(bleScanThread!=null)
        {
            PodEmuLog.debug("SIBLE: bleScanThread already running. Skipping initialization.");
        }
        else if(bleDevice==null)
        {
            PodEmuLog.debug("SIBLE: starting new BLE scan");
            /*bleScanThread = new Thread(new Runnable()
            {
                @Override
                public void run()
                {
*/
            try
            {
                PodEmuLog.debug("SIBLE: BLE scan started.");
                ScanSettings scanSettings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();

                ScanFilter scanFilter = new ScanFilter.Builder()
                        .setDeviceName(getName())
                        .setDeviceAddress(getAddress())
                        .build();

                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                BluetoothLeScanner bluetoothLeScanner = adapter.getBluetoothLeScanner();
                bluetoothLeScanner.startScan(Collections.singletonList(scanFilter), scanSettings, new MyScanCallback());

                setState(STATE_SEARCHING);
                SerialInterface_BLE.this.wait();
                PodEmuLog.debug("SIBLE: wait finished");
            }
            catch (InterruptedException e)
            {
                PodEmuLog.debug("SIBLE: BLE scan interrupted");
            }
/*                }
            });
            bleScanThread.start();
  */      }
        else if(bleGATT == null)
        {
            PodEmuLog.debug("SIBLE: getting new GATT handler");
            bleGATT = bleDevice.connectGatt(baseContext, true, bleGattCallback);
            bleGATT.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
        }

        return isConnected();
    }

    @TargetApi(21)
    public int write(byte[] buffer, int numBytes)
    {
        PodEmuLog.debug("SIBLE: starting GATT characteristic write");
        if(bleGATT==null)
        {
            PodEmuLog.debug("SIBLE: write attempt before GATT initialized");
            return -1;
        }

        byte[] tmpBuff = new byte[numBytes];
        System.arraycopy(buffer,0, tmpBuff, 0, numBytes);

        try
        {
            final BluetoothGattCharacteristic characteristic = bleGATT
                    .getService(SERVICE_UUID)
                    .getCharacteristic(CHARACTERISTIC_SERIAL_UUID);

            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            characteristic.setValue(tmpBuff);

            acquireWriteLock();

            if (!bleGATT.writeCharacteristic(characteristic))
            {
                PodEmuLog.error("SIBLE: write() couldn't send data!");
                releaseWriteLock();
                numBytes = -1;
            }

            PodEmuLog.debug("SIBLE: written " + numBytes + " bytes to GATT characteristic");
        }
        catch(NullPointerException e)
        {
            PodEmuLog.debug("SIBLE: ERROR during write. Interface disconnected.");
        }

        return numBytes;
    }


    public synchronized int read(byte[] buffer, int offset, int size)
    {
        int numBytes;
        byte[] tmpBuf;

        tmpBuf = mInBuffer.removeSome(size);
        numBytes = tmpBuf.length;
        System.arraycopy(tmpBuf, 0, buffer, offset, numBytes);
        //if(numBytes>0)PodEmuLog.debug("SIBLE: read() " + (new String(buffer)));

        return numBytes;
    }

    public synchronized int read(byte[] buffer)
    {
        return read(buffer, 0, BUFFER_SIZE);
    }

    public String readString()
    {
        // FIXME
        return "";
    }

    public String getName()
    {
        return getSharedPref().getString("bluetoothDeviceName", SerialInterface_BT.BTDEV_NAME_DEFAULT);
    }

    public String getAddress()
    {
        return getSharedPref().getString("bluetoothDeviceAddress", SerialInterface_BT.BTDEV_ADDRESS_DEFAULT);
    }

    public int getVID()
    {
        return 0;
    }

    public int getPID()
    {
        return 0;
    }

    public void setBaudRate(int rate)
    {
        // FIXME:
        return;
    }

    public int getBaudRate()
    {
        // FIXME
        return 57600;
    }

    public boolean isConnecting()
    {
        return (bleState == STATE_CONNECTING);
    }

    public boolean isConnected()
    {
        return (bleState == STATE_CONNECTED);
    }

    public int getReadBufferSize()
    {
        return BUFFER_SIZE;
    }

    @TargetApi(21)
    public void close()
    {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothLeScanner bluetoothLeScanner = adapter.getBluetoothLeScanner();
        bluetoothLeScanner.stopScan(new MyScanCallback());

        if(bleGATT!=null) bleGATT.close();
        bleGATT=null;
        bleDevice=null;
        bleScanThread=null;
        mInBuffer.removeAll();
        releaseWriteLock();
    }

    public void restart()
    {
        close();
        init(baseContext);
    }

    public static boolean checkLocationPermissions(final Activity activity)
    {
        Context context = activity;
        if(sharedPref==null)
        {
            sharedPref=context.getSharedPreferences("PODEMU_PREFS", Context.MODE_PRIVATE);
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED)
        {
            if(ActivityCompat.shouldShowRequestPermissionRationale(activity, android.Manifest.permission.ACCESS_COARSE_LOCATION))
            {
                new AlertDialog.Builder(context)
                        .setTitle("Information")
                        .setMessage("To access Bluetooth Low Energy devices PodEmu requires Location permissions.")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int which)
                            {
                                // continue
                                ActivityCompat.requestPermissions(activity,
                                        new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION},
                                        100);
                            }
                        })
                        .setIcon(android.R.drawable.ic_dialog_info)
                        .show();
            }
            else
            {
                ActivityCompat.requestPermissions(activity,
                        new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION},
                        100);

                boolean firstTimeRequestPermissions = getSharedPref().getBoolean("firstTimeRequestPermissionsLocation", true);

                PodEmuLog.debug("SIBLE: first time? " + firstTimeRequestPermissions);

                if( !firstTimeRequestPermissions )
                {
                    new AlertDialog.Builder(context)
                            .setTitle("Permissions missing :(")
                            .setMessage("You did not grant Location permissions. Bluetooth LE functionality is disabled. If you want to collect logs please go to Setting -> Apps -> PodEmu -> Permissions and grant Location permissions.")
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
                            {
                                public void onClick(DialogInterface dialog, int which)
                                {

                                }
                            })
                            .setIcon(android.R.drawable.ic_dialog_info)
                            .show();

                }
                SharedPreferences.Editor editor = getSharedPref().edit();
                editor.putBoolean("firstTimeRequestPermissionsLocation", false);
                editor.apply();
            }
        }
        else
        {
            return true;
        }

        return false;

    }


}
