package com.rp.podemu;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.ParcelFileDescriptor;

import com.hoho.android.usbserial.driver.UsbId;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by rp on 10/22/15.
 */
public class SerialInterface_FT31xD implements SerialInterface
{
    private static Map<String, String> deviceList = new LinkedHashMap<>();
    private static Map<String, Integer> deviceBufferSizes = new LinkedHashMap<>();

    private static String ManufacturerString = "FTDI";
    private static String VersionString = "1.0";

    private static boolean accessory_attached=false;
    private ParcelFileDescriptor fileDescriptor = null;
    private UsbAccessory usbAccessory;
    private boolean mPermissionRequestPending = false;

    private FileInputStream inputStream = null;
    private FileOutputStream outputStream = null;

    private UsbManager usbmanager;

    private static int baudRate=57600;
    public Context context=null;

    public void setBaudRate(int rate)
    {
        if(rate<9600) rate=9600;
        if(rate>115200) rate=115200;
        baudRate=rate;
    }

    public int getBaudRate()
    {
        return baudRate;
    }

    public SerialInterface_FT31xD()
    {
        deviceList.put("FTDIUARTDemo", "FTDI Uart");
        deviceList.put("Android Accessory FT312D", "FT312D");

        // read buffer sizes
        deviceBufferSizes.put("FTDIUARTDemo", 512);
        deviceBufferSizes.put("Android Accessory FT312D", 5512);

    }

    public void setHandler(Handler handler)
    {
        // Doing nothing. This method is mainly for BT interface.
    }

    /**
     * Initilize the device
     * @param context - application context
     * @return - true on success, false on failure
     */

    public boolean init(Context context)
    {
        PodEmuLog.debug("FT31xD: initialization started.");
        usbmanager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        if(ResumeAccessory()!=0)
        {
            PodEmuLog.debug("FT31xD: no devices found. Exiting...");
            return false;
        }

        PodEmuLog.debug("FT31xD: openning connection with baud rate="+baudRate);

        byte configData[]=new byte[8];
		/*prepare the baud rate buffer*/
        configData[0] = (byte)baudRate;
        configData[1] = (byte)(baudRate >> 8);
        configData[2] = (byte)(baudRate >> 16);
        configData[3] = (byte)(baudRate >> 24);

        /*data bits*/
        configData[4] = 8;
        /*stop bits*/
        configData[5] = 1;
        /* parity:
                0: none
                1: odd
                2: even
                3: mark
                4: space
        */
        configData[6] = 0;
        /*flow control:
                0: none
                1: hardware (CTS/RTS)
        */
        configData[7] = 0;

		/*send the UART configuration packet*/
        write(configData, 8);

        return true;
    }


    /*resume accessory*/
    public int ResumeAccessory()
    {
        if (inputStream != null && outputStream != null)
        {
            return 1;
        }

        UsbAccessory[] accessories = usbmanager.getAccessoryList();
        if(accessories != null)
        {
            PodEmuLog.debug("FT31xD: Accessory Attached");
        }
        else
        {
            accessory_attached = false;
            return 2;
        }

        UsbAccessory accessory = (accessories.length==0 ? null : accessories[0]);
        if (accessory != null)
        {
            PodEmuLog.debug("FT31xD: Accessory info: " + accessory.toString());

            if( !accessory.getManufacturer().contains(ManufacturerString))
            {
                PodEmuLog.debug("FT31xD: Manufacturer is not matched! Found manufacturer: " + accessory.getManufacturer());
                return 1;
            }

            if(!deviceList.containsKey(accessory.getModel()))
            {
                PodEmuLog.debug("FT31xD: Model is not matched. Found model: " + accessory.getModel());
                return 1;
            }

            if( !accessory.getVersion().contains(VersionString))
            {
                PodEmuLog.debug("FT31xD: Version is not matched. Version found: " + accessory.getVersion());
                return 1;
            }

            PodEmuLog.debug("FT31xD: Manufacturer, Model & Version are matched!");


            // we don't need to request permission as we are using intent filter
            // but we are checking if we have them anyway
            if (usbmanager.hasPermission(accessory))
            {
                if(OpenAccessory(accessory))
                {
                    accessory_attached = true;
                }
                else
                {
                    accessory_attached = false;
                    return 3;
                }
            }
            else
            {
                PodEmuLog.error("FT31xD: no permission for accessory");
                accessory_attached = false;
            }


        }

        return 0;
    }


