/**

 OAPMessenger.class is class that implements "30 pin" serial protocol
 for iPod. It is based on the protocol description available here:
 http://www.adriangame.co.uk/ipod-acc-pro.html

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
    public static final byte IPOD_ERROR = 0x02;
    public static final byte IPOD_OUT_OF_RANGE = 0x04;
    private static Handler mHandler;

    private int line_cmd_pos = 0;
    private int line_cmd_len = 0;
    private boolean is_extended_image = false;
    private int line_ext_pos = 2;
    private byte line_buf[] = new byte[6503]; // max should be 259 bytes
    private byte ext_image_buf[] = new byte[5];

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


    // definition of the in/out line if more than one instance of object created
    private static int line = 0;


    void setHandler(Handler handler)
    {
        this.mHandler = handler;

        // if this function was called, then the service was just bound
        // it means we need to communicate current iPod mode
        oap_communicate_ipod_connected();
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
    private byte unknown_var = 0x00;
    private boolean polling_mode = false;
    private byte shuffle_mode = 0x00;
    private byte repeat_mode = 0x00;
    private static PodEmuMessage currentlyPlaying = new PodEmuMessage();

    OAPMessenger()
    {
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

            PodEmuLog.debug(String.format("Line " + line + ": MSG LEN: %d TOTAL LEN: %d", (b & 0xff), line_cmd_len));

            if (b == (byte) 0x00)
            {
                /*
                 * this is potential extended image message candidate
                 * extended image message has 3 bytes allocated to message length
                 * instead of one byte. Thanks to that total message length could
                 * be more than 259 bytes. Byte 2 is treated as indicator of potential
                 * ext image msg. Length bytes are assumed to be 3 and 4
                 */
                is_extended_image = true;
                line_ext_pos = 2;
            }

        }

        if (is_extended_image)
        {
            if (line_ext_pos <= 8)
                PodEmuLog.verbose(String.format("EXT MSG check - pos:%d, byte: 0x%02X", line_ext_pos, b));

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

                PodEmuLog.debug(String.format("Line %d: Extended image message detected!!!", line));
                // from now and on message will be treated normally
            }

            line_ext_pos++;
        }


        if (line_cmd_pos == 0 && b != (byte) 0xff)
        {
            PodEmuLog.debug("Line " + line + ": ERROR: first byte is not 0xFF. Received 0x" + String.format("%02X", b));
            return -1;
        }

        if (line_cmd_pos == 1 && b != (byte) 0x55)
        {
            PodEmuLog.debug("Line " + line + ": ERROR: second byte is not 0x55. Received 0x" + String.format("%02X", b));
            return -1;
        }

        if (!is_extended_image && line_cmd_len > 259)
        {
            PodEmuLog.debug("Line " + line + ": ERROR: message length cannot exceed 259 bytes");
            return -1;
        }

        if (is_extended_image && line_cmd_len > 65025 + 6)
        {
            PodEmuLog.debug("Line " + line + ": ERROR: extended message length cannot exceed 65031 bytes");
            return -1;
        }

        line_buf[line_cmd_pos] = b;

        line_cmd_pos++;

        if (line_cmd_pos == line_cmd_len)
        {
            int msg_len;
            byte checksum;

            // next 2 lines are just for logging
            PodEmuLog.debug("Line " + line + ": RAW MSG  IN: " + oap_hex_to_str(line_buf, line_cmd_len));
            oap_print_podmsg(line_buf, true, (line_cmd_len > 7 && is_extended_image));

            checksum = oap_calc_checksum(line_buf, line_cmd_len);
            if (line_buf[line_cmd_len - 1] != checksum)
            {
                PodEmuLog.debug("Line " + line + String.format(": ERROR: checksum error. Received: %02X  Should be: %02X", line_buf[line_cmd_len - 1], checksum));
            } else
            {
                // Received message is OK. We cen process it and prepare the reply
                PodEmuLog.verbose("Line " + line + String.format(": Checksum OK. Received: %02X  Should be: %02X", line_buf[line_cmd_len - 1], checksum));
                PodEmuLog.debug("Line " + line + ": Checksum OK");

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

        PodEmuLog.debug("Processing message started");

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

        PodEmuLog.debug(String.format("Received - Ext: %d, Len: %d, Mode: 0x%02X, CMD: 0x%04X", (is_ext ? 1 : 0), len, mode, cmd));

        if (mode == 0x00 && len>=2) // general mode
        {
            switch(scmd1)
            {
                case 0x01:
                {
                    if (cmd == 0x0104) // AiR mode requested
                    {
                        ipod_mode = IPOD_MODE_AIR;
                        oap_communicate_ipod_connected();
                    } else if (cmd == 0x0102) // simple mode requested
                    {
                        ipod_mode = IPOD_MODE_SIMPLE;
                        oap_communicate_ipod_connected();
                    }
                } break;
                case 0x03: // current mode requested
                {
                    // writing current mode
                    byte msg[] = new byte[2];
                    msg[0] = 0x04;
                    msg[1] = (byte) ipod_mode;
                    oap_write_cmd(msg, 2, (byte) 0x00);
                } break;
                case 0x05: // AiR mode requested
                {
                    ipod_mode = IPOD_MODE_AIR;
                    oap_communicate_ipod_connected();
                    oap_00_write_return_code(scmd1, IPOD_SUCCESS);
                } break;
                case 0x06: // simple mode requested
                {
                    ipod_mode = IPOD_MODE_SIMPLE;
                    oap_communicate_ipod_connected();
                    oap_00_write_return_code(scmd1, IPOD_SUCCESS);
                } break;
                case 0x07: // device name requested
                {
                    byte c[] = {0x08};
                    byte msg[]=oap_build_ipod_msg(c, ipod_name);
                    oap_write_cmd(msg, msg.length, (byte) 0x00);
                } break;
                case 0x09: // SW Version requested
                {
                    byte msg[] = new byte[4];
                    msg[0] = 0x0a; // cmd
                    msg[1] = 0x01; // major version
                    msg[2] = 0x00; // minor version
                    msg[3] = 0x04; // revision
                    oap_write_cmd(msg, msg.length, (byte) 0x00);
                } break;
                case 0x0B: // SerialNumber requested
                {
                    byte c[] = {0x0C};
                    byte msg[]=oap_build_ipod_msg(c, "xPodEmu-076"); // don't ask me why :) just!
                    oap_write_cmd(msg, msg.length, (byte) 0x00);
                } break;
                case 0x0D: // Model Num requested
                {
                    byte msg[] = {0x0E, 0x00, 0x17, 0x00, 0x0A, 0x4D, 0x42, 0x37, 0x35, 0x34, 0x00};
                    oap_write_cmd(msg, msg.length, (byte) 0x00);
                } break;
                case 0x0F: // protocol version requested for mode stored in scmd2
                {
                    if(len<3)
                    {
                        oap_00_write_return_code(scmd1, IPOD_OUT_OF_RANGE);
                        break;
                    }
                    byte msg[] = {0x10, scmd2, 0x01, 0x0E};
                    oap_write_cmd(msg, msg.length, (byte) 0x00);
                } break;
                default:
                {
                    oap_00_write_return_code(scmd1, IPOD_ERROR);
                    PodEmuLog.debug(String.format("ERROR: Mode switching: unrecognized command 0x%04X received", cmd));
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
                    MediaControlLibrary.action_play();
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
                    MediaControlLibrary.action_next();
                    break;


                /*
                 * @cmd 0x00 0x10
                 * Skip<
                 */
                case 0x0010:
                    MediaControlLibrary.action_prev(0);
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
                    MediaControlLibrary.action_stop();
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
                        MediaControlLibrary.action_play();
                    }


                    /*
                     * @cmd 0x00 0x00 0x02
                     * Pause
                     */
                    else if (params.length == 1 && params[0] == (byte) 0x02)
                    {
                        MediaControlLibrary.action_pause();
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
                        // TODO implement shuffle
                    }


                    /*
                     * @cmd 0x00 0x00 0x00 0x01
                     * Toggles Repeat
                     */
                    else if (params.length == 1 && params[0] == (byte) 0x00 && params[1] == (byte) 0x01)
                    {
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
                 * @cmd: 0x00 0x02
                 * @param None
                 * NCU, simple ping-request ?
                 * should get response FF FF FF FF 00 00 00 00
                 */
                case 0x0002:
                    oap_04_write_ping_response();
                    break;

                /*
                 * @cmd: 0x00 0x09
                 * @param None
                 * NCU, requests flag set by command 0x00 0x0b
                 */
                case 0x0009:
                    oap_04_write_unknown_var();
                    break;

                /*
                 * set the unknown variable to params[0]
                 * @cmd 0x00 0x0b
                 * @param byte(1) Parameter is either 0x00 or 0x01.
                 * Get success/failure response
                 */
                case 0x000b:
                {
                    if (params.length < 1)
                    {
                        oap_04_write_return_code(cmd, IPOD_OUT_OF_RANGE);
                        break;
                    }
                    unknown_var = params[0];
                    oap_04_write_return_code(cmd, IPOD_SUCCESS);
                }
                break;

                /*
                 * TODO debug
                 * @cmd 0x00 0x0c
                 * @param type(1)
                 *          0x01 - unknown, gives log
                 *          0x02 - unknown, always gives response 02 00 00 00 00 00 00 00 00
                 *          0x03 - unknown, always gives response 03 00 00 00 00
                 *          0x04 - unknown, always gives response 04 00 00 00 00
                 *          0x05 - Genre
                 *          0x06 - Composer
                 *          0x07 - unknown, gives response 07 and 16 bytes
                 * @param number(4) song number
                 * @param number(2) always 0x00 0x00
                 * Returns some additional parameters for selected song, gives response 0x00 0x0d (type + string)
                 */
                case 0x000C:
                {
                    if (params.length < 7)
                    {
                        oap_04_write_return_code(cmd, IPOD_OUT_OF_RANGE);
                        break;
                    }
                    int song_nr = ((params[1] & 0xff) << 24) | ((params[2] & 0xff) << 16) | ((params[3] & 0xff) << 8) | (params[1] & 0xff);
                    oap_04_write_additional_info(params[0], song_nr);
                }
                break;

                /*
                 * @cmd 0x00 0x12
                 * @param None
                 * Thought to be "Get iPod Type / Size"
                 */
                case 0x0012:
                    oap_04_write_model();
                    break;

                /*
                 * @cmd 0x00 0x14
                 * @param None
                 * Get iPod Name
                 */
                case 0x0014:
                    oap_04_write_ipod_name();
                    break;

                /*
                 * @cmd 0x00 0x16
                 * @param None
                 * Switch to main library playlist (playlist 0)
                 */
                case 0x0016:
                {
                    // TODO implement libraries and playlists

                    // just in case writing success retval
                    oap_04_write_return_code(cmd, IPOD_SUCCESS);
                }
                break;

                /*
                 * @cmd 0x00 0x17
                 * @param type(1)
                 *              0x01 - Playlist
                 *              0x02 - Artist
                 *              0x03 - Album
                 *              0x04 - Genre
                 *              0x05 - Song
                 *              0x06 - Composer
                 * @param number(4)
                 * Switch to item identified by number and type given.
                 * 0xffffffff seems to be the beginning
                 */
                case 0x0017:
                {
                    if (params.length < 5)
                    {
                        oap_04_write_return_code(cmd, IPOD_OUT_OF_RANGE);
                        break;
                    }
                    // TODO implement libraries and playlists

                    // just in case writing success retval
                    oap_04_write_return_code(cmd, IPOD_SUCCESS);
                }
                break;

                /*
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
                    if (params.length < 1)
                    {
                        oap_04_write_return_code(cmd, IPOD_OUT_OF_RANGE);
                        break;
                    }
                    oap_04_write_type_count(params[0]);
                }
                break;

                /*
                 * @cmd 0x00 0x1A
                 * @param type(1)
                 *              0x01 - Playlist
                 *              0x02 - Artist
                 *              0x03 - Album
                 *              0x04 - Genre
                 *              0x05 - Song
                 *              0x06 - Composer
                 * @param number(4)
                 * @param number(4)
                 * Get names for range of items. First number is starting item offset
                 * (0 is first item) and second number is how many. One response
                 * message 0x00 0x1B for each item.
                 */
                case 0x001A:
                {
                    if (params.length < 9)
                    {
                        oap_04_write_return_code(cmd, IPOD_OUT_OF_RANGE);
                        break;
                    }
                    int pos_start = ((params[1] & 0xff) << 24) | ((params[2] & 0xff) << 16) | ((params[3] & 0xff) << 8) | (params[4] & 0xff);
                    int count = ((params[5] & 0xff) << 24) | ((params[6] & 0xff) << 16) | ((params[7] & 0xff) << 8) | (params[8] & 0xff);
                    oap_04_write_type_names(params[0], pos_start, count);
                }
                break;

                /*
                 * @cmd 0x00 0x1C
                 * @param none
                 * Get time and status info
                 */
                case 0x001C:
                    oap_04_write_info();
                    break;

                /*
                 * @cmd 0x00 0x1E
                 * @param none
                 * Get current position in playlist
                */
                case 0x001E:
                    oap_04_write_playlist_position();
                    break;

                /*
                 * @cmd 0x00 0x20
                 * @param number(4)
                 * Get title of a song number
                 */
                case 0x0020:
                {
                    if (params.length < 4)
                    {
                        oap_04_write_return_code(cmd, IPOD_OUT_OF_RANGE);
                        break;
                    }
                    int song_number = ((params[0] & 0xff) << 24) | ((params[1] & 0xff) << 16) | ((params[2] & 0xff) << 8) | (params[3] & 0xff);
                    oap_04_write_title(song_number);
                }
                break;

                /*
                 * @cmd 0x00 0x22
                 * @param number(4)
                 * Get artist of a song number
                 */
                case 0x0022:
                {
                    if (params.length < 4)
                    {
                        oap_04_write_return_code(cmd, IPOD_OUT_OF_RANGE);
                        break;
                    }
                    int song_number = ((params[0] & 0xff) << 24) | ((params[1] & 0xff) << 16) | ((params[2] & 0xff) << 8) | (params[3] & 0xff);
                    oap_04_write_artist(song_number);
                }
                break;

                /*
                 * @cmd 0x00 0x24
                 * @param number(4)
                 * Get album of a song number
                 */
                case 0x0024:
                {
                    if (params.length < 4)
                    {
                        oap_04_write_return_code(cmd, IPOD_OUT_OF_RANGE);
                        break;
                    }
                    int song_number = ((params[0] & 0xff) << 24) | ((params[1] & 0xff) << 16) | ((params[2] & 0xff) << 8) | (params[3] & 0xff);
                    oap_04_write_album(song_number);
                }
                break;

                /*
                 * @cmd 0x00 0x26
                 * @param pollingmode(1) Polling Mode:
                 *                       0x01 = Start
                 *                       0x00 = Stop
                 * Polling causes the return command 0x00 0x27 to be sent every 500 milliseconds.
                 */
                case 0x0026:
                {
                    if (params.length < 1)
                    {
                        oap_04_write_return_code(cmd, IPOD_OUT_OF_RANGE);
                        break;
                    }
                    enable_polling_mode(params[0] != 0);
                }
                break;

                /*
                 * @cmd 0x00 0x28
                 * @param number(4)
                 * Execute playlist and jump to specified songnumber.
                 * 0xFFFFFFFF will always be start of playlist even when shuffle is on.
                 */
                case 0x0028:
                {
                    if (params.length < 4)
                    {
                        oap_04_write_return_code(cmd, IPOD_OUT_OF_RANGE);
                        break;
                    }

                    // if jump to the beginning requested
                    int pos=byte_array_to_int(params);

                    MediaControlLibrary.jump_to(pos, currentlyPlaying.getPositionMS());
                    // TODO implement playlist
                    oap_04_write_return_code(cmd, IPOD_SUCCESS);
                    break;
                }

                /*
                 * @cmd 0x00 0x29
                 * @param command(1)  AiR Playbck Control:
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
                    if (params.length < 1)
                    {
                        oap_04_write_return_code(cmd, IPOD_OUT_OF_RANGE);
                        break;
                    }

                    byte retval = IPOD_SUCCESS;
                    switch (params[0])
                    {
                        case 0x01:
                            MediaControlLibrary.action_play_pause();
                            break;
                        case 0x02:
                            MediaControlLibrary.action_stop();
                            break;
                        case 0x03:
                            MediaControlLibrary.action_next();
                            break;
                        case 0x04:
                            MediaControlLibrary.action_prev(0);
                            break;
                        case 0x05:
                            MediaControlLibrary.action_skip_forward();
                            break;
                        case 0x06:
                            MediaControlLibrary.action_skip_backward();
                            break;
                        case 0x07:
                            break;
                        default:
                            retval = IPOD_ERROR;
                    }
                    oap_04_write_return_code(cmd, retval);
                    break;
                }

                /*
                 * @cmd 0x00 0x2C
                 * @param none
                 * Get shuffle mode
                 */
                case 0x002C:
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
                    if (params.length < 1 || params[0] < 0x00 || params[0] > 0x02)
                    {
                        oap_04_write_return_code(cmd, IPOD_OUT_OF_RANGE);
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
                    if (params.length < 1 || params[0] < 0x00 || params[0] > 0x02)
                    {
                        oap_04_write_return_code(cmd, IPOD_OUT_OF_RANGE);
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
                    oap_receive_image_msg(params, len - 3);
                    break;

                /*
                 * @cmd 0x00 0x33
                 * @param none
                 * Get max screen size for picture upload
                 */
                case 0x0033:
                    oap_04_write_screen_resolution_34();
                    break;

                /*
                 * @cmd 0x00 0x35
                 * @param none
                 * Get number of songs in playlist
                 */
                case 0x0035:
                    oap_04_write_playlist_song_count();
                    break;

                /*
                 * @cmd 0x00 0x37
                 * @param number(4) jump to specified song number in playlist
                 */
                case 0x0037:
                {
                    if (params.length < 4)
                    {
                        oap_04_write_return_code(cmd, IPOD_OUT_OF_RANGE);
                        break;
                    }
                    MediaControlLibrary.jump_to(byte_array_to_int(params),currentlyPlaying.getPositionMS());
                    // TODO implement playlist
                    oap_04_write_return_code(cmd, IPOD_SUCCESS);
                }
                break;

                /*
                 * TODO debug more
                 * @cmd 0x00 0x38 - action - get success response
                 * @param type(1) ?
                 * @param number(4) - song number?
                 * @param unknown(1) ?
                 * The purpose of this command is not known. Should be answered
                 * with success/failure response
                 */
                case 0x0038:
                {
                    if (params.length < 6)
                    {
                        oap_04_write_return_code(cmd, IPOD_OUT_OF_RANGE);
                        break;
                    }
                    // TODO debug
                    oap_04_write_return_code(cmd, IPOD_SUCCESS);
                }
                break;

                /*
                 * @cmd 0x00 0x39 - response is 3A
                 * @param NO PARAMS
                 * NCU
                 */
                case 0x0039:
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
     * @param cmd    - command to be parsed as 2 bytes
     * @param result - result to be posted
     * @cmd 0x00 0x01
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
    }

    /**
     * @cmd 0x00 0x03
     * @response byte(8) - response to ping
     * 0xFF 0xFF 0xFF 0xFF 0x00 0x00 0x00 0x00
     */
    private void oap_04_write_ping_response()
    {
        byte msg[] = {0x00, 0x03, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x00, 0x00, 0x00, 0x00};
        oap_04_write_cmd(msg);
    }

    /**
     * @cmd 0x00 0x0a
     * @response byte(1)      0x00 or 0x01 depending on flag status
     */
    private void oap_04_write_unknown_var()
    {
        byte msg[] = {0x00, 0x0A, unknown_var};
        oap_04_write_cmd(msg);
    }

    /**
     * TODO debug more
     *
     * @param rtype   - type requested as described above
     * @param song_nr - song number the request is related to
     * @cmd 0x00 0x0d
     * @response: type(1):
     * 0x01 - unknown, gives log
     * 0x02 - unknown, always gives response 02 00 00 00 00 00 00 00 00
     * 0x03 - unknown, always gives response 03 00 00 00 00
     * 0x04 - unknown, always gives response 04 00 00 00 00
     * 0x05 - Genre
     * 0x06 - Composer
     * 0x07 - unknown, gives response 07 and 16 bytes
     * @response: byte(n) - depending on type
     * Write additional information about selected song
     */
    private void oap_04_write_additional_info(byte rtype, int song_nr)
    {
        byte msg[] = {0x00, 0x0d, 0};
        byte msg02[] = {0x00, 0x0d, 0x02, 0, 0, 0, 0, 0, 0, 0, 0};
        msg[2] = rtype;

        switch (rtype)
        {
            case 0x02:
            {
                oap_04_write_cmd(msg02, 11);
            }
            break;

            case 0x03:
            case 0x04:
            {
                msg02[2] = rtype;
                oap_04_write_cmd(msg02, 7);
            }
            break;

            // TODO implement playlists
            case 0x05:
            {
                msg[2] = rtype;
                oap_04_write_string(msg, currentlyPlaying.getGenre());
            }
            break;

            // TODO implement playlists
            case 0x06:
            {
                msg[2] = rtype;
                oap_04_write_string(msg, currentlyPlaying.getComposer());
            }
            break;

            default:
                oap_04_write_return_code(0x000c, IPOD_ERROR);
        }
    }

    /**
     * @cmd 0x00 0x13
     * @response param(2)
     * iPod 3G 30GB:       0x01 0x02
     * iPod 4G 30GB:       0x01 0x09
     * iPod 5G 30GB:       0x01 0x09
     * iPod Touch 4G 10G:  0x01 0x0E
     * iPod Nano 4G:       0x01 0x0E
     */
    private void oap_04_write_model()
    {
        byte msg[] = {0x00, 0x13, 0x01, 0x0E};
        oap_04_write_cmd(msg);
    }

    /**
     * @cmd 0x00 0x15
     * @response string    iPod name as null terminated string
     */
    private void oap_04_write_ipod_name()
    {
        byte cmd[] = {0x00, 0x15};
        oap_04_write_string(cmd, ipod_name);
    }

    /**
     * @param rtype - could be one of the following:
     *              0x01 - Playlist
     *              0x02 - Artist
     *              0x03 - Album
     *              0x04 - Genre
     *              0x05 - Song
     *              0x06 - Composer
     *              this is response to 0x0018 command
     * @cmd 0x00 0x19
     * @response number(4) - count of the given type (count of tracks etc)
     */
    private void oap_04_write_type_count(int rtype)
    {
        byte b[]={0,0,0,1};
        if(rtype == 0x05)
        {
            int_to_byte_array(MediaControlLibrary.playlistOffset * 2, b);
        }
        // TODO get the actual counts
        // right now we say always 3 positions to implement next/prev actions
        byte msg[] = {0x00, 0x19, b[0], b[1], b[2], b[3]};
        oap_04_write_cmd(msg);
    }

    /**
     * @param rtype     - type to which we areplying to
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
        String str = "Unknown type";
        byte cmd[] = {0x00, 0x1B, 0x00, 0x00, 0x00, 0x00};
        int maxItemsInPlaylist=3; // FIXME

        if (pos_start > maxItemsInPlaylist  || pos_start + count > maxItemsInPlaylist )
        {
            oap_04_write_return_code(0x001C, IPOD_OUT_OF_RANGE);
            return;
        }

        for(int pos=pos_start;pos<pos_start+count;pos++)
        {
            // TODO implement playlists
            switch (rtype)
            {
                case 0x01: // Playlist
                    str = "Generic Playlist";
                    break;
                case 0x02: // Artist
                    str = currentlyPlaying.getArtist();
                    break;
                case 0x03: // Album
                    str = currentlyPlaying.getAlbum();
                    break;
                case 0x04: // Genre
                    str = currentlyPlaying.getGenre();
                    break;
                case 0x05: // Song
                    str = currentlyPlaying.getTrackName();
                    break;
                case 0x06: // Composer
                    str = currentlyPlaying.getComposer();
                    break;
                case 0x07: // Audiobook
                    str = "Audiobooks are not supported";
                    break;
                case 0x08: // Composer
                    str = "Podcasts are not supported";
                    break;
            }

            cmd[2] = (byte) ((pos >> 24) & 0xff);
            cmd[3] = (byte) ((pos >> 16) & 0xff);
            cmd[4] = (byte) ((pos >> 8) & 0xff);
            cmd[5] = (byte) (pos & 0xff);

            oap_04_write_string(cmd, str);
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
        int length = currentlyPlaying.getLength();
        int time = currentlyPlaying.getPositionMS();
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
        msg[10] = (byte) (currentlyPlaying.isPlaying() ? 0x01 : 0x02);
        oap_04_write_cmd(msg);
    }


    /**
     * @cmd 0x00 0x1F
     * @response position(4)    current position in playlist
     */
    private void oap_04_write_playlist_position()
    {
        byte b[]=new byte[4];
        int_to_byte_array(MediaControlLibrary.currentPlaylistPosition, b);
        // TODO implement playlists
        byte msg[] = {0x00, 0x1F, b[0], b[1], b[2], b[3]};
        oap_04_write_cmd(msg);
    }

    /**
     * @param song_number - song number to which we should provide information
     * @cmd 0x00 0x21
     * @response string     Title of song as null terminated string
     */
    private void oap_04_write_title(int song_number)
    {
        byte cmd[] = {0x00, 0x21};

        // TODO get the actual song number name, not currently playing
        oap_04_write_string(cmd, currentlyPlaying.getTrackName());
    }

    /**
     * @param song_number - song number to which we should provide information
     * @cmd 0x00 0x23
     * @response string     Artist returned as null terminated string
     */
    private void oap_04_write_artist(int song_number)
    {
        byte cmd[] = {0x00, 0x23};
        // TODO get the actual song number name, not currently playing
        oap_04_write_string(cmd, currentlyPlaying.getArtist());
    }


    /**
     * @param song_number - song number to which we should provide information
     * @cmd 0x00 0x25
     * @response string     Album returned as null terminated string
     */
    private void oap_04_write_album(int song_number)
    {
        byte cmd[] = {0x00, 0x25};
        // TODO get the actual song number name, not currently playing
        oap_04_write_string(cmd, currentlyPlaying.getAlbum());
    }

    /**
     * @cmd 0x00 0x27
     * @response number(4) time elapsed on current song
     */
    public void oap_04_write_elapsed_time()
    {
        if (!currentlyPlaying.isPlaying()) return;

        int pos = currentlyPlaying.getPositionMS();
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
    }

    /**
     * @cmd 0x00 0x27
     * @param pos - new position in playlist
     * @response number(4) time elapsed on current song
     */
    public void oap_04_write_track_status_changed(int pos)
    {
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
        byte msg[] = {0x00, 0x34, 0x00, 0x00, 0x00, 0x00, 0x01};
        msg[2] = (byte) ((DockingLogoView.IMAGE_MAX_RES_X >> 8) & 0xff);
        msg[3] = (byte) ((DockingLogoView.IMAGE_MAX_RES_X) & 0xff);
        msg[4] = (byte) ((DockingLogoView.IMAGE_MAX_RES_Y >> 8) & 0xff);
        msg[5] = (byte) ((DockingLogoView.IMAGE_MAX_RES_Y) & 0xff);
        oap_04_write_cmd(msg);
    }

    /**
     * @cmd 0x00 0x36
     * @response number(4)  number of songs in playlist
     */
    private void oap_04_write_playlist_song_count()
    {
        byte b[]=new byte[4];
        int_to_byte_array(MediaControlLibrary.playlistOffset*2, b);
        // TODO implement playlists
        // always say we have 3 songs to implement next/prev action
        byte msg[] = {0x00, 0x36, b[0], b[1], b[2], b[3]};
        oap_04_write_cmd(msg);
    }


    /**
     * @cmd 0x00 0x3A - colour version of 0x00 0x34
     * @response: number(2) - resolution X for mode 02
     * @response: number(2) - resolution Y for mode 02
     * @response: number(1) - always 02 - mode unknown
     * @response: number(2) - resolution X for mode 03
     * @response: number(2) - resolution Y for mode 03
     * @response: number(1) - always 02 - mode RGB_565 (2 bytes per pixel)
     * iPod Touch 4G response: 00 A6 00 4C 02 00 A6 00 4C 03
     */
    private void oap_04_write_screen_resolution_3A()
    {
        byte msg[] = {0x00, 0x3A, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x03};
        msg[7] = msg[2] = (byte) ((DockingLogoView.IMAGE_MAX_RES_X >> 8) & 0xff);
        msg[8] = msg[3] = (byte) ((DockingLogoView.IMAGE_MAX_RES_X) & 0xff);
        msg[7] = msg[4] = (byte) ((DockingLogoView.IMAGE_MAX_RES_Y >> 8) & 0xff);
        msg[10] = msg[5] = (byte) ((DockingLogoView.IMAGE_MAX_RES_Y) & 0xff);

        oap_04_write_cmd(msg);
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
    void oap_print_char(int rw, byte b, int num)
    {
        PodEmuLog.debug("Line " + line + ": len=" + line_cmd_len + " cnt=" + num + " " + String.format(": %s: %02X", (rw == READ ? "RCV" : "WRITE"), b));

        // TODO write char to logfile
        //write(fd_log, tmp, strlen(tmp));

    }


    /**
     * Transforms byte array to human readable form in hex format
     *
     * @param buf - buffer containing byte array
     * @param len - length of the byte array contained by buf
     * @return - resulting string
     */
    String oap_hex_to_str(byte buf[], int len)
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

        PodEmuLog.debug(tmp);

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

        PodEmuLog.debug("Line " + line + ": RAW MSG OUT: " + oap_hex_to_str(msg, msg.length));
        oap_print_podmsg(msg, false, false);

        SerialInterfaceBuilder serialInterfaceBuilder=new SerialInterfaceBuilder();
        serialInterfaceBuilder.getSerialInterface().write(msg, len + 5);
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

    public void update_currently_playing(PodEmuMessage msg)
    {
        PodEmuLog.debug("Updating currently playing...");

        currentlyPlaying.bulk_update(msg);
    }

    public PodEmuMessage get_currently_playing()
    {
        return currentlyPlaying;
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
        Message message = mHandler.obtainMessage(0);
        message.arg1 = 2; // indicate mode change message
        message.arg2 = ipod_mode;
        mHandler.sendMessage(message);
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
        if (b.length < 4)
        {
            PodEmuLog.error("byte array length is less then 4");
            return 0;
        }

        int i = ((b[0] & 0xff) << 24) | ((b[1] & 0xff) << 16) | ((b[2] & 0xff) << 8) | (b[3] & 0xff);

        return i;
    }
}

