package com.appspot.mancocktail.xkcdviewer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
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
    public void onDestroy()
    {
        super.onDestroy();
        mClient.getConnectionManager().shutdown();
    }

    @Override
    protected void onHandleIntent(final Intent intent)
    {
        final String action = intent.getAction();
        if (Intent.ACTION_SYNC.equals(action))
        {
            syncComics();
        }
        else if (ACTION_DOWNLOAD.equals(action))
        {
            syncImage(intent.getData());
        }
        else
        {
            throw new UnsupportedOperationException("Unsupported action \"" + action + "\".");
        }
        ComicSyncServiceHelper.handledIntent(intent);
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
    }

    private int latestNumber()
    {
        Cursor cursor = null;
        try
        {
            cursor = mResolver.query(ComicTable.CONTENT_URI,
                    new String[] { "max(" + ComicTable.NUMBER + ")" }, null, null, null);
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
        final ContentValues values = new ContentValues(5);
        for (ComicList.Comic comic : comics.getComicsList())
        {
            values.put(ComicTable.NUMBER, comic.getNumber());
            values.put(ComicTable.TITLE, comic.getTitle());
            values.put(ComicTable.IMAGE_NAME, comic.getImgName());
            values.put(ComicTable.IMAGE_TYPE, comic.getImgType().getNumber());
            values.put(ComicTable.MESSAGE, comic.getMessage());
            // This returns null, but is inserted correctly insert?
            mResolver.insert(ComicTable.CONTENT_URI, values);
        }
    }

    private void syncImage(final Uri comic)
    {
        try
        {
            downloadImage(comic, getImageRequest(comic));
        }
        catch (IOException e)
        {
            Log.e(TAG, "Failed to sync image for " + comic, e);
            return;
        }

        final ContentValues values = new ContentValues(1);
        values.put(ComicTable.IMAGE_SYNC_STATE, ComicTable.IMAGE_SYNC_STATE_SYNCED);
        if (getContentResolver().update(comic, values, null, null) != 1)
        {
            Log.e(TAG, "Incorrect number of rows updated for " + comic);
        }
        Log.d(TAG, "Synced image for " + comic);
    }

    private HttpGet getImageRequest(final Uri comic)
    {
        Cursor cursor = null;
        try
        {
            cursor = getContentResolver()
                    .query(comic,
                            new String[] { ComicTable.IMAGE_NAME, ComicTable.IMAGE_TYPE }, null,
                            null, null);
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
}
