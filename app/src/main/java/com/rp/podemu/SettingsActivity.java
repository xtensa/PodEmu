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
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class SettingsActivity extends AppCompatActivity
            implements  ControlledAppDialogFragment.ControlledAppDialogListener,
                        BaudRateDialogFragment.BaudRateDialogListener
{

    private ArrayList<ApplicationInfo> appInfos = new ArrayList<>(0);
    private ArrayList<Integer> baudRateList = new ArrayList<>(0);

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
        //saving to shared preferances
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("ControlledAppProcessName", appInfos.get(which).packageName);
        editor.apply();

        // loading information to the activity
        setCtrlApplicationInfo();

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


    }

    @Override
    public void onResume()
    {
        super.onResume();
        setCtrlApplicationInfo();
        setBaudRateInfo();
        setDebugInfo();
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

        } catch (PackageManager.NameNotFoundException e)
        {
            textView.setText("Cannot load application ");
        }

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

    public void selectBaudRate(View v)
    {
        BaudRateDialogFragment baudRateDialog = new BaudRateDialogFragment();
        baudRateDialog.setBaudRateList(baudRateList);
        baudRateDialog.show(getSupportFragmentManager(), "new_tag");
    }

    public void toggleDebug(View v)
    {
        String enableDebug = sharedPref.getString("enableDebug", "false");
        SharedPreferences.Editor editor = sharedPref.edit();

        if ( enableDebug.equals("true") )
        {
            // if it was enabled we need to disable it
            editor.putString("enableDebug", "false");
            PodEmuLog.DEBUG_LEVEL=0;
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
                            // continue with delete
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .show();

            editor.putString("enableDebug", "true");
            PodEmuLog.DEBUG_LEVEL=2;
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
        String username="dev.roman";
        String domain="gmail.com";
        String uriText =
                "mailto:" + username + "@" + domain +
                "?subject=" + Uri.encode("PodEmu debug") +
                "&body=" + Uri.encode("You can put additional description of the problem instead of this text.");

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

        enableDebugHint.setText(R.string.enable_debug_hint + " Logs will be saved to the following file: " + PodEmuLog.getLogFileName());
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.menu_settings, menu);
        return true;
    }

}
