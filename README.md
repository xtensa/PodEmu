## About

PodEmu is Android application that allows you to connect your Android device to iPod docking station or your car audio system. PodEmu supports both AiR (Advanced) and Simple modes so you can control your Android music app directly from docking station or from your cars steering wheel. PodEmu uses serial protocol to communicate with iPod docking station so it will work only with "old" 30-pin iPod connectors. Lightning interface is not supported.

This application is very similar to PodMode application that already [exists out there](http://forum.xda-developers.com/showthread.php?t=2220108). So why build new application? There are 3 (well, maybe 4) simple reasons:
 1. PodMode didn't work with my car
 2. PodMode is not open source (so I couldn't fix point 1)
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
</table>

## Supported serial interfaces (for DIY cable)

 - FTDI: FT232R, FT231X, FT311D, FT312D
 - Prolific: PL2303
 - SiLabs: CP2102, CP2105, CP2108, CP2110
 
PodEmu was tested with FT312D, PL2303, FT232R and CP2102. Other chips should also work (as claimed by driver developer), but were never tested with PodEmu. 


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
<td>Metadata does not contain ListSize and ListPosition information. Broadcasts should be enabled in Settings -> Device Broadcast Status.</td>
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

## TODO

 - Bluetooth support 
 - Support for more apps (if your app is not on the list or behaves weirdly, just drop me a message)

## Credits

 - USB Serial For Android: https://github.com/mik3y/usb-serial-for-android
 - ByteFIFO class: http://www.java2s.com/Code/Java/Threads/ByteFIFO.htm
 - Android Developer Icons: http://www.androidicons.com/
 - Question mark icon: http://www.clipartpanda.com/categories/animated-question-mark-for-powerpoint
