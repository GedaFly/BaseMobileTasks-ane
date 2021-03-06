package com.onebyonedesign.mobile.tasks;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Vibrator;
import android.view.View;
import android.widget.Toast;
import com.adobe.fre.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@TargetApi(Build.VERSION_CODES.KITKAT)
public class MobileTasksContext extends FREContext
{
    /**
     * Create a new MobileTasksContext
     */
    public MobileTasksContext()
    {
        Extension.debug("Context()");
    }

    @Override
    public void dispose()
    {
        Extension.debug("Context.dispose()");

        Extension.context = null;
    }

    @Override
    public Map<String, FREFunction> getFunctions()
    {
        Extension.debug("Context.getFunctions()");

        Map<String, FREFunction> functionMap = new HashMap<String, FREFunction>();

        functionMap.put("shareImage", new ShareImageFunction());
        functionMap.put("setFullscreen", new SetFullScreenFunction());
        functionMap.put("getOSVersion", new GetOSVersionFunction());
        functionMap.put("showText", new ShowToastFunction());
        functionMap.put("vibrate", new VibrateFunction());
        functionMap.put("displayAlert", new DisplayAlertFunction());
        functionMap.put("displayConfirmation", new DisplayConfirmationFunction());
        functionMap.put("testConnection", new IsConnectedFunction());

        return functionMap;
    }

    // ANE Function calls

    /**
     * Share Image
     * @param imagePath     public path to image file
     * @param chooserTitle  title of app chooser [optional]
     */
    public void shareImage(String imagePath, String chooserTitle)
    {
        // no image path passed
        if (imagePath==null || imagePath.length()==0)
        {
            String reason = "Invalid image path";
            Extension.warn(reason);
            dispatchEvent("shareImageError", reason);
            return;
        }

        // Create File from the image path
        File f = new File(imagePath);

        // Image file doesn't exist or can't be read
        if (!f.exists() || !f.canRead())
        {
            String reason = String.format("File not found or read (%s)", imagePath);
            Extension.warn(reason);
            dispatchEvent("shareImageError", reason);
            return;
        }

        // Create the new Intent using the 'Send' action.
        Intent shareIntent = new Intent(Intent.ACTION_SEND);

        // Set the MIME type
        shareIntent.setType("image/*");

        // Create URI from file and add to intent
        Uri uri = Uri.fromFile(f);
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);

        if (chooserTitle!=null && chooserTitle.length() > 0)
            getActivity().startActivity(Intent.createChooser(shareIntent, chooserTitle));
        else
            getActivity().startActivity(shareIntent);

