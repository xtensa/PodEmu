package com.rp.podemu;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.hoho.android.usbserial.driver.ProlificSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.List;

/**
 * Created by rp on 9/4/15.
 */
public class SerialInterface_USBSerial implements SerialInterface
{

    static private UsbDeviceConnection connection;
    static private UsbSerialPort port;

    public void init(UsbManager manager)
    {
        UsbManager usbManager=manager;

        // Find all available drivers from attached devices.
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            return;
        }

        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        connection = manager.openDevice(driver.getDevice());
        if (connection == null)
        {
            // You probably need to call UsbManager.requestPermission(driver.getDevice(), ..)
            return;
        }

        // Read some data! Most have just one port (port 0).
        List<UsbSerialPort> ports = driver.getPorts();
        port = ports.get(0);
        try {
            port.open(connection);
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        }
        catch (IOException e)
        {
            // TODO Deal with error.
        }


    }

    public int write(byte[] buffer, int numBytes)
    {
        // if connection is not set then nothing to write
        if(!isConnected()) return 0;

        try
        {
            return port.write(buffer, 0);
        }
        catch (IOException e)
        {
            // TODO Deal with error.
        }

        // if we are here then there was exception and 0 bytes were read
        return 0;
    }

    /**
     * Function reads bytes from serial port and returns number of read bytes
     * Warning: function may read maximum 258 bytes (256 basing on PL2303 datasheet)
     *
     * @param buffer - buffer for read data to be stored in. Should be at least
     *                 258 bytes long
     * @return       - number of bytes read
     */
    public int read(byte[] buffer)
    {
        int numBytesRead=0;

        // if connection is not set then nothing to read
        if(!isConnected()) return 0;

        try {
            numBytesRead = port.read(buffer, 1);
            Log.d("RPP", "Read " + numBytesRead + " bytes.");
        }
        catch (IOException e)
        {
            // TODO Deal with error.
            return -1;
        }
        finally
        {
        }

        return numBytesRead;
    }

    public String readString()
    {
        byte[] buffer=new byte[ProlificSerialDriver.ProlificSerialPort.DEFAULT_READ_BUFFER_SIZE];
        int numBytesRead=read(buffer);
        String str=new String(buffer,0,numBytesRead);
        //return "["+numBytesRead+"] "+str;
        return str;
    }

    public boolean isConnected()
    {
        return (connection!=null);
    }

    public void close()
    {
        try
        {
            if(port!=null)port.close();
        }
        catch (IOException e)
        {
            // TODO Deal with error.
        }
    }
}