    public int write(byte[] buffer, int numBytes)
    {
        // if connection is not set then nothing to write
        if(!isConnected())
        {
            PodEmuLog.error("FT31xD: write attempt while accessory not attached!");
            return 0;
        }

        try
        {
            outputStream.write(buffer, 0, numBytes);
            return numBytes;
        }
        catch (IOException e)
        {
            PodEmuLog.printStackTrace(e);
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
        int numBytesRead;

        // if connection is not set then nothing to read
        if(!isConnected()) return 0;

        try
        {
            numBytesRead = inputStream.read( buffer, 0, getReadBufferSize());
        }
        catch (IOException e)
        {
            // We don't want to spam logs.
            // IOException usually happen when accessory is detached.
            // proper handling is done in buffer thread
            return -1;
        }

        return numBytesRead;
    }

    public String readString()
    {
        byte[] buffer=new byte[getReadBufferSize()];
        int numBytesRead=read(buffer);
        String str=new String(buffer,0,numBytesRead);
        //return "["+numBytesRead+"] "+str;
        return str;
    }

    public boolean isConnected()
    {
        boolean result=true;
        result &= accessory_attached;
        result &= (fileDescriptor!=null);
        result &= (inputStream!=null);
        result &= (outputStream!=null);
        return result;
    }

    public void close()
    {
        PodEmuLog.debug("FT31xD: closing accessory.");
        if(accessory_attached)
            CloseAccessory();
    }

    public int getVID()
    {
        return UsbId.VENDOR_FTDI;
    }

    public int getPID()
    {
        int pid=0;
        if (usbAccessory.getModel().contains("FTDIUARTDemo")) pid=0x311D;
        if (usbAccessory.getModel().contains("FT312D")) pid=0x312D;
        return pid;
    }


    public String getName()
    {
        if(deviceList.containsKey(usbAccessory.getModel()))
            return deviceList.get(usbAccessory.getModel());
        else
            return "Unknown device";
    }

    public int getReadBufferSize()
    {
        return deviceBufferSizes.get(usbAccessory.getModel());
    }



    public boolean OpenAccessory(UsbAccessory accessory)
    {
        fileDescriptor = usbmanager.openAccessory(accessory);

        if(fileDescriptor != null)
        {
            PodEmuLog.debug("FT31xD: file descriptor successfully opened");
            usbAccessory = accessory;

            FileDescriptor fd = fileDescriptor.getFileDescriptor();

            inputStream  = new FileInputStream(fd);
            outputStream = new FileOutputStream(fd);

            /* check if any of them are null */
            if(inputStream == null || outputStream==null)
            {
                PodEmuLog.error("FT31xD: sth went wrong. In or Out descriptor is null!");
                return false;
            }
        }
        else
        {
            PodEmuLog.error("FT31xD: fileDescriptor is null!");
            return false;
        }
        return true;
    }

    private void CloseAccessory()
    {

        accessory_attached = false;

        try
        {
            if(fileDescriptor != null)
                fileDescriptor.close();
        }
        catch (IOException e)
        {
            PodEmuLog.printStackTrace(e);
        }

        try
        {
            if(inputStream != null)
                inputStream.close();
        }
        catch(IOException e)
        {
            PodEmuLog.printStackTrace(e);
        }

        try
        {
            if(outputStream != null)
                outputStream.close();
        }
        catch(IOException e)
        {
            PodEmuLog.printStackTrace(e);
        }

        fileDescriptor = null;
        inputStream = null;
        outputStream = null;
    }

}
