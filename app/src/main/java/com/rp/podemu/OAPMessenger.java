package com.rp.podemu;

import android.util.Log;

/**
 * Created by rp on 9/8/15.
 */
public class OAPMessenger
{
    private String TAG = "RPP OAP";
    private boolean DEBUG = true;
    private static int READ=1;
    private static int WRITE=2;
    private int line_cmd_pos = 0;
    private int line_cmd_len = 0;
    private byte line_buf[] = new byte[300]; // max should be 259 bytes

    // definition of the in/out line if more than one instance of object created
    private static int line = 0;

    OAPMessenger()
    {
        line++;
    }

    /*
     * Return values:
     *     0 - byte received but msg in progress
     *     1 - full message received
     *    -1 - error occured
     */
    public synchronized int oap_receive_byte(byte b)
    {
        oap_print_char(READ, b, line_cmd_pos);

        if (line_cmd_pos == 0 && b != (byte)0xff)
        {
            oap_print_msg("Line " + line + ": ERROR: first byte is not 0xFF. Received 0x" + String.format("%02X", b));
            return -1;
        }

        if (line_cmd_pos == 1 && b != (byte)0x55)
        {
            oap_print_msg("Line " + line + ": ERROR: second byte is not 0x55. Received 0x" + String.format("%02X", b));
            return -1;
        }

	/* at least checksum could be 0xFF so the next code should not be uncommented*/
	/*
	if(*line_cmd_pos > 0 && byte == 0xff)
	{
		sprintf(str, "Line %d: ERROR: New message arrived while still reading previous message.", line);
                oap_print_msg(str);
		*line_cmd_pos=0;
		*line_cmd_len=0;
	}
	*/

        if (line_cmd_pos == 2)
        {
		/*
		 * additional bytes:
         *     2 for control bytes (0xFF 0x55)
		 *     1 for msg len
		 *     (not counted) 1 for msg mode
		 *     (not counted) 2 for command
		 *     (not counted) parameter
		 *     1 at the end for checksum
		 */
            line_cmd_len = (int) (b&0xff) + 4;

            if (DEBUG)
            {
                oap_print_msg(String.format("Line " + line + ": MSG LEN: %d TOTAL LEN: %d", (b & 0xff), line_cmd_len));
            }

        }

        if (line_cmd_len > 259)
        {
            oap_print_msg("Line " + line + ": ERROR: message len cannot exceed 259 bytes");
            return -1;
        }

        line_buf[line_cmd_pos] = b;

        line_cmd_pos++;

        if (line_cmd_pos == line_cmd_len)
        {
            int msg_len;
            byte checksum;
            oap_print_msg("Line " + line + ": RAW MSG  IN: " +
                            oap_hex_to_str(line_buf, line_cmd_len));

            oap_print_podmsg(line_buf);

            checksum = oap_calc_checksum(line_buf, line_cmd_len);
            if (line_buf[line_cmd_len - 1] != checksum)
            {
                oap_print_msg("Line " + line + String.format(": ERROR: checksum error. Received: %02X  Should be: %02X", line_buf[line_cmd_len - 1], checksum));
            }
            else
            {
                // Received message is OK. We cen process it and prepare the reply
                if (DEBUG)
                {
                    //Log.d(TAG, "Line " + line + ": ERROR: checksum OK. Received: %d  Should be: %d", line, line_buf[*line_cmd_len-1], checksum);
                    oap_print_msg("Line " + line + ": Checksum OK");
                }

                oap_process_msg(line_buf,line_cmd_len);
            }

            msg_len = line_cmd_len;
            line_cmd_pos = 0;
            line_cmd_len = 0;

            return msg_len;
        }

        return 0;
    }


    public void oap_process_msg(byte[] line_buf, int line_cmd_len)
    {
        int len=(line_buf[2] & 0xff);
        int mode=(line_buf[3] & 0xff);
        int cmd=((line_buf[4] & 0xff) << 8 ) + (line_buf[5] & 0xff);

        Log.d(TAG, String.format("Len: %d Mode: 0x%02X, CMD: 0x%04X",len,mode,cmd));

        switch(cmd)
        {
            case 0x0102:
                Log.d(TAG, "CMD 0x0102");
                break;
            default:
                Log.d(TAG, "Unknown command");
        }
    }

    public byte oap_calc_checksum(byte buf[], int len)
    {
        int checksum=0, j;
	    /* do not consider first 2 bytes and last byte for checksum*/
        for(j=2;j<len-1;j++)
        {
            checksum+=buf[j];
        }
        checksum &= 0xff;
        checksum = 0x100 - checksum;
        return (byte)checksum;
    }



    void oap_print_char(int rw, byte b, int num)
    {
        if(DEBUG)
        {
        //    oap_print_msg("Line " + line + " cnt=" + num + " " + String.format(": %s: %02X", (rw == READ ? "RCV" : "WRITE"), b));
        }

        //Log.d(TAG, "DEBUG LINE " + line + String.format(": %02X\n", b));

        // TODO write char to logfile
        //write(fd_log, tmp, strlen(tmp));

    }



    void oap_print_msg(String str)
    {
        // TODO get current time and add to printed string
        //long int ms = tp.tv_sec * 1000 + tp.tv_usec / 1000;

        Log.d(TAG, str);

        // TODO writing to log
        /*
        if(logfile_name && fd_log)
        {
            sprintf(tmp, "%li: %s\n", ms, str);
            if(write(fd_log, tmp, strlen(tmp))<0)
            {
                if(baudrate()>0)
                {
                    getyx(cw, y, x);
                    if(x!=0) printw("\n");
                    printw("ERROR: cannot write to log file\n");

                }
                else
                {
                    printf("ERROR: cannot write to log file\n");
                    fflush(stdout);
                }

            }
        }
         */
    }


    String oap_hex_to_str(byte buf[], int len)
    {
        int j;
        String str=new String();
        //printw("LEN:(%d)",len);
        for(j=0;j<len;j++)
        {
            str+=String.format(" %02X", buf[j]);
        }
        return str;
    }




    int oap_print_podmsg(byte msg[])
    {
        int j, len=( msg[2] & 0xff );
        String tmp="";

        if(len>0) tmp+=String.format("|  %02X  ", msg[3]); else tmp+="|      ";
        if(len>1) tmp+=String.format("|  %02X ", msg[4]); else tmp+="|      ";
        if(len>2) tmp+=String.format("%02X  ", msg[5]); else tmp+="    ";
        tmp+="|  ";
        // 3 bytes are for mode and command length
        for(j=6;j<len+6-3;j++)
        {
            tmp+=String.format("%02X ", msg[j]);
        }
        tmp+=" ";
        if(len>3)
        {
            byte tmpmsg[]=new byte[len-3];
            for(j=0;j<len-3;j++)
            {
                tmpmsg[j]=msg[j+6];
            }
            String rawstr=new String(tmpmsg);
            tmp+="[" + rawstr + "]";
        }

        oap_print_msg(tmp);

        return 0;
    }


}