package com.appspot.mancocktail.xkcdviewer;

import android.content.Context;
import android.content.SharedPreferences;

public class Preferences
{
    private static final String NAME = "prefs";

    public static final String LAST_SYNC = "last_sync";
    public static final String LAST_SYNC_SUCCESSFUL = "last_sync_successful";

    public static final String LATEST_COMIC_NUMBER = "latest_comic_number";

    public static final String LAST_VIEWED_COMIC_NUMBER = "last_viewed_comic_number";
       public static final String COMIC_CURRENT = "comic_current";
       public static final String COMIC_LAST = "comic_last";


    public static SharedPreferences getPreferences(final Context context)
    {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }
}
