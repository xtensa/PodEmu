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

//import android.hardware.usb.UsbManager;


import android.content.Context;
import android.os.Handler;

public interface SerialInterface
{
//    boolean init(UsbManager manager);
    boolean init(Context context);

    int write(byte[] buffer, int numBytes);

    int read(byte[] buffer);

    String readString();

    String getName();
    String getAddress();

    String getAccessoryName();
    void   setAccessoryName(String name);

    int getVID();
    int getPID();

    void setHandler(Handler handler);

    void setBaudRate(int rate);
    int getBaudRate();

    boolean isConnecting();
    boolean isConnected();

    int getReadBufferSize();

    void close();
}
