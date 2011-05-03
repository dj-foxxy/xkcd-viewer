package com.appspot.mancocktail.xkcdviewer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.appspot.mancocktail.xkcdviewer.ComicContentProvider.ComicTable;
import com.appspot.mancocktail.xkcdviewer.ComicProtos.ComicList;

public class ComicSyncService extends IntentService
{
    private static final String TAG = "ComicDownloadService";
    private static final int BUFFER_SIZE_BYTES = 1024;

    public static final String ACTION_DOWNLOAD = "download";

    private ContentResolver mResolver;
    private HttpClient mClient;

    public ComicSyncService()
    {
        super(TAG);
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        mClient = new DefaultHttpClient();
        mResolver = getContentResolver();
    }

    @Override
    protected void onHandleIntent(final Intent intent)
    {
        final String action = intent.getAction();
        Log.d(TAG, "Got intent");
        if (Intent.ACTION_SYNC.equals(action))
        {
            Log.d(TAG, "Starting sync");
            syncComics();
        }
        else if (ACTION_DOWNLOAD.equals(action))
        {
            Log.d(TAG, "Starting download");
            syncImage(intent.getData());
        }
        else
        {
            throw new UnsupportedOperationException("Unsupported action \"" + action + "\".");
        }
    }

    private void syncComics()
    {
        final HttpGet request = new HttpGet(
                "http://rpc143.cs.man.ac.uk:8000/comics/?after=" + latestNumber());

        try
        {
            insertComics(ComicList.parseFrom(mClient.execute(request).getEntity().getContent()));
        }
        catch (IOException e)
        {
            Log.w(TAG, "Failed to sync comics.", e);
        }
        final Editor editor = getSharedPreferences(Prefs.NAME, MODE_PRIVATE).edit();
        editor.putLong(Prefs.LAST_SYNC, new Date().getTime());
        editor.commit();
    }

    private int latestNumber()
    {
        Cursor cursor = null;
        try
        {
            cursor = mResolver.query(ComicTable.CONTENT_URI,
                    new String[] { "max(" + ComicTable.NUMBER + ')' }, null, null, null);
            /*
             * There is always a first row. If there are no comics, the max
             * comic number is 0.
             */
            if (cursor != null && cursor.moveToFirst())
            {
                return cursor.getInt(0);
            }
            else
            {
                Log.e(TAG, "Could not get latest number, returning -1.");
                return -1;
            }
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }
    }

    private void insertComics(final ComicList comics)
    {
        final ContentResolver resolver = getContentResolver();
        final ContentValues values = new ContentValues(5);
        for (ComicList.Comic comic : comics.getComicsList())
        {
            values.put(ComicTable.NUMBER, comic.getNumber());
            values.put(ComicTable.TITLE, comic.getTitle());
            values.put(ComicTable.IMG_NAME, comic.getImgName());
            values.put(ComicTable.IMG_TYPE, comic.getImgType().getNumber());
            values.put(ComicTable.MESSAGE, comic.getMessage());
            // This returns null, but does correctly insert.
            if (resolver.insert(ComicTable.CONTENT_URI, values) == null);
            {
                Log.e(TAG, "Failed to insert comic with values " + values);
            }
        }
    }

    private void syncImage(final Uri comic)
    {
        int imgSyncState = ComicTable.IMG_SYNC_STATE_OK;

        try
        {
            downloadImage(comic, getImageRequest(comic));
        }
        catch (IOException e)
        {
            Log.e(TAG, "Failed to sync image for " + comic, e);
            imgSyncState = ComicTable.IMG_SYNC_STATE_ERROR;
        }

        // Update sync state.
        final ContentValues values = new ContentValues(1);
        values.put(ComicTable.IMG_SYNC_STATE, imgSyncState);
        Log.d(TAG, "Setting " + comic + " to " + imgSyncState);
        if (getContentResolver().update(comic, values, null, null) != 1)
        {
            Log.e(TAG, "Incorrect number of rows updated.");
        }
    }

    private HttpGet getImageRequest(final Uri comic)
    {
        Cursor cursor = null;
        try
        {
            cursor = getContentResolver().query(comic,
                    new String[] { ComicTable.IMG_NAME, ComicTable.IMG_TYPE }, null, null, null);
            if (cursor == null || !cursor.moveToFirst())
            {
                throw new IllegalArgumentException("No comic at URI \"" + comic + "\".");
            }
            return new HttpGet("http://imgs.xkcd.com/comics/" + cursor.getString(0)
                    + ComicTable.getExt(cursor.getInt(1)));
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }
    }

    private void downloadImage(final Uri comic, final HttpGet request) throws IOException
    {
        InputStream in = null;
        OutputStream out = null;
        try
        {
            in = mClient.execute(request).getEntity().getContent();
            out = getContentResolver().openOutputStream(comic);
            final byte[] buffer = new byte[BUFFER_SIZE_BYTES];
            for (int read; (read = in.read(buffer)) != -1;)
            {
                out.write(buffer, 0, read);
            }
        }
        finally
        {

            if (in != null)
            {
                try
                {
                    in.close();
                }
                finally
                {
                    if (out != null)
                    {
                        out.close();
                    }
                }

            }
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        mClient.getConnectionManager().shutdown();
    }
}
