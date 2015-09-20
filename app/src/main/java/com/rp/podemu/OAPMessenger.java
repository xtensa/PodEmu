package com.rp.podemu;


import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;

/**
 * Created by rp on 9/8/15.
 */
public class OAPMessenger
{
    private String TAG = "RPP OAP";
    private Context context;
    private static int READ=1;
    private static int WRITE=2;
    public static final int IPOD_MODE_SIMPLE=0x02;
    public static final int IPOD_MODE_AIR=0x04;
    public static final byte IPOD_SUCCESS=0x00;
    public static final byte IPOD_ERROR=0x02;
    public static final byte IPOD_OUT_OF_RANGE=0x04;
    private static Handler mHandler;

    private int line_cmd_pos = 0;
    private int line_cmd_len = 0;
    private boolean is_extended_image=false;
    private int line_ext_pos=2;
    private byte line_buf[] = new byte[6503]; // max should be 259 bytes
    private byte ext_image_buf[] = new byte[5];
    private byte unknown_var=0x00;
    private boolean polling_mode=false;
    private byte shuffle_mode=0x00;
    private byte repeat_mode=0x00;


    // definition of the in/out line if more than one instance of object created
    private static int line = 0;


    void setHandler(Handler handler)
    {
        this.mHandler=handler;
    }


    /**
     * The variables below represents the "iPod state" that is reported
     * to the dock station by variable commands
     */
    private static int last_cmd_return_code;
    private static String ipod_name="Android PodEmu";
                /**
                 * iPOD modes
                 * 0x00 - Mode Switching
                 * 0x01 - Voice Recorder (not supported)
                 * 0x02 - Simple Remote
                 * 0x03 - Request Mode Status
                 * 0x04 - Advanced Remote Mode (AiR)
                 */
    private static int ipod_mode = IPOD_MODE_SIMPLE;
    private static PodEmuMessage currentlyPlaying=new PodEmuMessage();


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
		/**
		 * additional bytes:
         *     2 for control bytes (0xFF 0x55)
		 *     1 for msg len
		 *     (not counted) 1 for msg mode
		 *     (not counted) 2 for command
		 *     (not counted) parameter
		 *     1 at the end for checksum
		 */
            line_cmd_len = (b&0xff) + 4;

            PodEmuLog.debug(String.format("Line " + line + ": MSG LEN: %d TOTAL LEN: %d", (b & 0xff), line_cmd_len));

