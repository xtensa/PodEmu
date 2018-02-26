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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

public class SerialInterfaceBuilder
{
    private static SerialInterfaceBuilder serialInterfaceBuilder;
    private static SerialInterface serialInterface=null;

    public SerialInterface getSerialInterface(Context context, Handler handler)
    {
        PodEmuLog.debug("SIB: getSerialInterface()");
        SharedPreferences sharedPref = context.getSharedPreferences("PODEMU_PREFS", Context.MODE_PRIVATE);
        int bluetoothEnabled=sharedPref.getInt("bluetoothEnabled", 0);
        boolean bluetoothIsBle=sharedPref.getBoolean("bluetoothIsBle", false);

        if(serialInterface==null)
        {
            serialInterface = new SerialInterface_USBSerial();
            if(!serialInterface.init(context)) serialInterface=null;
        }
        if(serialInterface==null)
        {
            serialInterface = new SerialInterface_FT31xD();
            if(!serialInterface.init(context)) serialInterface=null;
        }
        if(bluetoothEnabled==1 && !bluetoothIsBle && serialInterface==null)
        {
            serialInterface = SerialInterface_BT.getInstance(context);
            serialInterface.init(context);
        }
        if(bluetoothEnabled==1 && bluetoothIsBle && serialInterface==null)
        {
            PodEmuLog.debug("SIB: initializing BLE");
            serialInterface = SerialInterface_BLE.getInstance(context);
            serialInterface.init(context);
        }

        if(serialInterface != null)
        {
            serialInterface.setHandler(handler);
        }

        return serialInterface;
    }

    public static SerialInterfaceBuilder getInstance()
    {
        if(serialInterfaceBuilder==null) serialInterfaceBuilder=new SerialInterfaceBuilder();
        return serialInterfaceBuilder;
    }

    public static SerialInterface getSerialInterface()
    {
        return serialInterface;
    }

    public void detach()
    {
        if( serialInterface != null ) serialInterface.close();
        serialInterface=null;
        PodEmuService.communicateSerialStatusChange();
    }
}
