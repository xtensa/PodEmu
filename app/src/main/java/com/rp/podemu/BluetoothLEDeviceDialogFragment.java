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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;
import static android.util.TypedValue.COMPLEX_UNIT_PX;

/**
 * Created by E-LAB on 8/28/15.
 */
public class BluetoothLEDeviceDialogFragment extends DialogFragment // implements DialogInterface.OnClickListener
{
    private ArrayList<BluetoothDevice> bluetoothLEDevices;
    // Use this instance of the interface to deliver action events

    /* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event callbacks.
     * Each method passes the DialogFragment in case the host needs to query it. */
    public interface BluetoothLEDeviceDialogListener
    {
        void onBluetoothLEDeviceSelected(DialogInterface dialog, int which);
    }


    static private List<String> bleList = new ArrayList<>();
    ArrayList<BluetoothDevice> bleDevices = SettingsActivity.getBleDevices();

    private ArrayAdapter<String> arrayAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    //BluetoothLeScanner bleScanner;

    final BluetoothAdapter.LeScanCallback bleScanCallback =
            new BluetoothAdapter.LeScanCallback()
            {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord)
                {
                    PodEmuLog.debug("BLE: LeScanCallback()");
                    getActivity().runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {

                            if(!bleDevices.contains(device))
                            {
                                String devName = device.getName() + " [" + device.getAddress() + "]";
                                PodEmuLog.debug("BLE: device found - " + devName);
                                bleDevices.add(device);
                                bleList.add(devName);
                                arrayAdapter.notifyDataSetChanged();
                            }
                        }
                    });
                }
            };

    // Use this instance of the interface to deliver action events
    BluetoothLEDeviceDialogListener mListener;

    // Override the Fragment.onAttach() method to instantiate the NoticeDialogListener
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            mListener = (BluetoothLEDeviceDialogListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement BluetoothLEDeviceDialogListener");
        }
    }


    public void setBluetoothLEDevices(ArrayList<BluetoothDevice> btDevices)
    {
        bluetoothLEDevices=btDevices;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        stopBLE();
    }

    @TargetApi(18)
    private void stopBLE()
    {
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
                && Build.VERSION.SDK_INT >= 18)
        {
            PodEmuLog.debug("BLE: BLE scan stopped.");
            mBluetoothAdapter.stopLeScan(bleScanCallback);
        }
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {

        PodEmuLog.error("onCreateDialog()");
        arrayAdapter  = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, bleList);

        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.dialog_select_ble_device)
                .setItems((new String[0]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // The 'which' argument contains the index position
                        // of the selected item
                        mListener.onBluetoothLEDeviceSelected(dialog, which);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int which)
                    {

                    }
                });

        // Create the AlertDialog object and return it
        final AlertDialog dialog = builder.create();


        // overriding button behaviours
        dialog.setOnShowListener(new DialogInterface.OnShowListener()
        {

            @TargetApi(18)
            private void scanBLE()
            {
                Handler mHandler = new Handler();
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                final long SCAN_PERIOD = 15000;

                // TODO: maybe in the future replace with BluetoothLeScanner, but this will recuire API level 21
                //bleScanner = mBluetoothAdapter.getBluetoothLeScanner();


                // Stops scanning after a pre-defined scan period.
                mHandler.postDelayed(new Runnable()
                {
                    @Override
                    public void run()
                    {
                 /*       PodEmuLog.debug("BLE: BLE scan stopped.");
                        isBleScanning = false;
                        mBluetoothAdapter.stopLeScan(bleScanCallback);
                   */
                    }
                }, SCAN_PERIOD);

                // Start scanning in background
                mHandler.postDelayed(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        PodEmuLog.debug("BLE: BLE scan started.");
                        mBluetoothAdapter.startLeScan(/*SerialInterface_BT.BTDEV_UUID,*/ bleScanCallback);
                    }
                }, 0);
            }


            @Override
            public void onShow(DialogInterface dialogInterface)
            {
                final ListView listView = dialog.getListView();

                PodEmuLog.error("onShow()");


                listView.setAdapter(arrayAdapter);

                Button buttonNegative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                Button buttonPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                /*
                buttonNegative.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {

                        bleList.add("STH");
                        arrayAdapter.notifyDataSetChanged();
                        }
                });

                buttonPositive.setOnClickListener(new View.OnClickListener()
                {

                    @Override
                    public void onClick(View view)
                    {
                        if(bleList.size()>0)
                        {
                            bleList.remove(bleList.size() - 1);
                            arrayAdapter.notifyDataSetChanged();
                        }
                    }
                });
            */


                // Start scanning for BLE devices only if BLE is supported and API level >=18
                if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
                        && Build.VERSION.SDK_INT >= 18)
                {
                    scanBLE();

                }
            }
        });

        return dialog;
    }


}
