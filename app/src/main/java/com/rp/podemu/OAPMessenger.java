/**

 OAPMessenger.class is class that implements "30 pin" serial protocol
 for iPod. It is based on the protocol description available here:
 http://www.adriangame.co.uk/ipod-acc-pro.html

 Copyright (C) 2017, Roman P., dev.roman [at] gmail

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


import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Message;

public class OAPMessenger
{
    private static final int READ = 1;
    private static final int WRITE = 2;
    public static final int IPOD_MODE_DISCONNECTED = 0x00;
    public static final int IPOD_MODE_UNKNOWN = 0x00;
    public static final int IPOD_MODE_SIMPLE = 0x02;
    public static final int IPOD_MODE_AIR = 0x04;
    public static final byte IPOD_SUCCESS = 0x00;
    public static final byte IPOD_ERROR_DB_CATEGORY = 0x01;
    public static final byte IPOD_ERROR_CMD_FAILED = 0x02;
    public static final byte IPOD_ERROR_OUT_OF_RESOURCES = 0x03;
    public static final byte IPOD_ERROR_OUT_OF_RANGE = 0x04;
    public static final byte IPOD_ERROR_UNKOWN_ID = 0x05;

    private static int prev_polling_position = -1;
    private static Handler mHandler;
    private PodEmuMediaStore podEmuMediaStore;
    private MediaPlayback mediaPlayback;
    private int forceSimpleMode;

    private int line_cmd_pos = 0;
    private int line_cmd_len = 0;
    private boolean is_extended_image = false;
    private int line_ext_pos = 2;
    private byte line_buf[] = new byte[6503]; // max should be 259 bytes
    private byte ext_image_buf[] = new byte[5];

    private byte protoVersionMajor=0x01;
    private byte protoVersionMinor=0x0E;

    // bitmap to store dock image
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private static int pixel_format = 0;
    private static int image_start_x = 0;
    private static int image_start_y = 0;
    private static int image_pos_x = 0;
    private static int image_pos_y = 0;
    private static int image_res_x = 0;
    private static int image_res_y = 0;
    private static int image_bytes_per_line = 0;
    private static int current_byte_in_line=0;

    // processing action mechanism variables
    private int responsePendingCmd;
    private int responsePendingStatus=0;
    private long responsePendingSince;


    // definition of the in/out line if more than one instance of object created
    private static int line = 0;


    void setHandler(Handler handler)
    {
        this.mHandler = handler;

        // if this function was called, then the service was just bound
        // it means we need to communicate current iPod mode
        oap_communicate_ipod_connected();
    }

    public boolean getPendingResponseStatus()
    {
        return (responsePendingStatus>0);
    }

    public int getPendingResponse()
    {
        return responsePendingCmd;
    }

    public long getPendingResponseSince()
    {
        return responsePendingSince;
    }

    public void setPendingResponse(int cmd, int count)
    {
        long currTimeMillis=System.currentTimeMillis();

        // first, if another command is pending we need to send respond with FAIL
        if(responsePendingStatus>0) abortPendingResponse("new command received");

        responsePendingCmd=cmd;
        responsePendingStatus=count;
        responsePendingSince=currTimeMillis;

        PodEmuLog.debug("OAPM: PENDING_RESPONSE command set to " + String.format("0x%04X", cmd) + " (status: "+responsePendingStatus+")" );

    }


    public void abortPendingResponse(String reason)
    {
        PodEmuLog.debug("OAPM: aborting PENDING_RESPONSE for command " + String.format("0x%04X", responsePendingCmd) + " (status: "+responsePendingStatus+")" );
        if(responsePendingStatus>0)
        {
            responsePendingStatus = 1;
            respondPendingResponse(reason, IPOD_SUCCESS);
        }
    }

    public void respondPendingResponse(String reason, byte status)
    {
        if (responsePendingStatus > 0)
        {
            responsePendingStatus--;

            if (responsePendingStatus == 0)
            {
                oap_04_write_return_code(responsePendingCmd, status);
                PodEmuLog.debug("OAPM: PENDING_RESPONSE responded to command " + String.format("0x%04X", responsePendingCmd) + ". Reason: " + reason + ". Status: " + status);
            }
        }
    }

    void setMediaStore(PodEmuMediaStore podEmuMediaStore)
    {
        this.podEmuMediaStore = podEmuMediaStore;
    }

    void setMediaPlayback(MediaPlayback mediaPlayback)
    {
        this.mediaPlayback = mediaPlayback;
    }


    /**
     * The variables below represents the "iPod state" that is reported
     * to the dock station by variable commands
     */
    private static String ipod_name = "Android PodEmu";
    /**
     * iPOD modes
     * 0x00 - Mode Switching
     * 0x01 - Voice Recorder (not supported)
     * 0x02 - Simple Remote
     * 0x03 - Request Mode Status
     * 0x04 - Advanced Remote Mode (AiR)
     */
    private int ipod_mode = IPOD_MODE_UNKNOWN;
    private byte audiobook_speed = 0x00;
    private boolean polling_mode = false;
    private byte shuffle_mode = 0x00;
    private byte repeat_mode = 0x00;
    //private static PodEmuMessage currentlyPlaying1 = new PodEmuMessage();

    OAPMessenger()
    {
        mediaPlayback=MediaPlayback.getInstance();
        line++;

    }

    /**
     * Function receives one byte from input and interprets it.
     *
     * @param b - byte to be received
     * @return 0 - byte received but msg in progress
     * 1 - full message received
     * -1 - log occured
     */
    public synchronized int oap_receive_byte(byte b)
    {
        oap_print_char(READ, b, line_cmd_pos);

        if (line_cmd_pos == 2)
        {
        /*
         * Byte nr 2 is msg length (only in case of normal messages)
         * Next line calculates total msg length to be received by adding
         * additional bytes:
         *     2 for control bytes (0xFF 0x55)
         *     1 for msg len
         *     (already included) 1 for msg mode
         *     (already included) 2 for command
         *     (already included) parameters
         *     1 at the end for checksum
         */
            line_cmd_len = (b & 0xff) + 4;

            PodEmuLog.debug(String.format("OAPM: Line " + line + ": MSG LEN: %d TOTAL LEN: %d", (b & 0xff), line_cmd_len));

            if (b == (byte) 0x00)
            {
                /*
                 * this is potential extended image message candidate
                 * extended image message has 3 bytes allocated to message length
                 * instead of one byte. Thanks to that total message length could
                 * be more than 259 bytes. Byte 2 is treated as indicator of potential
                 * ext image msg. Length bytes are assumed to be 3 and 4
                 *
                 * Due to BLE speed limitation we will not recognise extended messages
                 * when connected using BLE.
                 */
                if(!(SerialInterfaceBuilder.getSerialInterface() instanceof SerialInterface_BLE))
                    is_extended_image = true;
                line_ext_pos = 2;
            }

        }

        if (is_extended_image)
        {
            if (line_ext_pos <= 8)
                PodEmuLog.debugVerbose(String.format("OAPM: EXT MSG check - pos:%d, byte: 0x%02X", line_ext_pos, b));

            if (line_ext_pos == 3 || line_ext_pos == 4)
            {
                // remember bytes 3 and 4 in case it turn out to be ext image
                ext_image_buf[line_ext_pos] = b;
            }
            // if byte 5 is not 0x04 then it is not ext image
            else if (line_ext_pos == 5 && b != (byte) 0x04) is_extended_image = false;
                // if byte 6 is not 0x00 then it is not ext image
            else if (line_ext_pos == 6 && b != (byte) 0x00) is_extended_image = false;
                // if byte 7 is not 0x32 then it is not ext image
            else if (line_ext_pos == 7 && b != (byte) 0x32) is_extended_image = false;
                // extended image message detected
            else if (line_ext_pos == 8)
            {
                // assumptions: length is in bytes 3 and 4 which gives maximum length of 65025
                (line_cmd_len) = (((ext_image_buf[3] & 0xff) << 8) | (ext_image_buf[4] & 0xff)) + 6;
                (line_cmd_pos) = 8;
                // now putting the bytes in their "correct" place
                line_buf[0] = (byte) 0xff;
                line_buf[1] = (byte) 0x55;
                line_buf[2] = (byte) 0x00;
                line_buf[3] = ext_image_buf[3];
                line_buf[4] = ext_image_buf[4];
                line_buf[5] = (byte) 0x04;
                line_buf[6] = (byte) 0x00;
                line_buf[7] = (byte) 0x32;

                PodEmuLog.debug(String.format("OAPM: Line %d: Extended image message detected!!!", line));
                // from now and on message will be treated normally
            }

            line_ext_pos++;
        }


        if (line_cmd_pos == 0 && b != (byte) 0xff)
        {
            PodEmuLog.debug("OAPM: Line " + line + ": ERROR: first byte is not 0xFF. Received 0x" + String.format("%02X", b));
            return -1;
        }

        if (line_cmd_pos == 1 && b != (byte) 0x55)
        {
            PodEmuLog.debug("OAPM: Line " + line + ": ERROR: second byte is not 0x55. Received 0x" + String.format("%02X", b));
            return -1;
        }

        if (!is_extended_image && line_cmd_len > 259)
        {
            PodEmuLog.debug("OAPM: Line " + line + ": ERROR: message length cannot exceed 259 bytes");
            return -1;
        }

        if (is_extended_image && line_cmd_len > 65025 + 6)
        {
            PodEmuLog.debug("OAPM: Line " + line + ": ERROR: extended message length cannot exceed 65031 bytes");
            return -1;
        }

        line_buf[line_cmd_pos] = b;

        line_cmd_pos++;

        if (line_cmd_pos == line_cmd_len)
        {
            int msg_len;
            byte checksum;

            // next 2 lines are just for logging
            PodEmuLog.debug("OAPM: Line " + line + ": RAW MSG  IN: " + oap_hex_to_str(line_buf, line_cmd_len));
            oap_print_podmsg(line_buf, true, (line_cmd_len > 7 && is_extended_image));

            checksum = oap_calc_checksum(line_buf, line_cmd_len);
            if (line_buf[line_cmd_len - 1] != checksum)
            {
                PodEmuLog.error("OAPM: Line " + line + String.format(": ERROR: checksum error. Received: %02X  Should be: %02X", line_buf[line_cmd_len - 1], checksum));
                // if checksum is wrong iPOD should not respond with any command. Such message is simply treated as corrupted and is skipped.
            }
            else
            {
                // Received message is OK. We cen process it and prepare the reply
                PodEmuLog.debugVerbose("OAPM: Line " + line + String.format(": Checksum OK. Received: %02X  Should be: %02X", line_buf[line_cmd_len - 1], checksum));
                PodEmuLog.debugVerbose("OAPM: Line " + line + ": Checksum OK");

                oap_process_msg(line_buf, line_cmd_len, (line_cmd_len > 7 && is_extended_image));
            }

            msg_len = line_cmd_len;
            line_cmd_pos = 0;
            line_cmd_len = 0;
            if (line_ext_pos > 7) is_extended_image = false;

            return msg_len;
        }

        return 0;
    }


    /**
     * Once full message is received and checksum is verified, this method is called
     * to process the message. This method interprets the message, invokes all necessary actions
     * and finally generates proper reply to serial interface.
     *
     * @param line_buf     - byte buffer that contains the message
     * @param line_cmd_len - total length of the message contained in line_buf
     * @param is_ext       - indicator if message should be treated as extended image message
     */
    public void oap_process_msg(byte[] line_buf, int line_cmd_len, boolean is_ext)
    {
        int pos_shift = 0;
        int len;

        PodEmuLog.debugVerbose("OAPM: Processing message started");

        if (is_ext)
        {
            // for extended image message bytes are assumed to be in positions 3 and 4
            len = ((line_buf[3] & 0xff) << 8) | (line_buf[4] & 0xff);
            // if we are dealing with extended message then we need to count 2 additional b
            pos_shift += 2;
        }
        else
        {
            len = (line_buf[2] & 0xff);
        }

        int mode = (line_buf[3 + pos_shift] & 0xff);
        byte scmd1 = line_buf[4 + pos_shift];
        byte scmd2 = line_buf[5 + pos_shift];
        int cmd = (scmd1 & 0xff);

        // second byte for command should not necessary be sent
        if (line_cmd_len - 1 > 5 + pos_shift)
        {
            cmd = (cmd << 8) + (scmd2 & 0xff);
        }

        // preparing the parameters
        byte params[];
        if (line_cmd_len > 7 + pos_shift)
        {
            params = new byte[line_cmd_len - 7 - pos_shift];
            // copy params from buf to separate variable
            for (int i = 6; i < line_cmd_len - 1 - pos_shift; i++)
            {
                params[i - 6] = line_buf[i + pos_shift];
            }
        }
        else
        {
            // just for parser not to throw errors:
            params = new byte[0];
            //params[0]=0;
        }

        PodEmuLog.debugVerbose(String.format("OAPM: Received - Ext: %d, Len: %d, Mode: 0x%02X, CMD: 0x%04X", (is_ext ? 1 : 0), len, mode, cmd));

        if (mode == 0x00 && len>=2) // general mode
        {
            switch(scmd1)
            {
                case 0x01:
                {
                    if (cmd == 0x0104) // AiR mode requested
                    {
                        // ignore this request if force simple mode enabled
                        if(forceSimpleMode==1)
                        {
                            ipod_mode = IPOD_MODE_SIMPLE;
                            PodEmuLog.debug("OAPM: skipping AiR mode request as force simple mode is enabled");
                            oap_00_write_return_code(scmd1, IPOD_ERROR_CMD_FAILED);
                        }
                        else
                        {
                            ipod_mode = IPOD_MODE_AIR;
                            oap_communicate_ipod_connected();
                            PodEmuLog.debug("OAPM: setting AiR mode");
                        }
                    }
                    else if (cmd == 0x0102) // simple mode requested
                    {
                        ipod_mode = IPOD_MODE_SIMPLE;
                        oap_communicate_ipod_connected();
                        PodEmuLog.debug("OAPM: setting Simple mode");
                    }
                } break;
                case 0x03: // current mode requested
                {
                    PodEmuLog.debug("OAPM: received current mode request");
                    // writing current mode
                    byte msg[] = new byte[2];
                    msg[0] = 0x04;
                    msg[1] = (byte) ipod_mode;
                    oap_write_cmd(msg, 2, (byte) 0x00);
                    PodEmuLog.debug("OAPM: sent current mode");
                } break;
                case 0x05: // AiR mode requested
                {
                    PodEmuLog.debug("OAPM: received AiR mode request");
                    // force this request if force simple mode enabled
                    if(forceSimpleMode==1)
                    {
                        ipod_mode = IPOD_MODE_SIMPLE;
                        PodEmuLog.debug("OAPM: skipping AiR mode request as force simple mode is enabled");
                        oap_00_write_return_code(scmd1, IPOD_ERROR_CMD_FAILED);
                    }
                    else
                    {
                        PodEmuLog.debug("OAPM: setting AiR mode");
                        ipod_mode = IPOD_MODE_AIR;
                        oap_communicate_ipod_connected();
                        oap_00_write_return_code(scmd1, IPOD_SUCCESS);
                    }
                } break;
                case 0x06: // simple mode requested
                {
                    PodEmuLog.debug("OAPM: received Simple mode request");
                    ipod_mode = IPOD_MODE_SIMPLE;
                    oap_communicate_ipod_connected();
                    oap_00_write_return_code(scmd1, IPOD_SUCCESS);
                } break;
                case 0x07: // device name requested
                {
                    PodEmuLog.debug("OAPM: received device name request");
                    byte c[] = {0x08};
                    byte msg[]=oap_build_ipod_msg(c, ipod_name);
                    oap_write_cmd(msg, msg.length, (byte) 0x00);
                    PodEmuLog.debug("OAPM: sent device name");
                } break;
                case 0x09: // SW Version requested
                {
                    PodEmuLog.debug("OAPM: received SW version request");
                    byte msg[] = new byte[4];
                    msg[0] = 0x0a; // cmd
                    msg[1] = 0x01; // major version
                    msg[2] = 0x00; // minor version
                    msg[3] = 0x04; // revision
                    oap_write_cmd(msg, msg.length, (byte) 0x00);
                    PodEmuLog.debug("OAPM: sent SW version");
                } break;
                case 0x0B: // SerialNumber requested
                {
                    PodEmuLog.debug("OAPM: received Serial Number request");
                    byte c[] = {0x0C};
                    byte msg[]=oap_build_ipod_msg(c, "xPodEmu-076"); // don't ask me why :) just!
                    oap_write_cmd(msg, msg.length, (byte) 0x00);
                    PodEmuLog.debug("OAPM: sent Serial Number");
                } break;
                case 0x0D: // Model Num requested
                {
                    PodEmuLog.debug("OAPM: received model number request");
                    byte msg[] = {0x0E, 0x00, 0x17, 0x00, 0x0A, 0x4D, 0x42, 0x37, 0x35, 0x34, 0x00};
                    oap_write_cmd(msg, msg.length, (byte) 0x00);
                    PodEmuLog.debug("OAPM: sent model number");
                } break;
                case 0x0F: // protocol version requested for mode stored in scmd2
                {
                    PodEmuLog.debug("OAPM: received protocol version request");
                    if(len<3)
                    {
                        oap_00_write_return_code(scmd1, IPOD_ERROR_OUT_OF_RANGE);
                        break;
                    }
                    byte msg[] = {0x10, scmd2, protoVersionMajor, protoVersionMinor};
                    oap_write_cmd(msg, msg.length, (byte) 0x00);
                    PodEmuLog.debug("OAPM: sent protocol version");
                } break;
                case 0x13: // IdentifyDeviceLingoes
                {
                    PodEmuLog.debug("OAPM: received IdentifyDeviceLingoes request");
                    byte msg[] = new byte[3];
                    byte bl = line_buf[8 + pos_shift];

                    // if bit 2 is set then accessory can communicate in Simple Mode
                    if ((bl & 0x04) == 0x04)
                    {
                        ipod_mode = IPOD_MODE_SIMPLE;
                        PodEmuLog.debug("OAPM: accessory supports Simple Mode. Switching to Simple Mode.");
                    }

                    // if bit 4 is set then accessory can communicate in Extended Mode
                    if ((bl & 0x10) == 0x10)
                    {
                        if(forceSimpleMode == 1)
                        {
                            PodEmuLog.debug("OAPM: accessory supports Extended Mode. However, forceSimpleMode is enabled so staying in Simple Mode.");
                            oap_00_write_return_code(scmd1, IPOD_ERROR_CMD_FAILED);
                        }
                        else
                        {
                            ipod_mode = IPOD_MODE_AIR;
                            PodEmuLog.debug("OAPM: accessory supports Extended Mode. Switching to Extended Mode.");
                        }
                    }
                    msg[0] = 0x02; // ACK
                    msg[1] = 0x00; // Response lingo
                    msg[2] = 0x13; // Response command
                    oap_write_cmd(msg, msg.length, (byte) 0x00);
                    if(ipod_mode != IPOD_MODE_DISCONNECTED && ipod_mode != IPOD_MODE_UNKNOWN)
                    {
                        oap_communicate_ipod_connected();
                    }

                    // Real iPod will also reply with FF5503002700D6 and repeat it once after 5 seconds
                    msg[0] = 0x27;
                    msg[1] = 0x01;
                    oap_write_cmd(msg, 2, (byte) 0x00);

                } break;
                case 0x28: // RetDeviceInfo
                {
                    byte[] msg = new byte[3];
                    PodEmuLog.debug("OAPM: accessory info received" );
                    if(scmd2==0x01) // name received
                    {
                        SerialInterfaceBuilder.getSerialInterface().setAccessoryName(new String(params));
                        PodEmuLog.debug("OAPM: accessoryName received: " + new String(params));
                        oap_communicate_ipod_connected();
                    }
                    msg[0] = 0x02; // ACK
                    msg[1] = 0x00; // Response lingo
                    msg[2] = 0x28; // Response command
                    oap_write_cmd(msg, msg.length, (byte) 0x00);
                } break;
                default:
                {
                    oap_00_write_return_code(scmd1, IPOD_ERROR_CMD_FAILED);
                    PodEmuLog.debug(String.format("OAPM: ERROR: Mode switching: unrecognized command 0x%04X received", cmd));
                }
            }
        }


        if (mode == 0x02 && ipod_mode == 0x02) // simple mode
        {
            switch (cmd)
            {

                /*
                 * @cmd 0x00 0x01
                 * Play
                 */
                case 0x0001:
                    PodEmuLog.debug("OAPM: SIMPLE_MODE IN  - play");
                    MediaPlayback.getInstance().action_play();
                    break;

                /*
                 * @cmd 0x00 0x02
                 * Vol+
                 */


                /*
                 * @cmd 0x00 0x04
                 * Vol-
                 */


                /*
                 * @cmd 0x00 0x08
                 * Skip>
                 */
                case 0x0008:
                    PodEmuLog.debug("OAPM: SIMPLE_MODE IN  - next");
                    MediaPlayback.getInstance().action_next();
                    break;


                /*
                 * @cmd 0x00 0x10
                 * Skip<
                 */
                case 0x0010:
                    PodEmuLog.debug("OAPM: SIMPLE_MODE IN  - prev");
                    MediaPlayback.getInstance().action_prev();
                    break;


                /*
                 * @cmd 0x00 0x20
                 * Next Album
                 */


                /*
                 * @cmd 0x00 0x40
                 * Previous Album
                 */


                /*
                 * @cmd 0x00 0x80
                 * Stop
                 */
                case 0x0080:
                    PodEmuLog.debug("OAPM: SIMPLE_MODE IN  - stop");
                    MediaPlayback.getInstance().action_stop();
                    break;

                case 0x0000:
                {
                    /*
                     * @cmd 0x00 0x00
                     * Button Released
                     */
                    if (params.length == 0)
                    {
                        // do nothing
                    }


                    /*
                     * @cmd 0x00 0x00 0x01
                     * Play
                     */
                    else if (params.length == 1 && params[0] == (byte) 0x01)
                    {
                        PodEmuLog.debug("OAPM: SIMPLE_MODE IN  - play");
                        MediaPlayback.getInstance().action_play();
                    }


                    /*
                     * @cmd 0x00 0x00 0x02
                     * Pause
                     */
                    else if (params.length == 1 && params[0] == (byte) 0x02)
                    {
                        PodEmuLog.debug("OAPM: SIMPLE_MODE IN  - pause");
                        MediaPlayback.getInstance().action_pause();
                    }

                    /*
                     * @cmd 0x00 0x00 0x04
                     * Mute (toggle)
                     */


                    /*
                     * @cmd 0x00 0x00 0x20
                     * Next Playlist
                     */


                    /*
                     * @cmd 0x00 0x00 0x40
                     * Previous Playlist
                     */


                    /*
                     * @cmd 0x00 0x00 0x80
                     * Toggles Shuffle
                     */
                    else if (params.length == 1 && params[0] == (byte) 0x80)
                    {
                        PodEmuLog.debug("OAPM: SIMPLE_MODE IN  - toggle shuffle (NOT IMPLEMENTED)");
                        // TODO implement shuffle
                    }


                    /*
                     * @cmd 0x00 0x00 0x00 0x01
                     * Toggles Repeat
                     */
                    else if (params.length == 1 && params[0] == (byte) 0x00 && params[1] == (byte) 0x01)
                    {
                        PodEmuLog.debug("OAPM: SIMPLE_MODE IN  - toggle repeat (NOT IMPLEMENTED)");
                        // TODO implement repeat
                    }



                    /*
                     * @cmd 0x00 0x00 0x00 0x04
                     * Ipod Off
                     */


                    /*
                     * @cmd 0x00 0x00 0x00 0x08
                     * Ipod On
                     */


                    /*
                     * @cmd 0x00 0x00 0x00 0x40
                     * Menu Button
                     */


                    /*
                     * @cmd 0x00 0x00 0x00 0x80
                     * OK/Select
                     */


                    /*
                     * @cmd 0x00 0x00 0x00 0x00 0x01
                     * Scroll Up
                     */


                    /*
                     * @cmd 0x00 0x00 0x00 0x00 0x02
                     * Scroll Down
                     */
                }
                break;

            }

        }
        if (mode == 0x04 && ipod_mode == 0x04) // AiR mode
        {
            switch (cmd)
            {

                /*
                 * Engine: Playback
                 * @cmd: 0x0002
                 * @param None
                 * get chapter info
                 * should get response FF FF FF FF 00 00 00 00
                 */
                case 0x0002:
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - get chapter info");
                    oap_04_write_chapter_info();
                    break;


                /*
                 * Set current chapter
                 * @param int(4) - chapter index
                 */
                case 0x0004:
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - set chapter");
                    if (params.length < 4)
                    {
                        oap_04_write_return_code(cmd, IPOD_ERROR_OUT_OF_RANGE);
                        break;
                    }
                    // just sending ACK
                    oap_04_write_return_code(cmd, IPOD_SUCCESS);
                    break;

                /*
                 * get current chapter status
                 * @param int(4) - chapter index
                 */
                case 0x0005:
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - get current chapter status");
                    if (params.length < 4)
                    {
                        oap_04_write_return_code(cmd, IPOD_ERROR_OUT_OF_RANGE);
                        break;
                    }
                    oap_04_write_chapter_status();
                    break;

                /*
                 * Get chapter name
                 * @param int(4) - chapter index
                 */
                case 0x0007:
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - get chapter name (NOT IMPLEMENTED)");
                    // TODO response with 0x0008, only 1 parameter - string
                    break;

                /*
                 * Engine: NA
                 * @cmd: 0x00 0x09
                 * @param None
                 * get audiobook speed
                 */
                case 0x0009:
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - get audiobook speed");
                    oap_04_write_audiobook_speed();
                    break;

                /*
                 * Engine: NA
                 * @cmd 0x00 0x0b
                 * @param byte(1) Parameter is either 0x00 or 0x01.
                 * Set audiobook speed
                 * Get success/failure response
                 */
                case 0x000b:
                {
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - set audiobook speed");
                    if (params.length < 1)
                    {
                        oap_04_write_return_code(cmd, IPOD_ERROR_OUT_OF_RANGE);
                        break;
                    }
                    audiobook_speed = params[0];
                    oap_04_write_return_code(cmd, IPOD_SUCCESS);
                }
                break;

                /*
                 * Engine: playback
                 * @cmd 0x00 0x0c
                 * @param type(1)
                 *          0x00 - track capabilities and information
                 *          0x01 - podcast name
                 *          0x02 - release date, always gives response 02 00 00 00 00 00 00 00 00
                 *          0x03 - description, always gives response 03 00 00 00 00
                 *          0x04 - song lyrics, always gives response 04 00 00 00 00
                 *          0x05 - Genre
                 *          0x06 - Composer
                 *          0x07 - artwork count, gives response 07 and 16 bytes
                 * @param number(4) song number
                 * @param number(2) chapter index
                 * Returns some additional parameters for selected song,
                 * gives response 0x00 0x0d (type + string)
                 */
                case 0x000C:
                {
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - get track information");
                    if (params.length < 7)
                    {
                        oap_04_write_return_code(cmd, IPOD_ERROR_OUT_OF_RANGE);
                        break;
                    }
                    int song_nr = ((params[1] & 0xff) << 24) | ((params[2] & 0xff) << 16) | ((params[3] & 0xff) << 8) | (params[4] & 0xff);
                    oap_04_write_additional_info(params[0], song_nr);
                }
                break;

                /*
                 * @cmd 0x00 0x12
                 * @param None
                 * get protocol version"
                 */
                case 0x0012:
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - get protocol version");
                    oap_04_write_protocol_version();
                    break;

                /*
                 * @cmd 0x00 0x14
                 * @param None
                 * Get iPod Name
                 */
                case 0x0014:
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - get iPod name");
                    oap_04_write_ipod_name();
                    break;

                /*
                 * Engine: DB
                 * @cmd 0x00 0x16
                 * @param None
                 * Switch to main library playlist (playlist 0)
                 */
                case 0x0016:
                {
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - switch to main library playlist");
                    podEmuMediaStore.selectionReset();

                    // just in case writing success retval
                    oap_04_write_return_code(cmd, IPOD_SUCCESS);
                }
                break;

                /*
                 * Engine: DB
                 * @cmd 0x00 0x17
                 * @param type(1)
                 *              0x01 - Playlist
                 *              0x02 - Artist
                 *              0x03 - Album
                 *              0x04 - Genre
                 *              0x05 - Song
                 *              0x06 - Composer
                 *              0x07 - Audiobook - not supported
                 *              0x08 - Podcast - not supported
                 * @param number(4)
                 * Switch to item identified by number and type given.
                 * 0xffffffff seems to be the beginning
                 */
                case 0x0017:
                {
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - switch to item identified by number and type");
                    if (params.length < 5)
                    {
                        oap_04_write_return_code(cmd, IPOD_ERROR_OUT_OF_RANGE);
                        break;
                    }

                    int pos=byte_array_to_int(params,1);
                    oap_04_make_db_selection(cmd, params[0], pos, (byte)0x05);
                }
                break;

                /*
                 * Engine: DB
                 * @cmd 0x00 0x18
                 * @param type(1)
                 *              0x01 - Playlist
                 *              0x02 - Artist
                 *              0x03 - Album
                 *              0x04 - Genre
                 *              0x05 - Song
                 *              0x06 - Composer
                 * get count of the given type (count of tracks etc)
                 */
                case 0x0018:
                {
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - get count of the given type");
                    if (params.length < 1)
                    {
                        oap_04_write_return_code(cmd, IPOD_ERROR_OUT_OF_RANGE);
                        break;
                    }

                    int count=PodEmuMediaStore.getInstance().selectionInitializeDB(params[0]);
                    if(count == -1)
                    {
                        oap_04_write_return_code(cmd, IPOD_ERROR_CMD_FAILED);
                    }
                    else
                    {
                        byte b[]={0,0,0,1};
                        int_to_byte_array(count, b);
                        byte msg[] = {0x00, 0x19, b[0], b[1], b[2], b[3]};
                        oap_04_write_cmd(msg);
                        PodEmuLog.debug("OAPM: AIR_MODE OUT  - response to 0x0018, count=" + count);
                    }
                }
                break;

                /*
                 * Engine: DB
                 * @cmd 0x00 0x1A
                 * @param type(1)
                 *              0x01 - Playlist
                 *              0x02 - Artist
                 *              0x03 - Album
                 *              0x04 - Genre
                 *              0x05 - Song
                 *              0x06 - Composer
                 * @param number(4) - position to start from
                 * @param number(4) - number of records to retrieve. 0xFFFFFFFF to retrieve all
                 * Get names for range of items. First number is starting item offset
                 * (0 is first item) and second number is how many. One response
                 * message 0x00 0x1B for each item.
                 */
                case 0x001A:
                {
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - get names for range of items");
                    if (params.length < 9)
                    {
                        oap_04_write_return_code(cmd, IPOD_ERROR_OUT_OF_RANGE);
                        break;
                    }
                    int pos_start = ((params[1] & 0xff) << 24) | ((params[2] & 0xff) << 16) | ((params[3] & 0xff) << 8) | (params[4] & 0xff);
                    int count = ((params[5] & 0xff) << 24) | ((params[6] & 0xff) << 16) | ((params[7] & 0xff) << 8) | (params[8] & 0xff);
                    oap_04_write_type_names(params[0], pos_start, count);
                }
                break;

                /*
                 * Engine: Playback
                 * @cmd 0x00 0x1C
                 * @param none
                 * Get time and status info
                 */
                case 0x001C:
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - get time and status info");
                    oap_04_write_info();
                    break;

                /*
                 * Engine: Playback
                 * @cmd 0x00 0x1E
                 * @param none
                 * Get current position in playlist
                */
                case 0x001E:
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - get current position in playlist");
                    oap_04_write_playlist_position();
                    break;

                /*
                 * Engine: Playback
                 * @cmd 0x00 0x20
                 * @param number(4)
                 * Get title of a song number
                 */
                case 0x0020:
                {
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - get title of a song number");
                    if (params.length < 4)
                    {
                        oap_04_write_return_code(cmd, IPOD_ERROR_OUT_OF_RANGE);
                        break;
                    }
                    int song_number = byte_array_to_int(params);
                    oap_04_write_title(song_number);
                }
                break;

                /*
                 * Engine: Playback
                 * @cmd 0x00 0x22
                 * @param number(4)
                 * Get artist of a song number
                 */
                case 0x0022:
                {
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - get artist of a song number");
                    if (params.length < 4)
                    {
                        oap_04_write_return_code(cmd, IPOD_ERROR_OUT_OF_RANGE);
                        break;
                    }
                    int song_number = byte_array_to_int(params);
                    oap_04_write_artist(song_number);
                }
                break;

                /*
                 * Engine: Playback
                 * @cmd 0x00 0x24
                 * @param number(4)
                 * Get album of a song number
                 */
                case 0x0024:
                {
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - get album of a song number");
                    if (params.length < 4)
                    {
                        oap_04_write_return_code(cmd, IPOD_ERROR_OUT_OF_RANGE);
                        break;
                    }
                    int song_number = byte_array_to_int(params);
                    oap_04_write_album(song_number);
                }
                break;

                /*
                 * Engine: Playback
                 * @cmd 0x00 0x26
                 * @param pollingmode(1) Polling Mode:
                 *                       0x01 = Start
                 *                       0x00 = Stop
                 * Polling causes the return command 0x00 0x27 to be sent every 500 milliseconds.
                 */
                case 0x0026:
                {
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - " + (params[0] == 0x01? "enable":"disable") + " polling mode");
                    if (params.length < 1)
                    {
                        oap_04_write_return_code(cmd, IPOD_ERROR_OUT_OF_RANGE);
                        break;
                    }
                    enable_polling_mode(params[0] != 0);
                }
                break;

                /*
                 * Engine: DB & Playback
                 * @cmd 0x00 0x28
                 * @param number(4)
                 * Execute playlist and jump to specified song number.
                 * This command copies selected items from DB to playback playlist
                 * 0xFFFFFFFF will always be start of playlist even when shuffle is on.
                 */
                case 0x0028:
                {
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - execute playlist and jump to specified song number");
                    if (params.length < 4)
                    {
                        oap_04_write_return_code(cmd, IPOD_ERROR_OUT_OF_RANGE);
                        break;
                    }

                    int pos=byte_array_to_int(params);
                    //int currentPos = mediaPlayback.getCurrentPlaylist().getCurrentTrackPos();
                    if( pos == 0xFFFFFFFF) pos=0;

                    mediaPlayback.setCurrentPlaylist(podEmuMediaStore.selectionBuildPlaylist());
                    mediaPlayback.getCurrentPlaylist().setCurrentTrackPosToStart();

                    /*
                     * Do not immediately respond with SUCCESS. Wait for broadcast instead.
                     */
                    int count = mediaPlayback.calcTrackCountFromPosition(pos);

                    if(count!=0)
                    {
                        // if count is 0, then no jump is required and we can just reply with success
                        oap_04_write_return_code(cmd, IPOD_SUCCESS);
                    }
                    else
                    {
                        setPendingResponse(cmd, Math.abs(count));
                    }

                    if(!mediaPlayback.jumpTrackCount(count))
                        respondPendingResponse("mediaPlayback.jumpTo failed", IPOD_ERROR_CMD_FAILED);

                    break;
                }

                /*
                 * Engine: Playback
                 * @cmd 0x00 0x29
                 * @param command(1)  AiR Playback Control:
                 *                      0x01 = Play/Pause
                 *                      0x02 = Stop
                 *                      0x03 = Skip++
                 *                      0x04 = Skip--
                 *                      0x05 = FFwd
                 *                      0x06 = FRwd
                 *                      0x07 = StopFF/RW
                 */
                case 0x0029:
                {
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - simple playback control");
                    if (params.length < 1)
                    {
                        oap_04_write_return_code(cmd, IPOD_ERROR_OUT_OF_RANGE);
                        break;
                    }

                    byte retval = IPOD_SUCCESS;
                    setPendingResponse(cmd, 1);
                    switch (params[0])
                    {
                        case 0x01:
                            PodEmuLog.debug("OAPM: AIR_MODE IN  - play/pause");
                            MediaPlayback.getInstance().action_play_pause();
                            break;
                        case 0x02:
                            PodEmuLog.debug("OAPM: AIR_MODE IN  - stop");
                            MediaPlayback.getInstance().action_stop();
                            break;
                        case 0x03:
                            PodEmuLog.debug("OAPM: AIR_MODE IN  - next");
                            MediaPlayback.getInstance().action_next();
                            break;
                        case 0x04:
                            PodEmuLog.debug("OAPM: AIR_MODE IN  - prev");
                            MediaPlayback.getInstance().action_prev();
                            break;
                        case 0x05:
                            PodEmuLog.debug("OAPM: AIR_MODE IN  - skip forward");
                            MediaPlayback.getInstance().action_skip_forward();
                            break;
                        case 0x06:
                            PodEmuLog.debug("OAPM: AIR_MODE IN  - skip backward");
                            MediaPlayback.getInstance().action_skip_backward();
                            break;
                        case 0x07: // end skip FF/REV
                            PodEmuLog.debug("OAPM: AIR_MODE IN  - end skip FF/REV");
                            MediaPlayback.getInstance().action_stop_ff_rev();
                            break;
                        case 0x08: // next chapter
                            PodEmuLog.debug("OAPM: AIR_MODE IN  - next chapter (NOT IMPLEMENTED)");
                            respondPendingResponse("action not implemented", IPOD_SUCCESS);
                            break;
                        case 0x09: // prev chapter
                            PodEmuLog.debug("OAPM: AIR_MODE IN  - previous chapter (NOT IMPLEMENTED)");
                            respondPendingResponse("action not implemented", IPOD_SUCCESS);
                            break;
                        default:
                            retval = IPOD_ERROR_CMD_FAILED;
                            PodEmuLog.debug("OAPM: AIR_MODE IN  - ERROR: unknown command");
                    }
                    if (retval != IPOD_SUCCESS)
                    {
                        respondPendingResponse("0x0029 failed", retval);
                    }
                    //oap_04_write_return_code(cmd, retval);
                    break;
                }

                /*
                 * @cmd 0x00 0x2C
                 * @param none
                 * Get shuffle mode
                 */
                case 0x002C:
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - get shuffle mode");
                    oap_04_write_shuffle_mode();
                    break;

                /*
                 * @cmd 0x00 0x2E
                 * @param shuffle(1) Sets the shuffle mode:
                 *                      0x00 = Off
                 *                      0x01 = Songs
                 *                      0x02 = Albums
                 */
                case 0x002E:
                {
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - set shuffle mode (NOT IMPLEMENTED)");
                    if (params.length < 1 || params[0] < 0x00 || params[0] > 0x02)
                    {
                        oap_04_write_return_code(cmd, IPOD_ERROR_OUT_OF_RANGE);
                        break;
                    }

                    // TODO implement shuffle
                    shuffle_mode = params[0];
                    oap_04_write_return_code(cmd, IPOD_SUCCESS);
                }
                break;

                /*
                 * @cmd 0x00 0x2F
                 * @param none
                 * Get Repeat Mode
                 */
                case 0x002F:
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - get repeat mode");
                    oap_04_write_repeat_mode();
                    break;

                /*
                 * @cmd 0x00 0x31
                 * @param repeatmode(1) Sets the repeat mode:
                 *                          0x00 = Off
                 *                          0x01 = Songs
                 *                          0x02 = Albums
                 */
                case 0x0031:
                {
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - set repeat mode (NOT IMPLEMENTED)");
                    if (params.length < 1 || params[0] < 0x00 || params[0] > 0x02)
                    {
                        oap_04_write_return_code(cmd, IPOD_ERROR_OUT_OF_RANGE);
                        break;
                    }

                    // TODO implement repeat mode
                    repeat_mode = params[0];
                    oap_04_write_return_code(cmd, IPOD_SUCCESS);
                }
                break;

                /*
                 * @cmd 0x00 0x32
                 * @param Picture Blocks
                 * upload a picture (see below)
                 */
                case 0x0032:
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - upload bitmap");
                    oap_receive_image_msg(params, len - 3);
                    break;

                /*
                 * @cmd 0x00 0x33
                 * @param none
                 * Get max screen size for picture upload
                 */
                case 0x0033:
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - get max screen size for the bitmap upload");
                    oap_04_write_screen_resolution_34();
                    break;

                /*
                 * Engine: Playback
                 * @cmd 0x00 0x35
                 * @param none
                 * Get number of songs in playlist
                 */
                case 0x0035:
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - get number of songs in the playlist");
                    oap_04_write_playlist_song_count();
                    break;

                /*
                 * Engine: Playback
                 * @cmd 0x00 0x37
                 * @param number(4) jump to specified song number in playlist
                 */
                case 0x0037:
                {
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - jump to a specified song number in the playlist");
                    if (params.length < 4)
                    {
                        oap_04_write_return_code(cmd, IPOD_ERROR_OUT_OF_RANGE);
                        break;
                    }

                    int count = mediaPlayback.calcTrackCountFromPosition(byte_array_to_int(params));
                    setPendingResponse(cmd, Math.abs(count));
                    if(!mediaPlayback.jumpTrackCount(count))
                        respondPendingResponse("mediaPlayback.jumpTo failed", IPOD_ERROR_CMD_FAILED);

                }
                break;

                /*
                 * Engine: DB
                 * @cmd 0x00 0x38 - action - get success response
                 * @param type1(1) - category type to select
                 *          0x01 - Playlist
                 *          0x02 - Artist
                 *          0x03 - Album
                 *          0x04 - Genre
                 *          0x05 - Track
                 *          0x06 - Composer
                 * @param number(4) - category item number
                 * @param type2(1) - sort type:
                 *                  0x00 - genre
                 *                  0x01 - artist
                 *                  0x02 - composer
                 *                  0x03 - album
                 *                  0x04 - name
                 *                  0x05 - playlist
                 *                  0x06 - release date
                 *                  0xFF - default sort type
                 * Select DB records (type1) and sort by (type2)
                 * Subsequent commands work on already existing selection (are added, not overwritten)
                 */
                case 0x0038:
                {
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - select DB records (type1) and sort by (type2)");
                    if (params.length < 6)
                    {
                        oap_04_write_return_code(cmd, IPOD_ERROR_OUT_OF_RANGE);
                        break;
                    }

                    int pos=byte_array_to_int(params,1);
                    oap_04_make_db_selection(cmd, params[0], pos, params[5]);

                }
                break;

                /*
                 * @cmd 0x00 0x39 - response is 3A
                 * @param NO PARAMS
           2      * Get color display max resolution
                 */
                case 0x0039:
                    PodEmuLog.debug("OAPM: AIR_MODE IN  - get color display max resolution");
                    oap_04_write_screen_resolution_3A();
                    break;
            }

        }

    }


    /**
     * @cmd 0x00 0x00
     * @response Result(1)
     * @response Command(2)     NCU, not often used.
     */

    /**
     * Engine: NA
     * @param cmd    - command to be parsed as 2 bytes
     * @param result - result to be posted
     * @cmd 0x0001
     * @response Result(1)
     * 0x00 = Success
     * 0x02 = Failure
     * 0x04 = Exceeded limit/Byte Count Wrong
     * 0x05 = Is a Response Not a Command
     * @response Command(2)
     * The command code this is responding to.
     * Feedback on command just executed.
     */
    private void oap_04_write_return_code(int cmd, byte result)
    {
        byte msg[] = {0x00, 0x01, result, 0, 0};
        msg[3] = (byte) ((cmd >> 8) & 0xff);
        msg[4] = (byte) (cmd & 0xff);
        oap_04_write_cmd(msg);

        String status="UNKNOWN_ERR";
        if(result==IPOD_SUCCESS) status="SUCCESS";
        if(result==IPOD_ERROR_OUT_OF_RESOURCES) status="ERROR_OUT_OF_RESOURCES";
        if(result==IPOD_ERROR_CMD_FAILED) status="ERROR_CMD_FAILED";
        if(result==IPOD_ERROR_OUT_OF_RANGE) status="ERROR_OUT_OF_RANGE";
        if(result==IPOD_ERROR_DB_CATEGORY) status="ERROR_DB_CATEGORY";
        if(result==IPOD_ERROR_UNKOWN_ID) status="ERROR_UNKOWN_ID";
        PodEmuLog.debug("OAPM: AIR_MODE OUT - response to command " + String.format("0x%04X", cmd) + ". Status: " + status);
    }

    /**
     * @param cmd    - command to be parsed as 2 bytes
     * @param result - result to be posted
     * @cmd 0x02 (for general mode 0x00)
     * @response Result(1)
     * 0x00 = Success
     * 0x02 = Failure
     * 0x04 = Exceeded limit/Byte Count Wrong
     * 0x05 = Is a Response Not a Command
     * @response Command(2)
     * The command code this is responding to.
     * Feedback on command just executed.
     */
    private void oap_00_write_return_code(byte cmd, byte result)
    {
        byte msg[] = {0x02, result, cmd};
        oap_write_cmd(msg, msg.length, (byte) 0x00);

        String status="UNKNOWN_ERR";
        if(result==IPOD_SUCCESS) status="SUCCESS";
        if(result==IPOD_ERROR_OUT_OF_RESOURCES) status="ERROR_OUT_OF_RESOURCES";
        if(result==IPOD_ERROR_CMD_FAILED) status="ERROR_CMD_FAILED";
        if(result==IPOD_ERROR_OUT_OF_RESOURCES) status="ERROR_OUT_OF_RESOURCES";
        if(result==IPOD_ERROR_DB_CATEGORY) status="ERROR_DB_CATEGORY";
        if(result==IPOD_ERROR_UNKOWN_ID) status="ERROR_UNKOWN_ID";
        PodEmuLog.debug("OAPM: 00 OUT - response to command " + String.format("0x%04X", cmd) + ". Status: " + status);
    }

    /**
     * Engine: playback
     * @cmd 0x0003
     * @response int(4) - chapter index (0xffffffff if no chapters)
     * @response int(4) - chapter count
     * 0xFF 0xFF 0xFF 0xFF 0x00 0x00 0x00 0x00
     */
    private void oap_04_write_chapter_info()
    {
        byte msg[] = {0x00, 0x03, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x00, 0x00, 0x00, 0x00};
        oap_04_write_cmd(msg);
        PodEmuLog.debug("OAPM: AIR_MODE OUT - written chapter info (GENERIC)");
    }

    /**
     * Engine: playback
     * @cmd 0x0006
     * @response int(4) - chapter length in millis
     * @response int(4) - elapsed time in millis
     */
    private void oap_04_write_chapter_status()
    {
        byte msg[] = {0x00, 0x06, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        oap_04_write_cmd(msg);
        PodEmuLog.debug("OAPM: AIR_MODE OUT - written chapter status (GENERIC)");
    }

    /**
     * Engine: NA
     * @cmd 0x000a
     * @response byte(1) - audiobook speed:
     *                      0xff - slow
     *                      0x00 - normal
     *                      0x01 - fast
     */
    private void oap_04_write_audiobook_speed()
    {
        byte msg[] = {0x00, 0x0A, audiobook_speed};
        oap_04_write_cmd(msg);
        PodEmuLog.debug("OAPM: AIR_MODE OUT - written audiobook speed (GENERIC)");
    }

    /**
     * FIXME
     * Engine: NA/Playback
     * @param rtype   - type requested as described above
     * @param song_nr - song number the request is related to
     * @cmd 0x000d
     * @response: type(1):
     *      0x00 - track capabilities and information
     *      0x01 - podcast name
     *      0x02 - track release date
     *      0x03 - track description
     *      0x04 - track song lyrics
     *      0x05 - Genre
     *      0x06 - Composer
     *      0x07 - artwork count, gives response 07 and 16 bytes
     * @response: byte(n) - content, depending on type
     *
     * Write additional information about selected song
     */
    private void oap_04_write_additional_info(byte rtype, int song_nr)
    {
        byte msg[] = {0x00, 0x0d, rtype};
        byte msg02[] = {0x00, 0x0d, rtype, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        int playlist_size = mediaPlayback.getCurrentPlaylist().getTrackCount();
        if(song_nr<0 || song_nr >= playlist_size)
        {
            PodEmuLog.error("OAPM: track number " + song_nr + " is out of boundaries. Playlist size is " + playlist_size + ".");
            oap_04_write_return_code(0x000c, IPOD_ERROR_OUT_OF_RANGE);
            return;
        }

        switch (rtype)
        {
            case 0x00: // Track Capabilities and Information
                int length = mediaPlayback.getCurrentPlaylist().getTrack(song_nr).length;
                msg02[7] = (byte) ((length >> 24) & 0xff);
                msg02[8] = (byte) ((length >> 16) & 0xff);
                msg02[9] = (byte) ((length >> 8) & 0xff);
                msg02[10] = (byte) ((length) & 0xff);
                oap_04_write_cmd(msg02, 13);
                PodEmuLog.debug("OAPM: AIR_MODE OUT - written track " + song_nr + " capabilities and information (GENERIC)");
                break;

            case 0x01: // podcast name
                oap_04_write_string(msg, "");
                PodEmuLog.debug("OAPM: AIR_MODE OUT - written track " + song_nr + " podcast name (empty string)");
                break;

            case 0x02: // track release date
                oap_04_write_cmd(msg02, 11);
                PodEmuLog.debug("OAPM: AIR_MODE OUT - written track " + song_nr + " release date (GENERIC)");
                break;

            case 0x03: // track description
                oap_04_write_string(msg, "");
                PodEmuLog.debug("OAPM: AIR_MODE OUT - written track " + song_nr + " description (empty string)");
                break;
            case 0x04: // song lyrics
            {
                oap_04_write_string(msg, "");
                PodEmuLog.debug("OAPM: AIR_MODE OUT - written track " + song_nr + " lyrics (empty string)");
            }
            break;

            case 0x05:
            {
                String song_name = mediaPlayback.getCurrentPlaylist().getTrack(song_nr).genre;
                oap_04_write_string(msg, song_name);
                PodEmuLog.debug("OAPM: AIR_MODE OUT - written track " + song_nr + " genre: " + song_name);
            }
            break;

            case 0x06:
            {
                String composer = mediaPlayback.getCurrentPlaylist().getTrack(song_nr).composer;
                oap_04_write_string(msg, composer);
                PodEmuLog.debug("OAPM: AIR_MODE OUT - written track " + song_nr + " composer: " + composer);
            }
            break;

            default:
                oap_04_write_return_code(0x000c, IPOD_ERROR_CMD_FAILED);
        }
    }

    /**
     * Engine: NA
     * writes protocol version
     * @cmd 0x0013
     * @response param(2)
     * iPod 3G 30GB:       0x01 0x02
     * iPod 4G 30GB:       0x01 0x09
     * iPod 5G 30GB:       0x01 0x09
     * iPod Touch 4G 10G:  0x01 0x0E
     * iPod Nano 4G:       0x01 0x0E
     */
    private void oap_04_write_protocol_version()
    {
        byte msg[] = {0x00, 0x13, protoVersionMajor, protoVersionMinor};
        oap_04_write_cmd(msg);
        PodEmuLog.debug("OAPM: AIR_MODE OUT - written protocol version: " + protoVersionMajor + "." + protoVersionMinor);
    }

    /**
     * Engine: NA
     * @cmd 0x00 0x15
     * @response string    iPod name as null terminated string
     */
    private void oap_04_write_ipod_name()
    {
        byte cmd[] = {0x00, 0x15};
        oap_04_write_string(cmd, ipod_name);
        PodEmuLog.debug("OAPM: AIR_MODE OUT - written iPod name: " + ipod_name);
    }


    /**
     * Make db selection and sort the results
     * @param cmd - command to respond to
     * @param select_type - selection type
     *                  0x01 - Playlist
     *                  0x02 - Artist
     *                  0x03 - Album
     *                  0x04 - Genre
     *                  0x05 - Track
     *                  0x06 - Composer
     * @param pos - position of the record
     * @param sort_type - sort type:
     *              0x01 - Playlist
     *              0x02 - Artist
     *              0x03 - Album
     *              0x04 - Genre
     *              0x05 - Song
     *              0x06 - Composer
     *              0x07 - Audiobook - not supported
     *              0x08 - Podcast - not supported
     */
    private void oap_04_make_db_selection(int cmd, byte select_type, int pos, byte sort_type)
    {
        // if DB was not initialized then -> error
        if (podEmuMediaStore.rebuildSelectionQueryRequired)
        {
            PodEmuLog.error(String.format("PEMS: accessory is trying to make selection (%04X) before DB initialization (0x0018)", cmd));
            oap_04_write_return_code(cmd, IPOD_ERROR_CMD_FAILED);
            return;
        }


        int id=podEmuMediaStore.selectionMapPosToId(pos);

        if(id == -1)
        {
            oap_04_write_return_code(cmd, IPOD_ERROR_OUT_OF_RANGE);
        }
        else
        {

            switch (select_type)
            {
                case 0x01:
                    podEmuMediaStore.selectionSetPlaylist(id);
                    break;
                case 0x02:
                    podEmuMediaStore.selectionSetArtist(id);
                    break;
                case 0x03:
                    podEmuMediaStore.selectionSetAlbum(id);
                    break;
                case 0x04:
                    podEmuMediaStore.selectionSetGenre(id);
                    break;
                case 0x05:
                    podEmuMediaStore.selectionSetTrack(id);
                    break;
                case 0x06:
                    podEmuMediaStore.selectionSetComposer(id);
                    break;
            }

            podEmuMediaStore.setSelectionSortOrder(sort_type);
            oap_04_write_return_code(cmd, IPOD_SUCCESS);
        }

    }

    /**
     * Engine:
     * @param rtype     - type to which we are plying to
     * @param pos_start - starting position
     * @param count   - ending position
     * @cmd 0x00 0x1B
     * @response number(4) Number is the offset of item from 0.
     * @response string    String is the name of the item.
     * Playlist 0 is the main library and has the same name as iPod itself
     * (same as returned for 0x0014)
     */
    private void oap_04_write_type_names(int rtype, int pos_start, int count)
    {
        String str;
        byte cmd[] = {0x00, 0x1B, 0x00, 0x00, 0x00, 0x00};
        Cursor cursor=PodEmuMediaStore.getInstance().selectionExecute(rtype);
        int maxItemInPlaylist = cursor.getCount()-1;

        if (pos_start > maxItemInPlaylist  || pos_start + count - 1 > maxItemInPlaylist )
        {
            oap_04_write_return_code(0x001A, IPOD_ERROR_OUT_OF_RANGE);
            return;
        }
        cursor.moveToPosition(pos_start);

        int pos=pos_start;
        if(count == 0xFFFFFFFF) count = cursor.getCount();
        for(int i=0; i<count; i++, pos++)
        {
            str=cursor.getString(cursor.getColumnIndex(PodEmuMediaStore.DbHelper.COLUMN_NAME));

            cmd[2] = (byte) ((pos >> 24) & 0xff);
            cmd[3] = (byte) ((pos >> 16) & 0xff);
            cmd[4] = (byte) ((pos >> 8) & 0xff);
            cmd[5] = (byte) (pos & 0xff);

            oap_04_write_string(cmd, str);
            PodEmuLog.debug("OAPM: AIR_MODE OUT - written type name: " + str);
            cursor.moveToNext();
        }
    }

    /**
     * @cmd 0x00 0x1D
     * @response length(4)  Track length in milliseconds
     * @response time(4)    Elapsed time in milliseconds
     * @response status(1)  Status:
     * 0x00 = Stop
     * 0x01 = Playing
     * 0x02 = Paused
     */
    private void oap_04_write_info()
    {
        byte msg[] = new byte[11];
        int length = mediaPlayback.getCurrentPlaylist().getCurrentTrack().length;
        int time = mediaPlayback.getCurrentTrackPositionMS();
        msg[0] = 0x00;
        msg[1] = 0x1D;
        msg[2] = (byte) ((length >> 24) & 0xff);
        msg[3] = (byte) ((length >> 16) & 0xff);
        msg[4] = (byte) ((length >> 8) & 0xff);
        msg[5] = (byte) ((length) & 0xff);
        msg[6] = (byte) ((time >> 24) & 0xff);
        msg[7] = (byte) ((time >> 16) & 0xff);
        msg[8] = (byte) ((time >> 8) & 0xff);
        msg[9] = (byte) ((time) & 0xff);
        msg[10] = (byte) (mediaPlayback.isPlaying() ? 0x01 : 0x02);
        oap_04_write_cmd(msg);
        PodEmuLog.debug("OAPM: AIR_MODE OUT - written track info. Length: " + length + ". Position: " + time);
    }


    /**
     * @cmd 0x00 0x1F
     * @response position(4)    current position in playlist
     */
    private void oap_04_write_playlist_position()
    {
        byte b[]=new byte[4];
        int pos = MediaPlayback.getInstance().getCurrentPlaylist().getCurrentTrackPos();
        int_to_byte_array(pos, b);
        byte msg[] = {0x00, 0x1F, b[0], b[1], b[2], b[3]};
        oap_04_write_cmd(msg);
        PodEmuLog.debug("OAPM: AIR_MODE OUT - written playlist position: " + pos);
    }

    /**
     * @param song_number - song number to which we should provide information
     * @cmd 0x00 0x21
     * @response string     Title of song as null terminated string
     */
    private void oap_04_write_title(int song_number)
    {
        byte cmd[] = {0x00, 0x21};
        PodEmuMediaStore.Track track = mediaPlayback.getCurrentPlaylist().getTrack(song_number);

        if(track==null)
        {
            oap_04_write_return_code(0x0020, IPOD_ERROR_OUT_OF_RANGE);
        }
        else
        {
            oap_04_write_string(cmd,track.name);
            PodEmuLog.debug("OAPM: AIR_MODE OUT - written track title " + track.name);
        }
    }

    /**
     * @param song_number - song number to which we should provide information
     * @cmd 0x00 0x23
     * @response string     Artist returned as null terminated string
     */
    private void oap_04_write_artist(int song_number)
    {
        byte cmd[] = {0x00, 0x23};
        PodEmuMediaStore.Track track = mediaPlayback.getCurrentPlaylist().getTrack(song_number);

        if(track==null)
        {
            oap_04_write_return_code(0x0022, IPOD_ERROR_OUT_OF_RANGE);
        }
        else
        {
            oap_04_write_string(cmd, track.artist);
            PodEmuLog.debug("OAPM: AIR_MODE OUT - written track artist " + track.artist);
        }
    }


    /**
     * @param song_number - song number to which we should provide information
     * @cmd 0x00 0x25
     * @response string     Album returned as null terminated string
     */
    private void oap_04_write_album(int song_number)
    {
        byte cmd[] = {0x00, 0x25};
        PodEmuMediaStore.Track track = mediaPlayback.getCurrentPlaylist().getTrack(song_number);

        if(track==null)
        {
            oap_04_write_return_code(0x0024, IPOD_ERROR_OUT_OF_RANGE);
        }
        else
        {
            oap_04_write_string(cmd, track.album);
            PodEmuLog.debug("OAPM: AIR_MODE OUT - written track album " + track.album);
        }
    }


    /**
     * @cmd 0x00 0x27
     * @response number(4) time elapsed on current song
     */
    public void oap_04_write_polling_playback_stopped()
    {
        if(!polling_mode) return;

        byte cmd[] = {
                0x00,
                0x27,
                0x00, // playback stopped
        };
        oap_04_write_cmd(cmd);
        PodEmuLog.debug("OAPM: AIR_MODE OUT - polling message: playback stopped");
    }


    /**
     * @cmd 0x00 0x27
     * @param pos - new position in playlist
     * @response number(4) time elapsed on current song
     */
    public void oap_04_write_polling_track_status_changed(int pos)
    {
        if(!polling_mode) return;
        if(prev_polling_position==pos) return;
        prev_polling_position = pos;

        byte cmd[] = {
                0x00,
                0x27,
                0x01, // info new track index
                (byte) ((pos >> 24) & 0xff),
                (byte) ((pos >> 16) & 0xff),
                (byte) ((pos >> 8) & 0xff),
                (byte) (pos & 0xff)
        };
        oap_04_write_cmd(cmd);
        PodEmuLog.debug("OAPM: AIR_MODE OUT - polling message: track changed to " + pos);
    }


    /**
     * @cmd 0x00 0x27
     * @response number(4) time elapsed on current song
     */
    public void oap_04_write_polling_elapsed_time()
    {
        if(!polling_mode) return;

        if (!mediaPlayback.isPlaying()) return;

        int pos = mediaPlayback.getCurrentTrackPositionMS();
        byte cmd[] = {
                0x00,
                0x27,
                0x04, // info - new track position
                (byte) ((pos >> 24) & 0xff),
                (byte) ((pos >> 16) & 0xff),
                (byte) ((pos >> 8) & 0xff),
                (byte) (pos & 0xff)
        };
        oap_04_write_cmd(cmd);
        PodEmuLog.debug("OAPM: AIR_MODE OUT - polling message: track position changed to " + pos + " ms");
    }


    /**
     * @cmd 0x00 0x27
     * @param chapter - new position in playlist
     * @response number(4) time elapsed on current song
     */
    public void oap_04_write_polling_chapter_status_changed(int chapter)
    {
        if(!polling_mode) return;

        byte cmd[] = {
                0x00,
                0x27,
                0x05, // info new chapter
                (byte) ((chapter >> 24) & 0xff),
                (byte) ((chapter >> 16) & 0xff),
                (byte) ((chapter >> 8) & 0xff),
                (byte) (chapter & 0xff)
        };
        oap_04_write_cmd(cmd);
        PodEmuLog.debug("OAPM: AIR_MODE OUT - polling message: chapter changed to " + chapter);
    }




    /**
     * @cmd 0x00 0x2D
     * @response shuffle(1) Returns current shuffle mode:
     * 0x00 = Off
     * 0x01 = Songs
     * 0x02 = Albums
     */
    private void oap_04_write_shuffle_mode()
    {
        // TODO implement shuffle
        byte msg[] = {0x00, 0x2D, shuffle_mode};
        oap_04_write_cmd(msg);
        PodEmuLog.debug("OAPM: AIR_MODE OUT - written shuffle mode: " + shuffle_mode);
    }

    /**
     * @cmd 0x00 0x30
     * @response repeatmode(1) Returns current repeat mode:
     * 0x00 = Off
     * 0x01 = Songs
     * 0x02 = Albums
     */
    private void oap_04_write_repeat_mode()
    {
        // TODO implement repeat mode
        byte msg[] = {0x00, 0x30, repeat_mode};
        oap_04_write_cmd(msg);
        PodEmuLog.debug("OAPM: AIR_MODE OUT - written repeat mode: " + repeat_mode);
    }


    /**
     * @cmd 0x00 0x34
     * Return screen size:
     * Gen3: 120 * 65
     * 0x00 0x78 0x00 0x41 0x01
     * Gen5: 310 * 168
     * 0x01 0x36 0x00 0xA8 0x01
     * iPod Touch 4G:
     * 0x00 0xA6 0x00 0x4C 0x01
     * @response: number(2) - resolution X for mode 02
     * @response: number(2) - resolution Y for mode 02
     * @response: number(1) - mode, always 01 which indicates greyscale mode, 2 bits per pixel
     */
    private void oap_04_write_screen_resolution_34()
    {
        if(SerialInterfaceBuilder.getSerialInterface() instanceof SerialInterface_BLE)
        {
            PodEmuLog.debug("OAPM: image upload is not supported for BLE interfaces");
            oap_04_write_return_code(0x33, IPOD_ERROR_CMD_FAILED);
        }
        else
        {
            byte msg[] = {0x00, 0x34, 0x00, 0x00, 0x00, 0x00, 0x01};
            msg[2] = (byte) ((DockingLogoView.IMAGE_MAX_RES_X >> 8) & 0xff);
            msg[3] = (byte) ((DockingLogoView.IMAGE_MAX_RES_X) & 0xff);
            msg[4] = (byte) ((DockingLogoView.IMAGE_MAX_RES_Y >> 8) & 0xff);
            msg[5] = (byte) ((DockingLogoView.IMAGE_MAX_RES_Y) & 0xff);
            oap_04_write_cmd(msg);
            PodEmuLog.debug("OAPM: AIR_MODE OUT - written screen resolution 34: " + DockingLogoView.IMAGE_MAX_RES_X + "x" + DockingLogoView.IMAGE_MAX_RES_Y);
        }
    }

    /**
     * @cmd 0x00 0x36
     * @response number(4)  number of songs in playlist
     */
    private void oap_04_write_playlist_song_count()
    {
        byte b[]=new byte[4];
        int count = MediaPlayback.getInstance().getCurrentPlaylist().getTrackCount();
        int_to_byte_array(count, b);
        byte msg[] = {0x00, 0x36, b[0], b[1], b[2], b[3]};
        oap_04_write_cmd(msg);
        PodEmuLog.debug("OAPM: AIR_MODE OUT - written playlist song count: " + count);
    }


    /**
     * @cmd 0x00 0x3A - colour version of 0x00 0x34
     * @response: number(2) - resolution X for mode 02
     * @response: number(2) - resolution Y for mode 02
     * @response: number(1) - mode RGB_565 (2 bytes per pixel)
     *                              0x01 - monochrome 2 bit/pixel
     *                              0x02 - RGB_565, little-endian
     *                              0x03 - RGB_565, big-endian
     * iPod Touch 4G response: 00 A6 00 4C 02 00 A6 00 4C 03
     */
    private void oap_04_write_screen_resolution_3A()
    {
        if(SerialInterfaceBuilder.getSerialInterface() instanceof SerialInterface_BLE)
        {
            PodEmuLog.debug("OAPM: image upload is not supported for BLE interfaces");
            oap_04_write_return_code(0x39, IPOD_ERROR_CMD_FAILED);
        }
        else
        {
            byte msg[] = {0x00, 0x3A, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x03};
            msg[7] =
            msg[12] =
                    msg[2] = (byte) ((DockingLogoView.IMAGE_MAX_RES_X >> 8) & 0xff);
            msg[8] =
            msg[13] =
                    msg[3] = (byte) ((DockingLogoView.IMAGE_MAX_RES_X) & 0xff);
            msg[9] =
            msg[14] =
                    msg[4] = (byte) ((DockingLogoView.IMAGE_MAX_RES_Y >> 8) & 0xff);
            msg[10] =
            msg[15] =
                    msg[5] = (byte) ((DockingLogoView.IMAGE_MAX_RES_Y) & 0xff);

            oap_04_write_cmd(msg);
            PodEmuLog.debug("OAPM: AIR_MODE OUT - written screen resolution 3A: " + DockingLogoView.IMAGE_MAX_RES_X + "x" + DockingLogoView.IMAGE_MAX_RES_Y);
        }
    }


    /**
     * Concatenates msg and str into byte[] array and returns the result
     *
     * @param msg - leading bytes to be added to the string
     * @param str - string to be added to the message
     * @return - concatenated byte[] array
     */
    private byte[] oap_build_ipod_msg(byte[] msg, String str)
    {
        // first adding leading spaces to later store msg in it
        for (int i = 0; i < msg.length; i++)
        {
            str = " " + str;
        }
        //adding trailing space to later store 0x00 in it
        str += " ";

        byte new_msg[] = str.getBytes();
        for (int i = 0; i < msg.length; i++)
        {
            new_msg[i] = msg[i];
        }
        new_msg[str.length() - 1] = 0x00;
        return new_msg;
    }


    /**
     * Writes msg[] concatenated with str to serial interface
     *
     * @param msg
     * @param str
     */
    private void oap_04_write_string(byte[] msg, String str)
    {
        oap_04_write_cmd(oap_build_ipod_msg(msg, str));
    }


    /**
     * Calculates checksum of provided message
     *
     * @param buf - buffer containing the message
     * @param len - length of the message in buf
     * @return - calculates checksum
     */
    public byte oap_calc_checksum(byte buf[], int len)
    {
        int checksum = 0, j;
        // do not consider first 2 bytes and last byte for the checksum
        for (j = 2; j < len - 1; j++)
        {
            checksum += buf[j];
        }
        checksum &= 0xff;
        checksum = 0x100 - checksum;
        return (byte) checksum;
    }


    /**
     * Just builds the message and prints a byte to the logs
     *
     * @param rw  - read/write indicator
     * @param b   - byte
     * @param num - position from the beginning of message on which this byte was received
     */
    private void oap_print_char(int rw, byte b, int num)
    {
        PodEmuLog.debugVerbose("OAPM: Line " + line + ": len=" + line_cmd_len + " cnt=" + num + " " + String.format(": %s: %02X", (rw == READ ? "RCV" : "WRITE"), b));
    }


    /**
     * Transforms byte array to human readable form in hex format
     *
     * @param buf - buffer containing byte array
     * @param len - length of the byte array contained by buf
     * @return - resulting string
     */
    public static String oap_hex_to_str(byte buf[], int len)
    {
        int j;
        String str = "";
        //printw("LEN:(%d)",len);
        for (j = 0; j < len; j++)
        {
            str += String.format(" %02X", buf[j]);
        }
        return str;
    }


    /**
     * Converts the message to human readable form and put it to logs. Message is expected
     * to be in correct format - especially length should be correct. Otherwise errors could occur.
     *
     * @param msg    - buffer containing the message
     * @param in_out - true=IN, false=OUT
     * @param is_ext - true, if treat message as extended image message
     */
    private void oap_print_podmsg(byte msg[], boolean in_out, boolean is_ext)
    {
        int j, pos_shift = 0, len;
        if (is_ext)
        {
            len = ((msg[3] & 0xff) << 8) | ((msg[4] & 0xff));
            // if we are dealing with extended message then we need to count 2 additional b
            pos_shift += 2;
        } else
        {
            len = (msg[2] & 0xff);
        }
        String tmp;

        if (in_out) tmp = "MSG IN  ";
        else tmp = "MSG OUT ";

        if (len > 0) tmp += String.format("|  %02X  ", msg[3 + pos_shift]);
        else tmp += "|      ";
        if (len > 1) tmp += String.format("|  %02X ", msg[4 + pos_shift]);
        else tmp += "|      ";
        if (len > 2) tmp += String.format("%02X  ", msg[5 + pos_shift]);
        else tmp += "    ";
        tmp += "|  ";
        // 3 bytes are for mode and command length
        for (j = 6; j < len + 6 - 3; j++)
        {
            tmp += String.format("%02X ", msg[j + pos_shift]);
        }
        tmp += " ";
        if (len > 3)
        {
            byte tmpmsg[] = new byte[len - 3];
            for (j = 0; j < len - 3; j++)
            {
                tmpmsg[j] = msg[j + 6 + pos_shift];
            }
            String rawstr = new String(tmpmsg);
            tmp += "[" + rawstr + "]";
        }

        PodEmuLog.debug("OAPM: " + tmp);

    }


    /**
     * Builds iPod serial message and writes it to serial line
     *
     * @param bytes  - command and parameters
     * @param len    - total length of command+parameters
     * @param mode   - mode that should be used
     */
    private void oap_write_cmd(byte bytes[], int len, byte mode)
    {
        byte msg[] = new byte[len + 5];
        int chksum, i;


        if (len > 254)
        {
            PodEmuLog.debug("ERROR: Message length cannot be greater than 255");
            return;
        }

        msg[0] = (byte) 0xFF;
        msg[1] = (byte) 0x55;
        msg[2] = (byte) (len + 1);
        msg[3] = mode;

        chksum = msg[2] + msg[3];
        for (i = 0; i < len; i++)
        {
            msg[i + 4] = bytes[i];
            chksum += bytes[i];
        }
        chksum &= 0xff;
        chksum = 0x100 - chksum;
        msg[i + 4] = (byte) chksum;

        PodEmuLog.debugVerbose("Line " + line + ": RAW MSG OUT: " + oap_hex_to_str(msg, msg.length));
        oap_print_podmsg(msg, false, false);

        SerialInterface serialInterface = SerialInterfaceBuilder.getSerialInterface();

        if (serialInterface != null)
        {
            serialInterface.write(msg, len + 5);
        }
        else
        {
            PodEmuLog.error("OAPM: attempt to write while serial interface is not connected");
        }
    }

    /**
     * Builds iPod serial message and writes it to serial line. Message is expected to be
     * normal message (not extended image message)
     *
     * @param bytes - command and parameters
     * @param len   - total length of command+parameters
     */
    private void oap_04_write_cmd(byte bytes[], int len)
    {
        oap_write_cmd(bytes, len, (byte) ipod_mode);
    }


    /**
     * Builds iPod serial message and writes it to serial line. Message is expected to be
     * normal message (not extended image message). Full byte[] length will be written.
     *
     * @param bytes - command and parameters
     */
    private void oap_04_write_cmd(byte bytes[])
    {
        oap_04_write_cmd(bytes, bytes.length);
    }

    /**
     * sends currently playing information to iPod
     */
    public void flush()
    {
        // TODO write flush body
    }

    /**
     * Enables or disables polling mode.
     * In polling mode elapsed time is sent to the docking station evey 500ms.
     *
     * @param on_off - enable if true, disable if false
     */
    public void enable_polling_mode(boolean on_off)
    {
        polling_mode = on_off;

        // write back success return code
        oap_04_write_return_code(0x0026, IPOD_SUCCESS);
    }


    /**
     * Class used to post picture block to the main activity
     */
    public class PictureBlock
    {
        public byte data[];
        public int len;

        PictureBlock(byte[] d, int l)
        {
            data = d;
            len = l;
        }
    }



    private void oap_send_bitmap()
    {
        Message message = mHandler.obtainMessage(0);
        message.arg1 = 1; //indication that we are sending a bitmap
        message.obj = mBitmap;
        mHandler.sendMessage(message);
    }


    /**
     * Once image block is received this function is called to inform Main Activity
     * and to process this block
     *
     * @param data - image block data buffer
     * @param len  - length of the image block contained in data
     */
    private void oap_receive_image_msg(byte[] data, int len)
    {
        Paint mPaint;
        mPaint = new Paint();
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setStrokeWidth(0);

        if (data.length < 10)
        {
            PodEmuLog.debug("ERROR: Wrong image block received");
            return;
        }
/*
        PodEmuLog.debug("PARAMS:");
        for (int h=0;h<data.length;h++)
            PodEmuLog.debug("Byte "+h+": "+String.format("%02X", data[h]));
*/
        int block_number = ((data[0] & 0xff) << 8) | (data[1] & 0xff);
        int shift = 2; // shift to start of image data

        if (block_number == 0)
        {
            pixel_format = data[2] & 0xff;
            image_pos_x = 0;
            image_pos_y = 0;
            current_byte_in_line = 0;
            image_res_x = ((data[3] & 0xff) << 8) | (data[4] & 0xff);
            image_res_y = ((data[5] & 0xff) << 8) | (data[6] & 0xff);

            // calculate image position so that it is displayed centered
            image_start_x = (Math.max(image_res_x, image_res_y) - image_res_x) / 2;
            image_start_y = (Math.max(image_res_x, image_res_y) - image_res_y) / 2;

            image_bytes_per_line = ((data[7] & 0xff) << 24) | ((data[8] & 0xff) << 16) | ((data[9] & 0xff) << 8) | (data[10] & 0xff);
            mBitmap = Bitmap.createBitmap(Math.max(image_res_x, image_res_y),
                    Math.max(image_res_x, image_res_y),
                    Bitmap.Config.RGB_565);
            mCanvas = new Canvas(mBitmap);
            mPaint.setColor(Color.WHITE);
            mCanvas.drawRect(0, 0, Math.max(image_res_x, image_res_y), Math.max(image_res_x, image_res_y), mPaint);

            shift = 11;
            PodEmuLog.debug("Received image starting block:");
            PodEmuLog.debug("             pixel format=" + pixel_format);
            PodEmuLog.debug("              raw msg len=" + len);
            PodEmuLog.debug("              image_res_x=" + image_res_x);
            PodEmuLog.debug("              image_res_y=" + image_res_y);
            PodEmuLog.debug("     image_bytes_per_line=" + image_bytes_per_line);
        }

        if (mCanvas == null)
        {
            return;
        }

        int data_last=Math.min(len, data.length) - 1;
        for (int i = shift; i < data_last; i += 2)
        {
            // draw pixels only if we are inside declared resolution
            if (image_pos_x < image_res_x && image_pos_y < image_res_y)
            {
                switch(pixel_format)
                {
                    case 0x01: // monochrome
                    {
                        int bytes=1;
                        if(i+2==data_last)
                        {
                            bytes = 2;
                        }
                        for(int j=i;j<=i+bytes;j++)
                        {
                            for (int s = 6; s >= 0; s -= 2)
                            {
                                //PodEmuLog.debug("Pixel byte "+String.format("0x%02X",data[j])+" at pos: " + image_pos_x+":"+image_pos_y + "("+image_start_x+":"+image_start_y+")");
                                int mask = (3 << s);
                                int color = data[j] & mask;
                                color >>= s;
                                color = 255 - color * (255 / 3);
                                mPaint.setColor(Color.rgb(color, color, color));
                                mCanvas.drawPoint(image_start_x + image_pos_x, image_start_y + image_pos_y, mPaint);

                                image_pos_x++;
                            }

                            current_byte_in_line++;
                            if (current_byte_in_line >= image_bytes_per_line)
                            {
                                if (image_pos_y < image_res_y) image_pos_y++;
                                image_pos_x = 0;
                                current_byte_in_line = 0;
                            }
                        }
                    } break;
                    case 0x02: // RGB_565 little-endian
                    {
                        // for little endian we just need to swap the bytes
                        byte tmp=data[i];
                        data[i]=data[i+1];
                        data[i+1]=tmp;
                    }
                    case 0x03: // RGB_565 big-endian
                    {
                        int red = (data[i] & 0xff) >> 3; // take first 5 bits
                        int green = ((((data[i] & 0xff) << 8) | (data[i + 1] & 0xff)) >> 5) & 0x3f; // take next 6 bits
                        int blue = data[i + 1] & 0x1f; // take last 5 bits

                        //expanding colors to 8 bit
                        red <<= 3;
                        green <<= 2;
                        blue <<= 3;

                        mPaint.setColor(Color.rgb(red, green, blue));
                        mCanvas.drawPoint(image_start_x + image_pos_x, image_start_y + image_pos_y, mPaint);
                        image_pos_x++;
                        current_byte_in_line+=2;
                    } break;
                }
            }

            if (current_byte_in_line >= image_bytes_per_line)
            {
                if (image_pos_y < image_res_y) image_pos_y++;
                image_pos_x = 0;
                current_byte_in_line = 0;
            }
        }

        // write to serial success command
        byte msg[] = {0x00, 0x01, 0x00, 0x00, 0x32};
        oap_04_write_cmd(msg);

        // send updated bitmap to MainActivity
        oap_send_bitmap();
    }

    /**
     * Inform Main Activity that iPod was connected and send current mode
     */
    private void oap_communicate_ipod_connected()
    {
        // if mHandler is not set then service is not ready. This method will be called again once service is ready.
        if (mHandler != null)
        {
            Message message = mHandler.obtainMessage(0);
            message.arg1 = 2; // indicate mode change message
            message.arg2 = ipod_mode;
            mHandler.sendMessage(message);
        }
    }


    /**
     * Standard getter for polling mode variable
     *
     * @return - boolean indication of polling mode
     */
    public boolean getPollingMode()
    {
        return polling_mode;
    }

    private void int_to_byte_array(int i, byte b[])
    {
        if (b.length < 4)
        {
            PodEmuLog.error("byte array length is less then 4");
            return;
        }

        b[0] = (byte) ((i >> 24) & 0xff);
        b[1] = (byte) (((i & 0x00ffffff) >> 16) & 0xff);
        b[2] = (byte) (((i & 0x0000ffff) >> 8) & 0xff);
        b[3] = (byte) ((i & 0x000000ff) & 0xff);
    }

    private int byte_array_to_int(byte b[])
    {
        return byte_array_to_int(b,0);
    }



    private int byte_array_to_int(byte b[], int start_pos)
    {
        if (b.length < 4+start_pos)
        {
            PodEmuLog.error("byte array length is less then 4");
            return 0;
        }

        int i = ((b[start_pos+0] & 0xff) << 24) | ((b[start_pos+1] & 0xff) << 16) | ((b[start_pos+2] & 0xff) << 8) | (b[start_pos+3] & 0xff);

        return i;
    }

    /**
     *
     * @param forceSimpleMode - 1 = force simple mode
     *                          0 = do not force
     */
    public void setForceSimpleMode(int forceSimpleMode)
    {
        this.forceSimpleMode = forceSimpleMode;
    }
}

