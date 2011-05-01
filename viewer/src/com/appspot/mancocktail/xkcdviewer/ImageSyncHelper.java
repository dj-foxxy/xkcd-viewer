package com.appspot.mancocktail.xkcdviewer;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;

import com.appspot.mancocktail.xkcdviewer.ComicContentProvider.Comic;

public class ImageSyncHelper
{
    @SuppressWarnings("unused")
    private static final String TAG = "ImageSyncHelper";
    private final Context mContext;
    private final ContentResolver mResolver;

    public ImageSyncHelper(final Context context)
    {
        mContext = context;
        mResolver = mContext.getContentResolver();
    }

    private final Object mImageSyncLock = new Object();

    private final ContentObserver mImageSyncListener = new ContentObserver(null)
    {
        @Override
        public void onChange(boolean selfChange)
        {
            synchronized (mImageSyncLock)
            {
                mImageSyncLock.notify();
            }
        }
    };

    public boolean syncImage(final int number) throws InterruptedException
    {
        final Uri comic = ContentUris.withAppendedId(Comic.CONTENT_URI, number);

        if (isImageSynced(comic))
        {
            return true;
        }

        final Intent intent = new Intent(mContext, ComicSyncService.class);
        intent.setAction(ComicSyncService.ACTION_DOWNLOAD);
        intent.setData(comic);

        mResolver.registerContentObserver(comic, false, mImageSyncListener);
        try
        {
            synchronized (mImageSyncLock)
            {
                mContext.startService(intent);
                mImageSyncLock.wait();
            }
        }
        finally
        {
            mResolver.unregisterContentObserver(mImageSyncListener);
        }

        return isImageSynced(comic);
    }

    private boolean isImageSynced(final Uri comic)
    {
        Cursor cursor = null;
        try
        {
            cursor = mResolver.query(comic, new String[] { Comic.IMG_SYNC_STATE }, null, null,
                    null);
            if (cursor != null && cursor.moveToFirst())
            {
                return cursor.getInt(0) == Comic.IMG_SYNC_STATE_OK;
            }
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }
        return false;
    }
}
