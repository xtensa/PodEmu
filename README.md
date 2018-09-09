## About

PodEmu is Android application that allows you to connect your Android device to iPod docking station or your car audio system. PodEmu supports both AiR (Advanced) and Simple modes so you can control your Android music app directly from docking station or from your cars steering wheel. PodEmu uses serial protocol to communicate with iPod docking station so it will work only with "old" 30-pin iPod connectors. Lightning interface is not supported.

This application is very similar to PodMode application that already [exists out there](http://forum.xda-developers.com/showthread.php?t=2220108). So why build new application? There are 3 (well, maybe 4) simple reasons:
 1. PodMode didn't work with my car
 2. PodMode is not open source (so I couldn't fix point 1. At least source code was not available at the moment I started this project. Now PodMode author released the code)
 3. PodMode is not maintained anymore (so author wouldn't fix point 1)
 4. for fun... :)

XDA Developers discussion about this app [is here ](http://forum.xda-developers.com/android/apps-games/app-podemu-connect-android-to-30pin-t3234840)
  
PodEmu in action:

[![PodEmu in action](/screenshots/Screenshot_07.png)](https://youtu.be/_egRqgbt9GE)

## Requirements

 - Android device with USB host support (USB host support not required if you are using FT311D or FT312D chips)
 - DIY cable. Unfortunately there is no ready to use cables available on the market so you need some soldering skills to assemble your own cable. All details on how to do that could be found on [this XDA developers thread](http://forum.xda-developers.com/showthread.php?t=2220108).
 
## Features
 
 - No root required
 - Display track/time information for currently playing song on your docking station or car audio system display.
 - Control your favourite Android music app (eg. Spotify) directly from car's steering wheel or docking station remote control
 - Download docking station color image to android device. Car audios usually have this feature.
 - wide variaty of serial devices are supported (see detailed list below).
 - easy to use debug information gathering. If for some reason you car is not supported it is not a problem - using 2 clicks gather all necessary debug information and send it to the developer.
 - support for Bluetooth serial devices. You can assemble the dongle and connect your android completely wirelesly.
 - Entirely Open Source :) You can modify and redistribute as long as you comply with GPLv3.
 
## Screenshots

<table>
<tr><td width=300>PodEmu with cable not connected</td>
<td  width=300>PodEmu with cable connected and connection with the car established</td>
</tr>

<tr>
<td align="center"><img width="250" src="/screenshots/Screenshot_01.png?raw=true" /></td>
<td align="center"><img width="250" src="/screenshots/Screenshot_03.png?raw=true" /></td>
</tr>

<tr><td width=300>PodEmu settings (part 1)</td>
<td  width=300>PodEmu  settings (part 2)</td>
</tr>


<tr>
<td align="center"><img width="250" src="/screenshots/Screenshot_05.png?raw=true" /></td>
<td align="center"><img width="250" src="/screenshots/Screenshot_06.png?raw=true" /></td>
</tr>

<tr>
<td align="center"><img width="250" src="/screenshots/PodEmu%202.03%20BT.png?raw=true" /></td>
</tr>

</table>

## Hardware

To use PodEmu with your car you will need to connect it to Android. There two communication channels that you need to connect: AUDIO channel (obvious) and DATA channel. Both of them can be connected by wires or with use of Bluetooth modules. So there multiple combinations and setups. 

Audio channel can be connected directly, by wires, or with use of BT module that supports A2DP profile, for example XS3868, RN52, BK8000L.

Data channel could be connected by wires. In this case you will need any USB-to-serial interface like FT232R, FT231X, FT311D, FT312D, PL2303, CP2102, CP2105, CP2108 or CP2110. Alternatively, you can also connect it with any BT module that supports SPP profile (except BLE devices which are too slow). For BT you can use modules like HC05, HC06, RN52.

Obviously you will also need to power all the modules up. There are multiple ways to do it. Below is an example of wiring diagram for XS3868 and HC05 modules that are powered up by LM2596, but you can use any power suply that can provide appropreate voltage and current.

## Supported serial interfaces (for DIY cable)

 - FTDI: FT232R, FT231X, FT311D, FT312D
 - Prolific: PL2303
 - SiLabs: CP2102, CP2105, CP2108, CP2110
 - Any Bluetooth device that supports SPP, eg.: HC-05, RN52, etc...
 
PodEmu was tested with FT312D, PL2303, FT232R, CP2102 and HC-05. Other chips should also work (as claimed by driver developer), but were never tested with PodEmu. 


## Supported Music Application List

PodEmu will support any music application out of the box if this application properly declares itself in the system. To be "selectable" on the list it should declare itselft for intent filter as "android.intent.category.APP_MUSIC". To receive metadata about currently playing track applications should broadcast metadata to other apps. The examples of applications that do all of it properly are Google Music, android native player, MixZing. Some applications do only parto of it: eg. Spotify and TIDAL does not include track position info in the broadcast. Also TIDAL and PowerAmp does not declare itself for intent filter although it broadcasts metadata information - I had to add them to application list manually. The table below summarizes the behavior of the applications with which PodMode was tested.

Additionally, please remember, that in order support track/album/artist/playlist/genre navigation fully PodEmu need to have this information provided. Unfortunately there is no way to retreive this information if the source application do not broadcast it itself. In the best case PodEmu is only informed about currently played track, total playlist size and current track position. Therefore PodEmu is trying to mimic the rest of the information for the docking station to be able to operate. Here remember, that each docking station behaves differently and the result could be also different. Feel free to provide feedback with your experience.

<table>
<tr>
<td><b>Application</td>
<td><b>Support for basic control (play/pause, prev, next)</b></td>
<td><b>Provides metadata information</b></td>
<td><b>Additional information</b></td>
</tr>
<tr>
<td>Google Play Music</td>
<td>yes</td>
<td>yes</td>
<td></td>
</tr>
<tr>
<td>Native Music application</td>
<td>yes</td>
<td>yes</td>
<td></td>
</tr>
<tr>
<td>Spotify</td>
<td>yes</td>
<td>yes</td>
<td>Metadata does not contain ListSize and ListPosition information. Broadcasts should be enabled in Settings -> Device Broadcast Status. Spotify also does not support Fast Forward / Rewind keycode injection.</td>
</tr>
<tr>
<td>TIDAL</td>
<td>yes</td>
<td>yes</td>
<td>Metadata does not contain ListSize, ListPosition.</td>
</tr>
<tr>
<td>MixZing</td>
<td>yes</td>
<td>yes</td>
<td></td>
</tr>
<tr>
<td>PowerAmp</td>
<td>yes</td>
<td>yes</td>
<td></td>
</tr>
<tr>
<td>Apple Music</td>
<td>no</td>
<td>no</td>
<td>Yes, unfortunately Apple Music neither react to button press emulation nor broadcasting current track information.</td>
</tr>


</table>

If you don't see your favourite app in the table above, don't worry, it still could be supported out of the box. The condition is, it should be programmed "properly" and follow standards described above.

ListSize and ListPosition information is very important to be able to see the total amount of songs in the playlist from the docking station and to be able to select random song from list and jump to it. Whenever this information is missing PodEmu will not know how many songs are in the the current playlist and will not support "jump to" command. In such case you will see one album, that contains just 3 songs and currently played song is always song nr 2.  Also remember, that even if ListSize information is provided (which is count of songs in the current playlist), PodEmu don't know track names "a priori". Therefore, first time you browse them from docking station, you will see titles like "Track XYZ" for all of them. However, once the song is played, it's title is remembered at given position. This list is flushed when total count of song is changed or application is restarted.

## Bluetooth - XS3868

Bluetooth setup was tested with HC-05 as serial interface device and XS3868 to stream audio. Connection diagram that was used is the following:
<img width="500" src="/schematics/HC-05%20and%20XS3868.png?raw=true" />
Important notes: 
 - do not short audio ground (pin 2) with power ground (pins 15 and 16). If you do it, significant noise will appear.
 - voltage is set to 3,55V and not to 3,3V. 3.3V is normal operating voltage for HC-05, 3.6V is maximum for HC-05. However 3.6V should be minimum voltage for XS3868. When voltage drops below 3.5V, XS3868 will produce audible warnings. To avoid it, but to stay within HC-05 voltage limits it is recommended to set voltage between 3.5V and 3.6V. There are known cases when slight exceeding 3.6V burned HC-05.
 - before using HC-05 it need to be configured. You need to change Baud Rate to 57600 (or whatever rate is required by your car/dock station)
	AT+UART=57600,0,0
For details about configuring HC-05 please refer to [this manual](https://www.itead.cc/wiki/Serial_Port_Bluetooth_Module_%28Master/Slave%29_%3A_HC-05)
 - changing device name is not required, because you can choose the device from paired devices list from the application
 - after BT module is configured, you need to manually pair with it. Once paired, start PodEmu, go to settings and select your device from the list of paired devices. Then PodEmu will connect automatically.
 - serial interface cable has higher priority to connect, so if it is attached, BT will not connect. Detach the cable first and then restart the app.

## Known problems

 - volume level produced by XS3868 is low (around 60%) comparing to direct audio connection.


## Credits

 - USB Serial For Android: https://github.com/mik3y/usb-serial-for-android
 - ByteFIFO class: http://www.java2s.com/Code/Java/Threads/ByteFIFO.htm
 - Android Developer Icons: http://www.androidicons.com/
 - Question mark icon: http://www.clipartpanda.com/categories/animated-question-mark-for-powerpoint
