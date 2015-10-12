## About

PodEmu is Android application that allows you to connect your Android device to iPod docking station or your car audio system. PodEmu supports both AiR (Advanced) and Simple modes so you can control your Android music app directly from docking station or from your cars steering wheel. PodEmu uses serial protocol to communicate with iPod docking station so it will work only with "old" 30-pin iPod connectors. Lightning interface is not supported.

This application is very similar to PodMode application that already [exists out there](http://forum.xda-developers.com/showthread.php?t=2220108). So why build new application? There are 3 (well, maybe 4) simple reasons:
 1. PodMode didn't work with my car
 2. PodMode is not open source (so I couldn't fix point 1)
 3. PodMode is not maintained anymore (so author wouldn't fix point 1)
 4. for fun... :)
  
PodEmu in action:
[![PodEmu in action](/screenshots/Screenshot_07.png)](https://youtu.be/zXcBL5EdCGY)

## Requirements

 - Android device with USB host support
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

 - FTDI: FT232R, FT231X
 - Prolific: PL2303
 - SiLabs: CP2102, CP2105, CP2108, CP2110
 
PodEmu was tested with PL2303, FT232R and CP2102. Other chips should also work (as claimed by driver developer), but were never tested with PodEmu. 
 

## TODO

 - Support for FT311/FT312
 - Add support playlist/album/artist browse

## Credits

 - USB Serial For Android: https://github.com/mik3y/usb-serial-for-android
 - ByteFIFO class: http://www.java2s.com/Code/Java/Threads/ByteFIFO.htm
 - Android Developer Icons: http://www.androidicons.com/
 - Question mark icon: http://www.clipartpanda.com/categories/animated-question-mark-for-powerpoint