            if(b == (byte)0x00)
            {
                /**
                 *
                 * this is potential extended image candidate
                 * extended image message has 2 or 3 bytes allocated to message
                 * byte 2 is treated as indicator of potential ext image msg
                 * length bytes are assumed to be 3 and 4
                 */
                is_extended_image=true;
                line_ext_pos = 2;
            }

        }

        if(is_extended_image)
        {
            if(line_ext_pos<=8)
                PodEmuLog.verbose(String.format("EXT MSG check - pos:%d, byte: 0x%02X", line_ext_pos, b));

            if (line_ext_pos == 3 || line_ext_pos==4)
            {
                  // remember bytes 3 and 4 in case it turn out to be ext image
                  ext_image_buf[line_ext_pos]=b;
            }
            // if byte 5 is not 0x04 then it is not ext image
            else if( line_ext_pos == 5 && b != (byte)0x04 ) is_extended_image=false;
            // if byte 6 is not 0x00 then it is not ext image
            else if( line_ext_pos == 6 && b != (byte)0x00 ) is_extended_image=false;
            // if byte 7 is not 0x32 then it is not ext image
            else if( line_ext_pos == 7 && b != (byte)0x32 ) is_extended_image=false;
            // extended image message detected
            else if( line_ext_pos == 8)
            {
                // assumptions: length is in bytes 3 and 4 which gives maximum length of 65025
                (line_cmd_len) = (((ext_image_buf[3] & 0xff) <<8) | (ext_image_buf[4] & 0xff)) + 6;
                (line_cmd_pos) = 8;
                // now putting the bytes in their "correct" place
                line_buf[0] = (byte)0xff;
                line_buf[1] = (byte)0x55;
                line_buf[2] = (byte)0x00;
                line_buf[3] = ext_image_buf[3];
                line_buf[4] = ext_image_buf[4];
                line_buf[5] = (byte)0x04;
                line_buf[6] = (byte)0x00;
                line_buf[7] = (byte)0x32;

                PodEmuLog.debug(String.format("Line %d: Extended image message detected!!!", line));
                // from now and on message will be treated normally
            }

            line_ext_pos++;
        }


        if (line_cmd_pos == 0 && b != (byte)0xff)
        {
            PodEmuLog.debug("Line " + line + ": ERROR: first byte is not 0xFF. Received 0x" + String.format("%02X", b));
            return -1;
        }

        if (line_cmd_pos == 1 && b != (byte)0x55)
        {
            PodEmuLog.debug("Line " + line + ": ERROR: second byte is not 0x55. Received 0x" + String.format("%02X", b));
            return -1;
        }

        if (!is_extended_image && line_cmd_len > 259)
        {
            PodEmuLog.debug("Line " + line + ": ERROR: message length cannot exceed 259 bytes");
            return -1;
        }

        if (is_extended_image && line_cmd_len > 65025+6)
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

            PodEmuLog.debug("Line " + line + ": RAW MSG  IN: " +
                    oap_hex_to_str(line_buf, line_cmd_len));

            oap_print_podmsg(line_buf, true, (line_cmd_len>7 && is_extended_image));

            checksum = oap_calc_checksum(line_buf, line_cmd_len);
            if (line_buf[line_cmd_len - 1] != checksum)
            {
                PodEmuLog.debug("Line " + line + String.format(": ERROR: checksum error. Received: %02X  Should be: %02X", line_buf[line_cmd_len - 1], checksum));
                //if(line_cmd_len==4 && is_extended_image) is_extended_image=false;
            }
            else
            {
                // Received message is OK. We cen process it and prepare the reply
                //Log.d(TAG, "Line " + line + ": ERROR: checksum OK. Received: %d  Should be: %d", line, line_buf[*line_cmd_len-1], checksum);
                PodEmuLog.debug("Line " + line + ": Checksum OK");

                oap_process_msg(line_buf,line_cmd_len, (line_cmd_len>7 && is_extended_image));
            }

            msg_len = line_cmd_len;
            line_cmd_pos = 0;
            line_cmd_len = 0;
            if(line_ext_pos>7) is_extended_image=false;

            return msg_len;
        }

        return 0;
    }


    public void oap_process_msg(byte[] line_buf, int line_cmd_len, boolean is_ext)
    {
        int pos_shift=0;
        int len;

        PodEmuLog.debug("Processing message started");

        if(is_ext)
        {
            len = ((line_buf[3] & 0xff) << 8) | (line_buf[4] & 0xff);
            // if we are dealing with extended message then we need to count 2 additional b
            pos_shift+=2;
        }
        else
        {
            len = (line_buf[2] & 0xff);
        }

        int mode=(line_buf[3+pos_shift] & 0xff);
        int cmd=(line_buf[4+pos_shift] & 0xff);

        // second byte for command should not necessary be sent
        if(line_cmd_len-1>5+pos_shift)
        {
            cmd = (cmd << 8) + (line_buf[5 + pos_shift] & 0xff);
        }

        byte params[];
        if(line_cmd_len>7+pos_shift)
        {
            params=new byte[line_cmd_len - 7 - pos_shift];
            // copy params from buf to separate variable
            for(int i=6;i<line_cmd_len-1-pos_shift;i++)
            {
                params[i-6]=line_buf[i+pos_shift];
            }
        }
        else
        {
            // just for checker not to throw errors:
            params=new byte[1];
            params[0]=0;
        }

        PodEmuLog.debug(String.format("Received - Ext: %d Len: %d Mode: 0x%02X, CMD: 0x%04X", (is_ext?1:0), len, mode, cmd));

        if(mode==0x00) // mode switch requested
        {
            if(cmd==0x0104 || (cmd==0x05 && line_cmd_len==6)) // AiR mode requested
            {
                ipod_mode=IPOD_MODE_AIR;
            }
            else if(cmd==0x0102 || (cmd==0x06 && line_cmd_len==6)) // simple mode requested
            {
                ipod_mode=IPOD_MODE_SIMPLE;
            }
            else if(cmd==0x03 && line_cmd_len==6) // current mode requested
            {
                oap_write_current_mode(); // writing current mode
            }
            else if(cmd==0x09 && line_cmd_len==6) // unknown TODO debug more
            {
                byte msg[]={0x0a, 0x01, 0x00, 0x04};
                oap_write_cmd(msg,msg.length,true);
            }
            else
            {
                PodEmuLog.error(String.format("Mode switching: unrecognized command 0x%04X received", cmd));
            }
        }

        if(mode==0x04 && ipod_mode==0x04) // AiR mode
        {
            switch(cmd)
            {

                /**
                 * cmd: 0x00 0x02
                 * @param None
                 * NCU, simple ping-request ?
                 * should get response FF FF FF FF 00 00 00 00
                 */
                case 0x0002: oap_write_ping_response(); break;

                /**
                 * @cmd: 0x00 0x09
                 * @param None
                 * NCU, requests flag set by command 0x00 0x0b
                 */
                case 0x0009: oap_write_unknown_var(); break;

                /**
                 * @cmd 0x00 0x0b
                 * @param [1] Parameter is either 0x00 or 0x01.
                 * Get success/failure response
                 */
                case 0x000b:
                {
                    if(params.length<1)
                    {
                        oap_write_return_code(cmd,IPOD_OUT_OF_RANGE);
                        break;
                    }
                    unknown_var=params[0];
                    oap_write_return_code(cmd,IPOD_SUCCESS);
                } break;

                /**
                 * TODO debug
                 * @cmd 0x00 0x0c
                 * @param type(1)
                 *          0x01 - unknown, gives error
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
                    if(params.length<7)
                    {
                        oap_write_return_code(cmd,IPOD_OUT_OF_RANGE);
                        break;
                    }
                    int song_nr=((params[1] & 0xff)<<24) | ((params[2] & 0xff)<<16) | ((params[3] & 0xff)<<8) | (params[1] & 0xff);
                    oap_write_additional_info(params[0], song_nr);
                } break;

                /**
                 * @cmd 0x00 0x12
                 * @param None
                 * Thought to be "Get iPod Type / Size"
                 */
                case 0x0012: oap_write_model(); break;

                /**
                 * @cmd 0x00 0x14
                 * @param None
                 * Get iPod Name
                 */
                case 0x0014: oap_write_ipod_name(); break;

                /**
                 * @cmd 0x00 0x16
                 * @param None
                 * Switch to main library playlist (playlist 0)
                 */
                case 0x0016:
                {
                    // TODO implement libraries and playlists

                    // just in case writing success retval
                    oap_write_return_code(cmd, IPOD_SUCCESS);
                } break;

                /**
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
                 */
                case 0x0017:
                {
                    if(params.length<5)
                    {
                        oap_write_return_code(cmd,IPOD_OUT_OF_RANGE);
                        break;
                    }
                    // TODO implement libraries and playlists

                    // just in case writing success retval
                    oap_write_return_code(cmd, IPOD_SUCCESS);
                } break;

                /**
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
                    if(params.length<1)
                    {
                        oap_write_return_code(cmd,IPOD_OUT_OF_RANGE);
                        break;
                    }
                    oap_write_type_count(params[0]);
                } break;

                /**
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
                    if(params.length<9)
                    {
                        oap_write_return_code(cmd,IPOD_OUT_OF_RANGE);
                        break;
                    }
                    int pos_start = ((params[1] & 0xff) << 24) | ((params[2] & 0xff) << 16) | ((params[3] & 0xff) << 8) | (params[4] & 0xff);
                    int pos_end   = ((params[5] & 0xff) << 24) | ((params[6] & 0xff) << 16) | ((params[7] & 0xff) << 8) | (params[8] & 0xff);
                    oap_write_type_names(params[0], pos_start, pos_end);
                } break;

                /**
                 * @cmd 0x00 0x1C
                 * @param none
                 * Get time and status info
                 */
                case 0x001C: oap_write_info(); break;

                /**
                 * @cmd 0x00 0x1E
                 * @param none
                 * Get current position in playlist
                */
                case 0x001E: oap_write_playlist_position(); break;

                /**
                 * @cmd 0x00 0x20
                 * @param number(4)
                 * Get title of a song number
                 */
                case 0x0020:
                {
                    if(params.length<4)
                    {
                        oap_write_return_code(cmd,IPOD_OUT_OF_RANGE);
                        break;
                    }
                    int song_number = ((params[0] & 0xff) << 24) | ((params[1] & 0xff) << 16) | ((params[2] & 0xff) << 8) | (params[3] & 0xff);
                    oap_write_title(song_number);
                } break;

                /**
                 * @cmd 0x00 0x22
                 * @param number(4)
                 * Get artist of a song number
                 */
                case 0x0022:
                {
                    if(params.length<4)
                    {
                        oap_write_return_code(cmd,IPOD_OUT_OF_RANGE);
                        break;
                    }
                    int song_number = ((params[0] & 0xff) << 24) | ((params[1] & 0xff) << 16) | ((params[2] & 0xff) << 8) | (params[3] & 0xff);
                    oap_write_artist(song_number);
                } break;

                /**
                 * @cmd 0x00 0x24
                 * @param number(4)
                 * Get album of a song number
                 */
                case 0x0024:
                {
                    if(params.length<4)
                    {
                        oap_write_return_code(cmd,IPOD_OUT_OF_RANGE);
                        break;
                    }
                    int song_number = ((params[0] & 0xff) << 24) | ((params[1] & 0xff) << 16) | ((params[2] & 0xff) << 8) | (params[3] & 0xff);
                    oap_write_album(song_number);
                } break;

                /**
                 * @cmd 0x00 0x26
                 * @param pollingmode(1) Polling Mode:
                 *                       0x01 = Start
                 *                       0x00 = Stop
                 * Polling causes the return command 0x00 0x27 to be sent every 500 milliseconds.
                 */
                case 0x0026:
                {
                    if(params.length<1)
                    {
                        oap_write_return_code(cmd,IPOD_OUT_OF_RANGE);
                        break;
                    }
                    enable_polling_mode(params[0]!=0);
                } break;

                /**
                 * @cmd 0x00 0x28
                 * @param number(4)
                 * Execute playlist and jump to specified songnumber.
                 * 0xFFFFFFFF will always be start of playlist even when shuffle is on.
                 */
                case 0x0028:
                {
                    if(params.length<4)
                    {
                        oap_write_return_code(cmd,IPOD_OUT_OF_RANGE);
                        break;
                    }
                    // TODO implement playlist
                    oap_write_return_code(cmd, IPOD_SUCCESS);
                    break;
                }

                /**
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
                    if(params.length<1)
                    {
                        oap_write_return_code(cmd,IPOD_OUT_OF_RANGE);
                        break;
                    }

                    byte retval=IPOD_SUCCESS;
                    switch (params[0])
                    {
                        case 0x01: MediaControlLibrary.action_play_pause(); break;
                        case 0x02: MediaControlLibrary.action_stop(); break;
                        case 0x03: MediaControlLibrary.action_next(); break;
                        case 0x04: MediaControlLibrary.action_prev(); break;
                        case 0x05:
                        case 0x06:
                        case 0x07:
                            break;
                        default: retval=IPOD_ERROR;
                    }
                    oap_write_return_code(cmd,retval);
                    break;
                }

                /**
                 * @cmd 0x00 0x2C
                 * @param none
                 * Get shuffle mode
                 */
                case 0x002C: oap_write_shuffle_mode(); break;

                /**
                 * @cmd 0x00 0x2E
                 * @param shuffle(1) Sets the shuffle mode:
                 *                      0x00 = Off
                 *                      0x01 = Songs
                 *                      0x02 = Albums
                 */
                case 0x002E:
                {
                    if(params.length<1 || params[0]<0x00 || params[0]>0x02)
                    {
                        oap_write_return_code(cmd,IPOD_OUT_OF_RANGE);
                        break;
                    }

                    // TODO implement shuffle
                    shuffle_mode=params[0];
                    oap_write_return_code(cmd, IPOD_SUCCESS);
                } break;

                /**
                 * @cmd 0x00 0x2F
                 * @param none
                 * Get Repeat Mode
                 */
                case 0x002F: oap_write_repeat_mode(); break;

                /**
                 * @cmd 0x00 0x31
                 * @param repeatmode(1) Sets the repeat mode:
                 *                          0x00 = Off
                 *                          0x01 = Songs
                 *                          0x02 = Albums
                 */
                case 0x0031:
                {
                    if(params.length<1 || params[0]<0x00 || params[0]>0x02)
                    {
                        oap_write_return_code(cmd,IPOD_OUT_OF_RANGE);
                        break;
                    }

                    // TODO implement repeat mode
                    repeat_mode=params[0];
                    oap_write_return_code(cmd, IPOD_SUCCESS);
                } break;

                /**
                 * @cmd 0x00 0x32
                 * @param Picture Blocks
                 * upload a picture (see below)
                 */
                case 0x0032: oap_receive_image_msg(params, len-3); break;

                /**
                 * @cmd 0x00 0x33
                 * @param none
                 * Get max screen size for picture upload
                 */
                case 0x0033: oap_write_screen_resolution_34(); break;

                /**
                 * @cmd 0x00 0x35
                 * @param none
                 * Get number of songs in playlist
                 */
                case 0x0035: oap_write_playlist_song_count(); break;

                /**
                 * @cmd 0x00 0x37
                 * @param number(4) jump to specified song number in playlist
                 */
                case 0x0037:
                {
                    if(params.length<4)
                    {
                        oap_write_return_code(cmd,IPOD_OUT_OF_RANGE);
                        break;
                    }
                    // TODO implement playlist
                    oap_write_return_code(cmd, IPOD_SUCCESS);
                } break;

                /**
                 * TODO debug more
                 * @cmd 0x00 0x38 - action - get success response
                 * @param type(1) ?
                 * @param number(4) - song number?
                 * @param unknown(1) ?
                 * NCU
                 */
                case 0x0038:
                {
                    if(params.length<6)
                    {
                        oap_write_return_code(cmd,IPOD_OUT_OF_RANGE);
                        break;
                    }
                    // TODO debug
                    oap_write_return_code(cmd, IPOD_SUCCESS);
                } break;

                /**
                 * @cmd 0x00 0x39 - response is 3A
                 * @param NO PARAMS
                 * NCU
                 */
                case 0x0039: oap_write_screen_resolution_3A(); break;
            }

        }

    }


    /**
     * @cmd 0x00 0x00
     * @response Result(1)
     * @response Command(2)     NCU, not often used.
     */

    /**
     * @cmd 0x00 0x01
     * @response Result(1)
     *                  0x00 = Success
     *                  0x02 = Failure
     *                  0x04 = Exceeded limit/Byte Count Wrong
     *                  0x05 = Is a Response Not a Command
     * @response Command(2)
     *                  The command code this is responding to.
     * Feedback on command just executed.
     */
    private void oap_write_return_code(int cmd, byte result)
    {
        byte msg[]={0x00, 0x01, result, 0, 0};
        msg[3]=(byte)((cmd>>8) & 0xff);
        msg[4]=(byte)(cmd & 0xff);
        oap_write_cmd(msg);
    }

    /**
     * @cmd 0x00 0x03
     * @response byte(8) - response to ping
     * 0xFF 0xFF 0xFF 0xFF 0x00 0x00 0x00 0x00
     */
    private void oap_write_ping_response()
    {
        byte msg[]={0x00, 0x03, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, 0x00, 0x00, 0x00, 0x00};
        oap_write_cmd(msg);
    }

    /**
     * @cmd 0x00 0x0a
     * @response byte(1)      0x00 or 0x01 depending on flag status
     */
    private void oap_write_unknown_var()
    {
        byte msg[]={0x00, 0x0A, unknown_var};
        oap_write_cmd(msg);
    }

    /**
     * TODO debug more
     * @cmd 0x00 0x0d
     * @response: type(1):
     *          0x01 - unknown, gives error
     *          0x02 - unknown, always gives response 02 00 00 00 00 00 00 00 00
     *          0x03 - unknown, always gives response 03 00 00 00 00
     *          0x04 - unknown, always gives response 04 00 00 00 00
     *          0x05 - Genre
     *          0x06 - Composer
     *          0x07 - unknown, gives response 07 and 16 bytes
     * @response: byte(n) - depending on type
     * Write additional information about selected song
     */
    private void oap_write_additional_info(byte rtype, int song_nr)
    {
        byte msg[]={0x00, 0x0d, 0};
        byte msg02[]={0x00, 0x0d, 0x02, 0, 0, 0, 0, 0, 0, 0, 0};
        msg[2] = rtype;

        switch(rtype)
        {
            case 0x02:
            {
                oap_write_cmd(msg02,11);
            } break;

            case 0x03:
            case 0x04:
            {
                msg02[2]=rtype;
                oap_write_cmd(msg02,7);
            } break;

            // TODO implement playlists
            case 0x05:
            {
                msg[2]=rtype;
                oap_write_string(msg, currentlyPlaying.getGenre());
            } break;

            // TODO implement playlists
            case 0x06:
            {
                msg[2]=rtype;
                oap_write_string(msg, currentlyPlaying.getComposer());
            } break;

            default:
                oap_write_return_code(0x000c, IPOD_ERROR);
        }
    }

    /**
     * @cmd 0x00 0x13
     * @response param(2)
     *          iPod 3G 30GB:       0x01 0x02
     *          iPod 4G 30GB:       0x01 0x09
     *          iPod 5G 30GB:       0x01 0x09
     *          iPod Touch 4G 10G:  0x01 0x0E
     *          iPod Nano 4G:       0x01 0x0E
     */
    private void oap_write_model()
    {
        byte msg[]={0x00, 0x13, 0x01, 0x0E};
        oap_write_cmd(msg);
    }

    /**
     * @cmd 0x00 0x15
     * @response string  	iPod name as null terminated string
     */
    private void oap_write_ipod_name()
    {
        byte cmd[]={0x00,0x15};
        oap_write_string(cmd, ipod_name);
    }

    /**
     * @cmd 0x00 0x19
     * @response number(4) - count of the given type (count of tracks etc)
     * @param rtype - could be one of the following:
     *              0x01 - Playlist
     *              0x02 - Artist
     *              0x03 - Album
     *              0x04 - Genre
     *              0x05 - Song
     *              0x06 - Composer
     * this is response to 0x0018 command
     */
    private void oap_write_type_count(int rtype)
    {
        // TODO get the actual counts
        byte msg[]={0x00, 0x19, 0x00, 0x00, 0x00, 0x01};
        oap_write_cmd(msg);
    }

    /**
     * @cmd 0x00 0x1B
     * @response number(4) Number is the offset of item from 0.
     * @response string  	String is the name of the item.
     *                  Playlist 0 is the main library and has the same name as iPod itself
     *                  (same as returned for 0x0014)
     */
    private void oap_write_type_names(int rtype, int pos_start, int pos_end)
    {
        String str="Unknown type";
        byte cmd[]={0x00,0x1B, 0x00};

        if(pos_start>1 || pos_end>1 || pos_end<pos_start)
        {
            oap_write_return_code(0x001C,IPOD_OUT_OF_RANGE);
            return;
        }

        // TODO implement playlists
        switch(rtype)
        {
            case 0x01: // Playlist
                str="Generic Playlist";
                break;
            case 0x02: // Artist
                str=currentlyPlaying.getArtist();
                break;
            case 0x03: // Album
                str=currentlyPlaying.getAlbum();
                break;
            case 0x04: // Genre
                str=currentlyPlaying.getGenre();
                break;
            case 0x05: // Song
                str =currentlyPlaying.getTrackName();
                break;
            case 0x06: // Composer
                str=currentlyPlaying.getComposer();
                break;
        }
        oap_write_string(cmd, str);
    }

    /**
     * @cmd 0x00 0x1D
     * @response length(4)  Track length in milliseconds
     * @response time(4)    Elapsed time in milliseconds
     * @response status(1)  Status:
     *                      0x00 = Stop
     *                      0x01 = Playing
     *                      0x02 = Paused
     */
    private void oap_write_info()
    {
        byte msg[]=new byte[11];
        int length=currentlyPlaying.getLength();
        int time=currentlyPlaying.getPositionMS();
        msg[0]=0x00;
        msg[1]=0x1D;
        msg[2]=(byte)((length >> 24) & 0xff);
        msg[3]=(byte)((length >> 16) & 0xff);
        msg[4]=(byte)((length >> 8) & 0xff);
        msg[5]=(byte)((length) & 0xff);
        msg[6]=(byte)((time >> 24) & 0xff);
        msg[7]=(byte)((time >> 16) & 0xff);
        msg[8]=(byte)((time >> 8) & 0xff);
        msg[9]=(byte)((time) & 0xff);
        msg[10]=(byte)(currentlyPlaying.isPlaying()?0x01:0x02);
        oap_write_cmd(msg);
    }


    /**
     * @cmd 0x00 0x1F
     * @response position(4)    current position in playlist
     */
    private void oap_write_playlist_position()
    {
        // TODO implement playlists
        byte msg[]={0x00, 0x1F, 0x00, 0x00, 0x00, 0x00};
        oap_write_cmd(msg);
    }

    /**
     * @cmd 0x00 0x21
     * @response string     Title of song as null terminated string
     */
    private void oap_write_title(int song_number)
    {
        byte cmd[]={0x00, 0x21};

        // TODO get the actual song number name, not currently playing
        oap_write_string(cmd, currentlyPlaying.getTrackName());
    }

    /**
     * @cmd 0x00 0x23
     * @response string     Artist returned as null terminated string
     */
    private void oap_write_artist(int song_number)
    {
        byte cmd[]={0x00, 0x23};
        // TODO get the actual song number name, not currently playing
        oap_write_string(cmd, currentlyPlaying.getArtist());
    }


    /**
     * @cmd 0x00 0x25
     * @response string     Album returned as null terminated string
     */
    private void oap_write_album(int song_number)
    {
        byte cmd[]={0x00, 0x25};
        // TODO get the actual song number name, not currently playing
        oap_write_string(cmd, currentlyPlaying.getAlbum());
    }

    /**
     * @cmd 0x00 0x27
     * @response number(4) time elapsed on current song
     */
    public void oap_write_elapsed_time()
    {
        if(!currentlyPlaying.isPlaying()) return;

        int pos=currentlyPlaying.getPositionMS();
        byte cmd[]={
                0x00,
                0x27,
                (byte)((pos >> 24) & 0xff),
                (byte)((pos >> 16) & 0xff),
                (byte)((pos >> 8) & 0xff),
                (byte) (pos & 0xff)
        };
        oap_write_cmd(cmd);
    }


    /**
     * @cmd 0x00 0x2D
     * @response shuffle(1) Returns current shuffle mode:
     *                          0x00 = Off
     *                          0x01 = Songs
     *                          0x02 = Albums
     */
    private void oap_write_shuffle_mode()
    {
        // TODO implement shuffle
        byte msg[]={0x00, 0x2D, shuffle_mode};
        oap_write_cmd(msg);
    }

    /**
     * @cmd 0x00 0x30
     * @response repeatmode(1) Returns current repeat mode:
     *                          0x00 = Off
     *                          0x01 = Songs
     *                          0x02 = Albums
     */
    private void oap_write_repeat_mode()
    {
        // TODO implement repeat mode
        byte msg[]={0x00, 0x30, repeat_mode};
        oap_write_cmd(msg);
    }



    /**
     * @cmd 0x00 0x34
     * Return screen size:
     *           Gen3: 120 * 65
     *                 0x00 0x78 0x00 0x41 0x01
     *           Gen5: 310 * 168
     *                 0x01 0x36 0x00 0xA8 0x01
     *           iPod Touch 4G:
     *                 0x00 0xA6 0x00 0x4C 0x01
     * @response: number(2) - resolution X for mode 02
     * @response: number(2) - resolution Y for mode 02
     * @response: number(1) - mode, always 01 which indicates greyscale mode, 2 bits per pixel
     */
    private void oap_write_screen_resolution_34()
    {
        byte msg[]={0x00, 0x34, 0x00, 0x00, 0x00, 0x00, 0x01};
        msg[2]=(byte)((DockingLogoView.IMAGE_RES_X>>8) & 0xff);
        msg[3]=(byte)((DockingLogoView.IMAGE_RES_X) & 0xff);
        msg[4]=(byte)((DockingLogoView.IMAGE_RES_Y>>8) & 0xff);
        msg[5]=(byte)((DockingLogoView.IMAGE_RES_Y) & 0xff);
        oap_write_cmd(msg);
    }

    /**
     * @cmd 0x00 0x36
     * @response number(4)  number of songs in playlist
     */
    private void oap_write_playlist_song_count()
    {
        // TODO implement playlists
        byte msg[]={0x00, 0x36, 0x00, 0x00, 0x00, 0x01};
        oap_write_cmd(msg);
    }


    /**
     * @cmd 0x00 0x3A - colour version of 0x00 0x34
     * @response:   number(2) - resolution X for mode 02
     * @response:   number(2) - resolution Y for mode 02
     * @response:   number(1) - always 02 - mode unknown
     * @response:   number(2) - resolution X for mode 03
     * @response:   number(2) - resolution Y for mode 03
     * @response:   number(1) - always 02 - mode RGB_565 (2 bytes per pixel)
     * iPod Touch 4G response: 00 A6 00 4C 02 00 A6 00 4C 03
     *
     */
    private void oap_write_screen_resolution_3A()
    {
        byte msg[]={0x00, 0x3A, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x03};
        msg[7] =msg[2]=(byte)((DockingLogoView.IMAGE_RES_X>>8) & 0xff);
        msg[8] =msg[3]=(byte)((DockingLogoView.IMAGE_RES_X) & 0xff);
        msg[7] =msg[4]=(byte)((DockingLogoView.IMAGE_RES_Y>>8) & 0xff);
        msg[10]=msg[5]=(byte)((DockingLogoView.IMAGE_RES_Y) & 0xff);

        oap_write_cmd(msg);
    }



    /**
     * Concatenates msg and str into byte[] array and returns the result
     * @param msg - leading bytes to be added to the string
     * @param str - string to be added to the message
     * @return - concatenated byte[] array
     */
    private byte[] oap_build_ipod_msg(byte[] msg, String str)
    {
        // first adding leading spaces to later store msg in it
        for(int i=0;i<msg.length;i++)
        {
            str=" "+str;
        }
        //adding trailing space to later store 0x00 in it
        str+=" ";

        byte new_msg[]=str.getBytes();
        for(int i=0;i<msg.length;i++)
        {
            new_msg[i] = msg[i];
        }
        new_msg[str.length()-1]=0x00;
        return new_msg;
    }

    private void oap_write_string(byte[] msg, String str)
    {
        oap_write_cmd(oap_build_ipod_msg(msg, str));
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
        return (byte) checksum;
    }


    void oap_print_char(int rw, byte b, int num)
    {
        PodEmuLog.verbose("Line " + line + ": len=" + line_cmd_len + " cnt=" + num + " " + String.format(": %s: %02X", (rw == READ ? "RCV" : "WRITE"), b));

        // TODO write char to logfile
        //write(fd_log, tmp, strlen(tmp));

    }



    void oap_print_msg(String str)
    {
        // TODO get current time and add to printed string
        //long int ms = tp.tv_sec * 1000 + tp.tv_usec / 1000;

        PodEmuLog.debug(str);

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
        String str="";
        //printw("LEN:(%d)",len);
        for(j=0;j<len;j++)
        {
            str+=String.format(" %02X", buf[j]);
        }
        return str;
    }


    /**
     *
     * @param msg
     * @param in_out - true=IN, flase=OUT
     * @param is_ext
     * @return
     */
    int oap_print_podmsg(byte msg[], boolean in_out, boolean is_ext)
    {
        int j, pos_shift=0, len;
        if(is_ext)
        {
            len = ((msg[3] & 0xff) << 8) | ((msg[4] & 0xff));
            // if we are dealing with extended message then we need to count 2 additional b
            pos_shift+=2;
        }
        else
        {
            len = (msg[2] & 0xff);
        }
        String tmp;

        if(in_out) tmp="IN  "; else tmp="OUT ";

        if(len>0) tmp+=String.format("|  %02X  ", msg[3+pos_shift]); else tmp+="|      ";
        if(len>1) tmp+=String.format("|  %02X ", msg[4+pos_shift]); else tmp+="|      ";
        if(len>2) tmp+=String.format("%02X  ", msg[5+pos_shift]); else tmp+="    ";
        tmp+="|  ";
        // 3 bytes are for mode and command length
        for(j=6;j<len+6-3;j++)
        {
            tmp+=String.format("%02X ", msg[j+pos_shift]);
        }
        tmp+=" ";
        if(len>3)
        {
            byte tmpmsg[]=new byte[len-3];
            for(j=0;j<len-3;j++)
            {
                tmpmsg[j]=msg[j+6+pos_shift];
            }
            String rawstr=new String(tmpmsg);
            tmp+="[" + rawstr + "]";
        }

        oap_print_msg(tmp);

        return 0;
    }


    /**
     * builds iPod serial message and writes it to serial line
     * @param bytes - command and parameters
     * @param len - total length of command+parameters
     * @param mode_switch - if true, use 0x00 instead of current mode
     */
    private void oap_write_cmd(byte bytes[], int len, boolean mode_switch)
    {
        byte msg[]=new byte[len+5];
        int chksum, i;


        if(len>254)
        {
            PodEmuLog.error("ERROR: Message length cannot be greater than 255");
            return;
        }

        msg[0]=(byte)0xFF;
        msg[1]=(byte)0x55;
        msg[2]=(byte)(len+1);
        msg[3]=(byte)(mode_switch?0x00:ipod_mode);

        chksum=msg[2]+msg[3];
        for(i=0;i<len;i++)
        {
            msg[i+4]=bytes[i];
            chksum+=bytes[i];
        }
        chksum&=0xff;
        chksum=0x100-chksum;
        msg[i+4]=(byte)chksum;

        PodEmuLog.debug("Line " + line + ": RAW MSG OUT: " + oap_hex_to_str(msg, msg.length));
        oap_print_podmsg(msg, false, false);

        SerialInterface serialInterface=new SerialInterface_USBSerial();
        serialInterface.write(msg, len + 5);
    }

    private void oap_write_cmd(byte bytes[], int len)
    {
        oap_write_cmd(bytes,len,false);
    }

    private void oap_write_cmd(byte bytes[])
    {
        oap_write_cmd(bytes,bytes.length);
    }

    private void oap_write_current_mode()
    {
        byte msg[]=new byte[2];
        msg[0]=0x04;
        msg[1]=(byte)ipod_mode;
        oap_write_cmd(msg,2,true);
    }

    public void update_currently_playing(PodEmuMessage msg)
    {
        currentlyPlaying.setIsPlaying(msg.isPlaying());
        currentlyPlaying.setTimeSent(msg.getTimeSent());
        currentlyPlaying.setAction(msg.getAction());
        currentlyPlaying.setLength(msg.getLength());
        currentlyPlaying.setTrackID(msg.getTrackID());
        currentlyPlaying.setTrackName(msg.getTrackName());
        currentlyPlaying.setAlbum(msg.getAlbum());
        currentlyPlaying.setArtist(msg.getArtist());
        currentlyPlaying.setPositionMS(msg.getPositionMS());
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
     * @param on_off - enable if true, disable if false
     */
    public void enable_polling_mode(boolean on_off)
    {
        polling_mode=on_off;

        // write back success return code
        oap_write_return_code(0x0026,IPOD_SUCCESS);
    }


    public class PictureBlock
    {
        public byte data[];
        public int len;

        PictureBlock(byte[] d, int l)
        {
            data=d;
            len=l;
        }
    };

    private void oap_receive_image_msg(byte[] data, int len)
    {
        PictureBlock pictureBlock=new PictureBlock(data, len);

        Message message = mHandler.obtainMessage(0);
        message.arg1 = 1; //indication that we are sending a picture block
        message.obj=pictureBlock;
        mHandler.sendMessage(message);

        byte msg[]={0x00, 0x01, 0x00, 0x00, 0x32};
        oap_write_cmd(msg);
    }

    public boolean getPollingMode()
    {
        return polling_mode;
    }

}


