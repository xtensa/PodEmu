/**

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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.DialogFragment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;


public class PlaylistCountDialogFragment extends DialogFragment // implements DialogInterface.OnClickListener
{

    public static Map<Integer, String> optionsList = new LinkedHashMap<>();

    public static void initialize()
    {
        if(optionsList.size()>0) return;

        optionsList.put(PodEmuMediaStore.MODE_PLAYLIST_SIZE_SINGLE, "Single track playlist");
        optionsList.put(PodEmuMediaStore.MODE_PLAYLIST_SIZE_TRIPLE, "Triple track playlist");
        optionsList.put(PodEmuMediaStore.MODE_PLAYLIST_SIZE_FIXED , "Fixed count playlist (20)");
    }

    public PlaylistCountDialogFragment()
    {
        initialize();
    }

    // Use this instance of the interface to deliver action events

    /* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event callbacks.
     * Each method passes the DialogFragment in case the host needs to query it. */
    public interface PlaylistCountDialogListener
    {
        void onPlaylistCountModeSelected(DialogInterface dialog, int which);
    }

    // Use this instance of the interface to deliver action events
    PlaylistCountDialogListener mListener;

    // Override the Fragment.onAttach() method to instantiate the NoticeDialogListener
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            mListener = (PlaylistCountDialogListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement PlaylistCountDialogListener");
        }
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        Vector<String> playlistCountModes = new Vector<String>();
        for (String mode: optionsList.values())
        {
            playlistCountModes.add(mode);
        }


        // converting Vector appNames to String[] appNamesStr
        String [] appNamesStr=playlistCountModes.toArray(new String[playlistCountModes.size()]);

        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.dialog_playlist_count_mode)
                .setItems(appNamesStr, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // The 'which' argument contains the index position
                        // of the selected item
                        mListener.onPlaylistCountModeSelected(dialog, which);
                    }
                });
        // Create the AlertDialog object and return it
        return builder.create();
    }

/*

    @Override
    public void onClick(DialogInterface dialogInterface, int i)
    {
        //dismiss();
    }
*/
}
