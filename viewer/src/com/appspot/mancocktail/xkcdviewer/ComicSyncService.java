package com.appspot.mancocktail.xkcdviewer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.appspot.mancocktail.xkcdviewer.ComicContentProvider.Comic;
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
        mClient = new GZipHttpClient();
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
            sync();
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

    private void sync()
    {
        final HttpGet request = new HttpGet(
                "http://192.168.1.80:8000/comics/?after=" + latestNumber());

        ComicList comics = null;
        boolean syncError = false;
        try
        {
            comics = ComicList.parseFrom(mClient.execute(request).getEntity().getContent());
        }
        catch (IOException e)
        {
            Log.e(TAG, "Failed to sync comics.", e);
            syncError = true;
        }

        if (!syncError && comics.getComicsCount() > 0)
        {
            final Uri latestComic = bulkInsert(comics);
            if (latestComic != null)
            {
                final Editor editor = Preferences.getPreferences(this).edit();
                editor.putLong(Preferences.COMIC_LAST, ContentUris.parseId(latestComic));
                editor.commit();
            }
        }
    }

    private Uri bulkInsert(final ComicList comics)
    {
        final ContentResolver resolver = getContentResolver();
        Uri latestComic = null;
        final ContentValues values = new ContentValues(5);
        for (ComicList.Comic comic : comics.getComicsList())
        {
            values.put(Comic.NUMBER, comic.getNumber());
            values.put(Comic.TITLE, comic.getTitle());
            values.put(Comic.IMG_NAME, comic.getImgName());
            values.put(Comic.IMG_TYPE, comic.getImgType().getNumber());
            values.put(Comic.MESSAGE, comic.getMessage());
            latestComic = resolver.insert(Comic.CONTENT_URI, values);
            if (latestComic == null)
            {
                Log.e(TAG, "Failed to insert comic!");
                return null;
            }
            Log.d(TAG, "Synced " + latestComic);
        }
        return latestComic;
    }

    private int latestNumber()
    {
        Cursor cursor = null;
        try
        {
            cursor = mResolver.query(Comic.CONTENT_URI,
                    new String[] { "max(" + Comic.NUMBER + ')' }, null, null, null);
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

    private void syncImage(final Uri comic)
    {
        int imgSyncState = Comic.IMG_SYNC_STATE_OK;

        try
        {
            downloadImage(comic, getImageRequest(comic));
        }
        catch (IOException e)
        {
            Log.e(TAG, "Failed to sync image for " + comic, e);
            imgSyncState = Comic.IMG_SYNC_STATE_ERROR;
        }

        // Update sync state.
        final ContentValues values = new ContentValues(1);
        values.put(Comic.IMG_SYNC_STATE, imgSyncState);
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
                    new String[] { Comic.IMG_NAME, Comic.IMG_TYPE }, null, null, null);
            if (cursor == null || !cursor.moveToFirst())
            {
                throw new IllegalArgumentException("No comic at URI \"" + comic + "\".");
            }
            return new HttpGet("http://imgs.xkcd.com/comics/" + cursor.getString(0)
                    + Comic.getExt(cursor.getInt(1)));
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }
    }

    private void downloadImage(final Uri comic, final HttpGet request)
            throws IOException
    {
        InputStream in = null;
        OutputStream out = null;
        try
        {
            in = mClient.execute(request).getEntity().getContent();
            out = mResolver.openOutputStream(comic);
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
