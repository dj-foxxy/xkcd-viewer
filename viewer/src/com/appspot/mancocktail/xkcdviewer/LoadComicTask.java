package com.appspot.mancocktail.xkcdviewer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

public class LoadComicTask extends AsyncTask<Integer, String, Comic>
{
    private static final String TAG = "DownloadLoadImageTask";

    private final Context mContext;
    private final ContentResolver mReslover;
    private final ImageView mComicView;
    private ProgressDialog mProgress = null;

    public LoadComicTask(final ImageView comicView)
    {
        super();
        mComicView = comicView;
        mContext = mComicView.getContext();
        mReslover = mContext.getContentResolver();
    }

    @Override
    protected void onPreExecute()
    {
        mProgress = new ProgressDialog(mContext);
        mProgress.setMessage("Initialising");
        mProgress.setOnCancelListener(new OnCancelListener()
        {
            public void onCancel(final DialogInterface dialog)
            {
                cancel(true);
            }
        });
        mProgress.show();
    }

    @Override
    protected Comic doInBackground(final Integer... params)
    {
        // The number of the comic to load. If -1, load the last viewed comic.
        int number = params[0];

        final SharedPreferences prefs = Preferences.getPreferences(mContext);

        // If -1, load the last viewed comic.
        if (number == -1)
        {
            number = prefs.getInt(Preferences.LAST_VIEWED_COMIC_NUMBER, -1);

            // If -1, a comic has not been viewed yet, so view the latest one.
            if (number == -1)
            {
                number = prefs.getInt(Preferences.LATEST_COMIC_NUMBER, -1);

                // If -1, no comics have been synced, so sync comics.
                if (number == -1)
                {
                    try
                    {
                        publishProgress("Syncing comics");
                        number = new ComicSyncHelper(mContext).syncComics();
                    }
                    catch (InterruptedException e)
                    {
                        Log.d(TAG, "Sync comics interrupted.", e);
                        return null;
                    }

                    // If -1, the sync failed, nothing more can be done.
                    if (number == -1)
                    {
                        return null;
                    }
                }
            }
        }

        publishProgress("Loading comic " + number);

        Editor editor = prefs.edit();
        editor.putInt(Preferences.LAST_VIEWED_COMIC_NUMBER, number);
        editor.commit();

        publishProgress("Downloading comic " + number);
        boolean imageSyned = false;
        try
        {
            imageSyned = new ImageSyncHelper(mContext).syncImage(number);
        }
        catch (InterruptedException e)
        {
            Log.d(TAG, "Sync image interrupted.", e);
            return null;
        }

        if (!imageSyned)
        {
            Log.d(TAG, "Failed to sync image.");
            return null;
        }

        publishProgress("Reading comic " + number);

        final Uri comic =
                ContentUris.withAppendedId(ComicContentProvider.Comic.CONTENT_URI, number);

        final Bitmap image = tryLoadBitmap(comic);
        if (image == null)
        {
            Log.w(TAG, "Failed to load image");
            return null;
        }

        Cursor cursor = null;
        try
        {
            cursor = mReslover.query(comic, new String[] { ComicContentProvider.Comic.TITLE,
                    ComicContentProvider.Comic.MESSAGE }, null, null, null);
            if (cursor != null && cursor.moveToFirst())
            {
                return new Comic(image, number, cursor.getString(0), cursor.getString(1));
            }
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }
        return null;
    }

    private Bitmap tryLoadBitmap(final Uri comic)
    {
        InputStream in = null;
        try
        {
            in = mReslover.openInputStream(comic);
            return BitmapFactory.decodeStream(in);
        }
        catch (FileNotFoundException e)
        {
            Log.e(TAG, "Image file does not exist.", e);
            return null;
        }
        finally
        {
            if (in != null)
            {
                try
                {
                    in.close();
                }
                catch (IOException e)
                {
                    Log.e(TAG, "Failed to close image file.", e);
                }
            }
        }
    }

    @Override
    protected void onProgressUpdate(final String... values)
    {
        mProgress.setMessage(values[0]);
    }

    @Override
    protected void onPostExecute(final Comic comic)
    {
        if (comic != null)
        {
            mComicView.setImageBitmap(comic.getImage());
        }
        else
        {
            Toast.makeText(mContext, "Can't load comic.", Toast.LENGTH_SHORT).show();
        }
        mProgress.dismiss();
    }
}
