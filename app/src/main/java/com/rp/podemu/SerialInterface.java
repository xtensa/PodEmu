package com.rp.podemu;

import android.hardware.usb.UsbManager;

/**
 * Created by rp on 9/4/15.
 */
public interface SerialInterface
{
    void init(UsbManager manager);

    int write(byte[] buffer, int numBytes);

    int read(byte[] buffer);

    String readString();

    boolean isConnected();

    void close();
}
