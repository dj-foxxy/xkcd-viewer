package com.appspot.mancocktail.xkcdviewer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.util.Log;

public class ComicSyncHelper
{
    private static final String TAG = "ComicSyncer";

    private final Context mContext;

    public ComicSyncHelper(final Context context)
    {
        super();
        mContext = context;
    }

    private SharedPreferences mPrefs = null;
    private final Object mComicSyncLock = new Object();
    private int latestComicNumber = -1;

    private final OnSharedPreferenceChangeListener mComicsSyncedListener =
            new OnSharedPreferenceChangeListener()
    {
        public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences,
                final String key)
        {
            Log.d(TAG, "Got key: " + key);
            if (Preferences.LATEST_COMIC_NUMBER.equals(key))
            {
                synchronized (mComicSyncLock)
                {
                    latestComicNumber = mPrefs.getInt(Preferences.LATEST_COMIC_NUMBER, -1);
                    mComicSyncLock.notify();
                }
            }
        }
    };

    public int syncComics() throws InterruptedException
    {
        mPrefs = Preferences.getPreferences(mContext);
        mPrefs.registerOnSharedPreferenceChangeListener(mComicsSyncedListener);
        final Intent intent = new Intent(mContext, ComicSyncService.class);
        intent.setAction(Intent.ACTION_SYNC);

        try
        {
            synchronized (mComicSyncLock)
            {
                mContext.startService(intent);
                mComicSyncLock.wait();
            }
        }
        finally
        {
            mPrefs.unregisterOnSharedPreferenceChangeListener(mComicsSyncedListener);
        }

        return latestComicNumber;
    }
}
