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

//import android.support.v4.app.FragmentManager;


import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.View;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.jar.Manifest;



public class SettingsActivity extends AppCompatActivity
            implements  ControlledAppDialogFragment.ControlledAppDialogListener,
                        PlaylistCountDialogFragment.PlaylistCountDialogListener,
                        BaudRateDialogFragment.BaudRateDialogListener
{

    private ArrayList<ApplicationInfo> appInfos = new ArrayList<>(0);
    private ArrayList<Integer> baudRateList = new ArrayList<>(0);

    private PodEmuLog podEmuLog;

    private boolean enableListCountSelection = false;

    SharedPreferences sharedPref;

/*
    private saveSettings()
    {

    }
*/


    // The dialog fragment receives a reference to this Activity through the
    // Fragment.onAttach() callback, which it uses to call the following methods
    // defined by the NoticeDialogFragment.NoticeDialogListener interface
    @Override
    public void onCtrlAppSelected(DialogInterface dialog, int which)
    {
        //saving to shared preferences
        SharedPreferences.Editor editor = sharedPref.edit();
        String oldCtrlApp=sharedPref.getString("ControlledAppProcessName", "unknown app");
        Boolean ctrlAppUpdated=sharedPref.getBoolean("ControlledAppUpdated", false);
        editor.putString("ControlledAppProcessName", appInfos.get(which).packageName);
        editor.putBoolean("ControlledAppUpdated",!oldCtrlApp.equals(appInfos.get(which).packageName) || ctrlAppUpdated);
        editor.apply();

        // loading information to the activity
        setCtrlApplicationInfo();

        PodEmuLog.debug("Selected app: " + appInfos.get(which).packageName);

    }


    // The dialog fragment receives a reference to this Activity through the
    // Fragment.onAttach() callback, which it uses to call the following methods
    // defined by the NoticeDialogFragment.NoticeDialogListener interface
    @Override
    public void onPlaylistCountModeSelected(DialogInterface dialog, int which)
    {
        int mode = which+1;
        //saving to shared preferences
        SharedPreferences.Editor editor = sharedPref.edit();
        int oldPlaylistCountMode=sharedPref.getInt("PlaylistCountMode", PlaylistCountDialogFragment.MODE_PLAYLIST_SIZE_DEFAULT);
        Boolean playlistCountModeUpdated=sharedPref.getBoolean("PlaylistCountModeUpdated", false);
        editor.putInt("PlaylistCountMode", mode);
        editor.putBoolean("PlaylistCountModeUpdated", oldPlaylistCountMode != which || playlistCountModeUpdated);
        editor.apply();

        // loading information to the activity
        setPlaylistCountModeInfo();

        PodEmuLog.debug("PESA: Selected PlaylistCountMode: " + mode + " - " + PlaylistCountDialogFragment.optionsList.get(mode));

    }



    // The dialog fragment receives a reference to this Activity through the
    // Fragment.onAttach() callback, which it uses to call the following methods
    // defined by the NoticeDialogFragment.NoticeDialogListener interface
    @Override
    public void onBaudRateSelected(DialogInterface dialog, int which)
    {
        String baudRate = baudRateList.get(which).toString();

        //saving to shared preferances
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("BaudRate", baudRate);
        editor.commit();

        // loading information to the activity
        setBaudRateInfo();

    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sharedPref = this.getSharedPreferences("PODEMU_PREFS", Context.MODE_PRIVATE);


        //String[] controlledApp = new String[appsRunning.size()];
        //Drawable[] icons = new Drawable[appsRunning.size()];
        //pm.getApplicationInfo(r.baseActivity.getPackageName(), PackageManager.GET_META_DATA);
        //Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        //startActivity(intent);


        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_APP_MUSIC);

        PackageManager pm = getPackageManager();
        String text = "";
        List<ResolveInfo> packages = pm.queryIntentActivities(intent, 0);
        //get a list of installed apps.
        //List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        //using hashset so that there will be no duplicate packages,
        //if no duplicate packages then there will be no duplicate apps
        HashSet<String> packageNames = new HashSet<String>(0);
        appInfos = new ArrayList<ApplicationInfo>(0);

        //getting package names and adding them to the hashset
        for (ResolveInfo resolveInfo : packages)
        {
            packageNames.add(resolveInfo.activityInfo.packageName);
        }

        // used just for tests
        /*
        ApplicationInfo dummyApp = new ApplicationInfo();
        dummyApp.name="select application";
        dummyApp.processName="dummy";
        appInfos.add(dummyApp);
        */

        for (String packageName : PodEmuIntentFilter.getAppList())
        {
            try
            {
                appInfos.add(pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA));
            }
            catch (PackageManager.NameNotFoundException e)
            {
                //Do Nothing
            }
        }
        //now we have unique packages in the hashset, so get their application infos
        //and add them to the arraylist
        for (String packageName : packageNames)
        {
            try
            {
                appInfos.add(pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA));

            } catch (PackageManager.NameNotFoundException e)
            {
                //Do Nothing
            }
        }


        text += "Apps count: " + appInfos.size() + "\n";
        for (ApplicationInfo appInfo : appInfos)
        {
            //if (packageInfo.)
            {
                appInfo.name = (String) appInfo.loadLabel(pm);
                text += appInfo.loadLabel(pm) + "\n";
                //text += packageInfo.
                //text += "\n";
            }
        }

        //TextView textView = (TextView) findViewById(R.id.ctrlAppTitle);
        //textView.setText(text);


        //LauncherApps launcherApps=new LauncherApps();
        //List<LauncherActivityInfo> activities=launcherApps.getActivityList(null, android.os.Process.myUserHandle());
        //List<LauncherActivityInfo> activities=LauncherApps().getActivityList(null, android.os.Process.myUserHandle());


        baudRateList.add(9600);
        baudRateList.add(14400);
        baudRateList.add(19200);
        baudRateList.add(28800);
        baudRateList.add(38400);
        baudRateList.add(56000);
        baudRateList.add(57600);
        baudRateList.add(115200);

        try
        {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            TextView versionHint = (TextView) findViewById(R.id.versionHint);
            versionHint.setText(getResources().getString(R.string.version_hint) + version);
        }
        catch (PackageManager.NameNotFoundException e)
        {
            // do nothing
        }

        // The below code is required to workaround file policy restrictions introduced in API 24
        // For details see: https://stackoverflow.com/questions/38200282/android-os-fileuriexposedexception-file-storage-emulated-0-test-txt-exposed
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        
    }

    @Override
    public void onResume()
    {
        super.onResume();
        PodEmuLog.initialize(getApplicationContext());
        setCtrlApplicationInfo();
        setPlaylistCountModeInfo();
        setBaudRateInfo();
        setDebugInfo();
        setToggleForceSimpleMode();
        setAutoSwitchToApp();

        if( !enableListCountSelection )
        {
            RelativeLayout layout = (RelativeLayout) findViewById(R.id.playlistCountLayout);
            layout.setVisibility(View.GONE);
        }
    }

    private void setCtrlApplicationInfo()
    {
        String processName = sharedPref.getString("ControlledAppProcessName", "unknown application");

        PackageManager pm = getPackageManager();
        ApplicationInfo appInfo;
        TextView textView = (TextView) findViewById(R.id.ctrlAppName);

        try
        {
            appInfo = pm.getApplicationInfo(processName, PackageManager.GET_META_DATA);

            textView.setText(appInfo.loadLabel(pm));

            ImageView imageView = (ImageView) findViewById(R.id.ctrlAppIcon);
            imageView.setImageDrawable(appInfo.loadIcon(pm));

        }
        catch (PackageManager.NameNotFoundException e)
        {
            textView.setText("Cannot load application ");
        }

    }


    private void setPlaylistCountModeInfo()
    {
        int playlistCountMode = sharedPref.getInt("PlaylistCountMode", PlaylistCountDialogFragment.MODE_PLAYLIST_SIZE_DEFAULT);

        PlaylistCountDialogFragment.initialize();
        TextView textView = (TextView) findViewById(R.id.playlistCountName);
        textView.setText("Playlist Size - " + PlaylistCountDialogFragment.optionsList.get(playlistCountMode));
    }

    private void setBaudRateInfo()
    {
        if (!sharedPref.contains("BaudRate"))
        {
            // writing default baud rate
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("BaudRate", "57600");
            editor.apply();
        }
        String baudRate = sharedPref.getString("BaudRate", "unknown baud rate");

        // changing text
        TextView textView = (TextView) findViewById(R.id.baudRateValue);
        textView.setText(baudRate);
    }

    public void selectCtrlApp(View v)
    {
        ControlledAppDialogFragment ctrlAppDialog = new ControlledAppDialogFragment();
        ctrlAppDialog.setApplicationInfos(appInfos, appInfos.size());
        ctrlAppDialog.show(getSupportFragmentManager(), "new_tag");

    }

    public void selectPlaylistCountMode(View v)
    {
        PlaylistCountDialogFragment playlistCountDialogFragment = new PlaylistCountDialogFragment();
        playlistCountDialogFragment.show(getSupportFragmentManager(), "new_tag");

    }

    public void toggleForceSimpleMode(View v)
    {

        //saving to shared preferences
        SharedPreferences.Editor editor = sharedPref.edit();
        int forceSimpleMode=(sharedPref.getInt("ForceSimpleMode", 0)==0?1:0);
        editor.putInt("ForceSimpleMode", forceSimpleMode);
        editor.apply();

        // loading information to the activity
        setToggleForceSimpleMode();


        PodEmuLog.debug("PESA: forceSimpleMode switched to: " + forceSimpleMode);
    }

    public void toggleAutoSwitchToApp(View v)
    {

        //saving to shared preferences
        SharedPreferences.Editor editor = sharedPref.edit();
        int autoSwitchToApp=(sharedPref.getInt("autoSwitchToApp", 0)==0?1:0);
        editor.putInt("autoSwitchToApp", autoSwitchToApp);
        editor.apply();

        // loading information to the activity
        setAutoSwitchToApp();

        PodEmuLog.debug("PESA: autoSwitchToApp switched to: " + autoSwitchToApp);
    }



    public void selectBaudRate(View v)
    {
        BaudRateDialogFragment baudRateDialog = new BaudRateDialogFragment();
        baudRateDialog.setBaudRateList(baudRateList);
        baudRateDialog.show(getSupportFragmentManager(), "new_tag");
    }

    public boolean checkStoragePermissions()
    {

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED)
        {
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
            {
                new AlertDialog.Builder(this)
                    .setTitle("Information")
                    .setMessage("In order to collect logs PodEmu requires access to storage (logs are written to a file on your SD card).")
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int which)
                        {
                            // continue
                            ActivityCompat.requestPermissions(SettingsActivity.this,
                                    new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                    100);
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .show();
            }
            else
            {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        100);

                boolean firstTimeRequestPermissions = sharedPref.getBoolean("firstTimeRequestPermissions", true);

                if( !firstTimeRequestPermissions )
                {
                    new AlertDialog.Builder(this)
                            .setTitle("Permissions missing :(")
                            .setMessage("You did not grant permissions to access your storage. If you want to collect logs please go to Setting -> Apps -> PodEmu -> Permissions and grant storage access permissions.")
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {

                                }
                            })
                            .setIcon(android.R.drawable.ic_dialog_info)
                            .show();

                }
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putBoolean("firstTimeRequestPermissions", false);
                editor.apply();
            }
        }
        else
        {
            return true;
        }

        return false;

    }

    public void toggleDebug(View v)
    {
        boolean forceDisableDebug = false;
        if ( !checkStoragePermissions() ) forceDisableDebug = true;


        String enableDebug = sharedPref.getString("enableDebug", "false");
        SharedPreferences.Editor editor = sharedPref.edit();

        if ( enableDebug.equals("true") || forceDisableDebug)
        {
            // if it was enabled we need to disable it
            editor.putString("enableDebug", "false");
            PodEmuLog.debug_level = PodEmuLog.LOGLEVEL_DISABLED;
        }
        else
        {
            new AlertDialog.Builder(this)
                    .setTitle("Warning")
                    .setMessage("Debug file can grow significantly if debug is not turned off. Please remember to turn off debug when it is not needed. Also, image download functionality could be affected when debug is turn on (image could be downloaded partially, distorted or not downloaded at all).")
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int which)
                        {
                            // continue with action
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .show();

            editor.putString("enableDebug", "true");
            PodEmuLog.debug_level=PodEmuLog.LOGLEVEL_DEFAULT;
            PodEmuLog.printSystemInfo();
        }

        editor.apply();
        setDebugInfo();

    }

    public void viewDebug(View v)
    {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = Uri.parse("file://" + PodEmuLog.getLogFileName());
        intent.setDataAndType(uri, "text/plain");

        try
        {
            startActivity(intent);
        } catch(android.content.ActivityNotFoundException e)
        {
            new AlertDialog.Builder(this)
                    .setTitle("Application not found")
                    .setMessage("There is no application installed that can view text files. Please go to Android Market and install one.")
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int which)
                        {
                            // continue with delete
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }
    }


    public void sendDebug(View v)
    {
        String version;
        try
        {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pInfo.versionName;
        }
        catch (PackageManager.NameNotFoundException e)
        {
            version="NA";
        }

        String username="dev.roman";
        String domain="gmail.com";
        String uriText =
                "mailto:" + username + "@" + domain +
                "?subject=" + Uri.encode("PodEmu debug - V" + version) +
                "&body=" + Uri.encode("IMPORTANT - READ THIS BEFORE SENDING: \n " +
                        "1. Please ensure that debug was enabled before the problem occured. If not, please reproduce the problem while debug is enabled and only then send this message.\n" +
                        "2. Please replace this message with step by step guide on how to reproduce the problem you encountered. \n");

        Uri uri = Uri.parse(uriText);
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(uri);

        //intent.setAction(Intent.ACTION_ATTACH_DATA);
        Uri attachment = Uri.parse("file://" + PodEmuLog.getLogFileName());
        intent.putExtra(Intent.EXTRA_STREAM, attachment);

        try
        {
            startActivity(Intent.createChooser(intent, "Send email"));
        }
        catch(android.content.ActivityNotFoundException e)
        {
            new AlertDialog.Builder(this)
                    .setTitle("Application not found")
                    .setMessage("There is no application installed that can send emails. Please go to Android Market and install one.")
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // continue with delete
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }
    }

    public void eraseDebug(View v)
    {
        new AlertDialog.Builder(this)
                .setTitle("Confirm")
                .setMessage("Are you sure you want permanently erase all gathered debug information?")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int which)
                    {
                        // continue with erasing
                        PodEmuLog.eraseDebug();
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    public void showLicense(View v)
    {
        Intent intent = new Intent(this, LicenseActivity.class);
        startActivity(intent);
    }

    public void showCredits(View v)
    {
        Intent intent = new Intent(this, CreditsActivity.class);
        startActivity(intent);
    }

    private void setDebugInfo()
    {
        PodEmuLog.checkPermissions();
        String enableDebug = sharedPref.getString("enableDebug", "false");

        TextView enableDebugValue = (TextView) findViewById(R.id.enableDebugValue);
        CheckedTextView enableDebugHint = (CheckedTextView) findViewById(R.id.enableDebugHint);

        if( enableDebug.equals("true") )
        {
            enableDebugValue.setText("Debug Enabled");
            enableDebugHint.setChecked(true);
        }
        else
        {
            enableDebugValue.setText("Debug Disabled");
            enableDebugHint.setChecked(false);
        }

        enableDebugHint.setText(getResources().getString(R.string.enable_debug_hint) +
                " Logs will be saved to the following file: " + PodEmuLog.getLogFileName());
    }

    private void setToggleForceSimpleMode()
    {
        int forceSimpleMode = sharedPref.getInt("ForceSimpleMode", 0);

        RelativeLayout layout = (RelativeLayout) findViewById(R.id.playlistCountLayout);

        CheckedTextView toggleForceSimpleModeView = (CheckedTextView) findViewById(R.id.forceSimpleModeHint);

        if( forceSimpleMode == 1 )
        {
            toggleForceSimpleModeView.setChecked(true);
            if ( enableListCountSelection ) layout.setVisibility(View.INVISIBLE);
        }
        else
        {
            toggleForceSimpleModeView.setChecked(false);
            if ( enableListCountSelection ) layout.setVisibility(View.VISIBLE);
        }
    }
    private void setAutoSwitchToApp()
    {
        int autoSwitchToApp = sharedPref.getInt("autoSwitchToApp", 0);
        CheckedTextView autoSwitchToAppView = (CheckedTextView) findViewById(R.id.switchToAppHint);

        if( autoSwitchToApp == 1 )
        {
            autoSwitchToAppView.setChecked(true);
        }
        else
        {
            autoSwitchToAppView.setChecked(false);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        //getMenuInflater().inflate(R.menu.menu_settings, menu);
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 100: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                    PodEmuLog.initialize(getApplicationContext());
                    toggleDebug(this.findViewById(R.id.enableDebugLayout));
                    /*
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("enableDebug", "true");
                    PodEmuLog.debug_level=PodEmuLog.LOGLEVEL_DEFAULT;
                    PodEmuLog.printSystemInfo();
                    editor.apply();
                    setDebugInfo();
                    */
                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

}
