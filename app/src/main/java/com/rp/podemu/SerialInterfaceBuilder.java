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

public class SerialInterfaceBuilder
{
    private static SerialInterface serialInterface=null;

    public SerialInterface getSerialInterface(Context context)
    {
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
        if(serialInterface==null)
        {
            serialInterface = SerialInterface_BT.getInstance(context);
            serialInterface.init(context);
        }

        return serialInterface;
    }

    public SerialInterface getSerialInterface()
    {
        return serialInterface;
    }

    public void detach()
    {
        if( serialInterface != null ) serialInterface.close();
        serialInterface=null;
    }
}
