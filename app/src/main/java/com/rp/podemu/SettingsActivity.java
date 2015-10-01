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
 along with this program; if not, write to the Free Software Foundation,
 Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA

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
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class SettingsActivity extends AppCompatActivity
            implements ControlledAppDialogFragment.ControlledAppDialogListener
{

    private static ArrayList<ApplicationInfo> appInfos = new ArrayList<ApplicationInfo>(0);

/*
    private saveSettings()
    {

    }
*/


    // The dialog fragment receives a reference to this Activity through the
    // Fragment.onAttach() callback, which it uses to call the following methods
    // defined by the NoticeDialogFragment.NoticeDialogListener interface
    @Override
    public void onClick(DialogInterface dialog, int which)
    {
        //saving to shared preferances
        SharedPreferences sharedPref = this.getSharedPreferences("PODEMU_PREFS", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("ControlledAppProcessName", appInfos.get(which).packageName);
        editor.commit();

        // loading information to the activity
        setCtrlApplicationInfo();

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);



        //String[] controlledApp = new String[appsRunning.size()];
        //Drawable[] icons = new Drawable[appsRunning.size()];
        //pm.getApplicationInfo(r.baseActivity.getPackageName(), PackageManager.GET_META_DATA);
        //Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        //startActivity(intent);


        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_APP_MUSIC);

        PackageManager pm = getPackageManager();
        String text="";
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


        //now we have unique packages in the hashset, so get their application infos
        //and add them to the arraylist
        for(String packageName : packageNames) {
            try {
                appInfos.add(pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA));

            } catch (PackageManager.NameNotFoundException e) {
                //Do Nothing
            }
        }


        text += "Apps count: " + appInfos.size() + "\n";
        for (ApplicationInfo appInfo : appInfos)
        {
            //if (packageInfo.)
            {
                appInfo.name=(String)appInfo.loadLabel(pm);
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

    }

    @Override
    public void onResume()
    {
        super.onResume();
        setCtrlApplicationInfo();

    }

    private void setCtrlApplicationInfo() {
        SharedPreferences sharedPref = this.getSharedPreferences("PODEMU_PREFS", Context.MODE_PRIVATE);
        String processName = sharedPref.getString("ControlledAppProcessName", "log loading");

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
            textView.setText("Cannot load app: " + processName);
        }

    }

    public void chooseCtrlApp(View v)
    {
        ControlledAppDialogFragment ctrlAppDialog=new ControlledAppDialogFragment();
        ctrlAppDialog.setApplicationInfos(appInfos, appInfos.size());
        ctrlAppDialog.show(getSupportFragmentManager(), "new_tag");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
