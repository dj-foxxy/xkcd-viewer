package com.appspot.mancocktail.xkcdviewer;

import android.app.Application;
import android.os.StrictMode;

public class XkcdViewerApplication extends Application
{
    private static final boolean DEVELOPER_MODE = true;

    public void onCreate()
    {
        if (DEVELOPER_MODE)
        {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
        }
        super.onCreate();
    }
}