        dispatchEvent("imageShared");
    }

    // Messaging

    /**
     * Dispatch event back to ANE Actionscript
     * @param type  type of Flash BaseMobileTaskEvent (@see BaseMobileTaskEvent.as)
     */
    public void dispatchEvent(String type)
    {
        // Dispose has been called
        if (Extension.context==null)
        {
            Extension.warn(String.format("Attempting to send event (%s) to Flash after ANE dispose()", type));
            return;
        }
        dispatchEvent(type, "");
    }

    /**
     * Dispatch event back to ANE Actionscript
     * @param type		type of Actionscript BaseMobileTaskEvent (@see BaseMobileTaskEvent.as)
     * @param reason	reason for event incl. event code if it exists
     */
    public void dispatchEvent(String type, String reason)
    {
        // Dispose has been called
        if (Extension.context==null)
        {
            Extension.warn(String.format("Attempting to send event (%s) reason (%s) to Flash after ANE dispose()", type, reason));
            return;
        }

        Extension.debug(String.format("Context.dispatchEventWithReason(%s, %s)", type, reason));
        dispatchStatusEventAsync(type, reason);
    }

    // Helpers

    /**
     * Create Dialog Builder
     * @return AlertDialog.Builder instance
     */
    public AlertDialog.Builder createDialogBuilder()
    {
        AlertDialog.Builder builder;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            builder = new AlertDialog.Builder(getActivity(), AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);

        else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            builder = new AlertDialog.Builder(getActivity(), AlertDialog.THEME_HOLO_LIGHT);

        else
            builder = new AlertDialog.Builder(getActivity());

        return builder;
    }

    // Nested Function Classes

    /** Share Function */
    class ShareImageFunction implements FREFunction
    {
        @Override
        public FREObject call(FREContext freContext, FREObject[] freObjects)
        {
            try
            {
                String imagePath = freObjects[0].getAsString();
                String chooserTitle = freObjects[1].getAsString();

                // do actual share
                shareImage(imagePath, chooserTitle);
            }
            catch (Throwable t)
            {
                Extension.warn("Could not set imagePath or chooserTitle", t);
                dispatchEvent("shareImageError");
            }

            return null;
        }
    }

    /** Set Full Screen Function */
    class SetFullScreenFunction implements FREFunction
    {
        @Override
        public FREObject call(FREContext freContext, FREObject[] freObjects)
        {
            Extension.debug("SetFullScreen()");
            
            // utilizes 'immersive mode' only available on KitKat (4.4) and above
            // @see https://developer.android.com/training/system-ui/immersive.html
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
                return null;

            try
            {
                final int uiOptions =  View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                     | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                     | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                     | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                     | View.SYSTEM_UI_FLAG_FULLSCREEN
                                     | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

                final View decorView = getActivity().getWindow().getDecorView();
                decorView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus)
                    {
                        if (hasFocus)
                            decorView.setSystemUiVisibility(uiOptions);
                    }
                });

                decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                    @Override
                    public void onSystemUiVisibilityChange(int visibility)
                    {
                        decorView.setSystemUiVisibility(uiOptions);
                    }
                });
                
                // set immediately
                decorView.setSystemUiVisibility(uiOptions);
                
                // return 'real' size of display in array
                Point p = new Point();
                decorView.getDisplay().getRealSize(p);
                
                FREArray ret = FREArray.newArray(2);
                ret.setObjectAt(0, FREObject.newObject(p.x));
                ret.setObjectAt(1, FREObject.newObject(p.y));
                return ret;
            }
            catch (Throwable t)
            {
                Extension.warn("Could not set full screen", t);
            }
            
            return null;
        }
    }

    /** Get OS Version */
    class GetOSVersionFunction implements FREFunction
    {
        @Override
        public FREObject call(FREContext freContext, FREObject[] freObjects)
        {
            Extension.debug("GetOSVersion()");
            try
            {
                return FREObject.newObject(Build.VERSION.RELEASE);
            }
            catch (Throwable t)
            {
                Extension.warn("Could not return OS version", t);
            }
            return null;
        }
    }

    /** Show toast text function */
    class ShowToastFunction implements FREFunction
    {
        @Override
        public FREObject call(FREContext freContext, FREObject[] freObjects)
        {
            try
            {
                String text = freObjects[0].getAsString();
                int dur = freObjects[1].getAsInt();
                
                // default to short display
                int duration = Toast.LENGTH_SHORT;
                if (dur==1)
                    duration = Toast.LENGTH_LONG;
                    
                Toast toast = Toast.makeText(getActivity().getApplicationContext(), text, duration);
                toast.show();
            }
            catch (Throwable t)
            {
                Extension.warn("Cannot make toast", t);
            }

            return null;
        }
    }

    /** Vibrate device function */
    class VibrateFunction implements FREFunction
    {
        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        @Override
        public FREObject call(FREContext freContext, FREObject[] freObjects)
        {
            try
            {
                long duration = (long)freObjects[0].getAsInt();
                Vibrator vibe = (Vibrator) getActivity().getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
                if(!vibe.hasVibrator())
                {
                    Extension.debug("Device has no vibrator");
                    return null;
                }

                vibe.vibrate(duration);
            }
            catch (Throwable t)
            {
                Extension.warn("Could not vibrate device", t);
            }
            return null;
        }
    }

    /** Display Alert Function (Dialog with single 'OK' button) */
    class DisplayAlertFunction implements FREFunction
    {
        @Override
        public FREObject call(FREContext freContext, FREObject[] freObjects)
        {
            try
            {
                String msg = freObjects[0].getAsString();
                String title = freObjects[1].getAsString();

                AlertDialog.Builder builder = createDialogBuilder();
                if (title.length()>0)
                    builder.setTitle(title);
                builder.setMessage(msg);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int id) {
                        // Do nothing but acknowledge and dismiss
                        dialog.dismiss();
                    }
                });
                AlertDialog dialog = builder.create();
                dialog.setCancelable(false);
                dialog.setCanceledOnTouchOutside(false);
                dialog.show();
            }
            catch (Throwable t)
            {
                Extension.warn("Could not display Alert Dialog", t);
                dispatchEvent("confirmationError");
            }
            return null;
        }
    }

    /** Display Confirmation Function (dialog with 2 buttons - positive and negative) */
    class DisplayConfirmationFunction implements  FREFunction
    {
        @Override
        public FREObject call(FREContext freContext, FREObject[] freObjects)
        {
            try
            {
                String msg = freObjects[0].getAsString();
                String title = freObjects[1].getAsString();
                String pLabel = freObjects[2].getAsString();
                String nLabel = freObjects[3].getAsString();

                AlertDialog.Builder builder = createDialogBuilder();
                if (title.length()>0)
                    builder.setTitle(title);
                builder.setMessage(msg);
                builder.setPositiveButton(pLabel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                        dispatchEvent("confirmationPositive");
                    }
                });
                builder.setNegativeButton(nLabel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                        dispatchEvent("confirmationNegative");
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.setCancelable(false);
                dialog.setCanceledOnTouchOutside(false);
                dialog.show();
            }
            catch (Throwable t)
            {
                Extension.warn("Could not display Confirmation Dialog", t);
                dispatchEvent("confirmationError");
            }
            return null;
        }
    }

    /** Check that internet connection is both available and connected */
    class IsConnectedFunction implements FREFunction
    {
        @Override
        public FREObject call(FREContext freContext, FREObject[] freObjects)
        {
            // Test connection is available
            try
            {
                ConnectivityManager connectivityManager = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo info = connectivityManager.getActiveNetworkInfo();
                if (info == null || !info.isConnectedOrConnecting())
                {
                    dispatchEvent("internetConnectionError");
                    return null;
                }

                // Test connection is working
                new ConnectionCheck().execute();
            }
            catch (Throwable t)
            {
                Extension.warn("Could not test for Internet Connection", t);
                dispatchEvent("internetConnectionError");
            }

            return null;
        }
    }

    /** AsyncTask to check that internet is connected */
    class ConnectionCheck extends AsyncTask<Void, Void, Boolean>
    {
        @Override
        protected Boolean doInBackground(Void... params)
        {
            // http://stackoverflow.com/questions/6493517/detect-if-android-device-has-internet-connection

            try
            {
                HttpURLConnection url = (HttpURLConnection) (new URL("http://clients3.google.com/generate_204").openConnection());
                url.setRequestProperty("User-Agent", "Android");
                url.setRequestProperty("Connection", "close");
                url.setConnectTimeout(1500);
                url.connect();
                return (url.getResponseCode()==204 && url.getContentLength()==0);
            }
            catch (Throwable t)
            {
                Extension.warn("Could not reach URL", t);
            }

            return false;
        }

        @Override
        protected void onPostExecute(Boolean success)
        {
            if (!success)
            {
                dispatchEvent("internetConnectionError");
                return;
            }
            dispatchEvent("internetConnectionSuccess");
        }
    }
}