package com.appspot.mancocktail.xkcdviewer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

public class ViewImageActivity extends Activity
{
    @SuppressWarnings("unused")
    private static final String TAG = "ViewImageActivity";
    private ImageView mComic;
    private LoadComicTask mTask = null;

    private class LoadComicTask extends AsyncTask<Integer, String, Comic>
    {
        private static final String TAG = "DownloadLoadImageTask";

        private final Context mContext;
        private final ContentResolver mReslover;
        private ProgressDialog mProgress = null;

        public LoadComicTask()
        {
            super();
            mContext = ViewImageActivity.this;
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
            final SharedPreferences prefs = Preferences.getPreferences(mContext);
            prefs.getInt(Preferences.COMIC_CURRENT, -1);

            // The number of the comic to load. If -1, load the last viewed
            // comic.
            int number = params[0];



            // If -1, load the last viewed comic.
            if (number == -1)
            {
                number = prefs.getInt(Preferences.LAST_VIEWED_COMIC_NUMBER, -1);

                // If -1, a comic has not been viewed yet, so view the latest
                // one.
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
                mComic.setImageBitmap(comic.getImage());
                message = comic.getMessage();
                title = comic.getTitle();
            }
            else
            {
                Toast.makeText(mContext, "Can't load comic.", Toast.LENGTH_SHORT).show();
            }
            mProgress.dismiss();
        }
    }

    private int number = 600;
    private int numberMax = 891;

    @Override
    protected void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mComic = (ImageView) findViewById(R.id.comic);
        loadComic();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if (mTask != null)
        {
            mTask.cancel(true);
            mTask = null; // Avoid memory leak.
        }
    }

    private String title;
    private String message;

    public void onImageClick(final View view)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message).setTitle(title);
        builder.create().show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.view_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.first:
                if (number > 1)
                {
                    number = 1;
                    loadComic();
                }
                return true;
            case R.id.prev:
                if (number > 1)
                {
                    number--;
                    loadComic();
                }
                return true;
            case R.id.rand:
                return true;
            case R.id.next:
                if (number < numberMax)
                {
                    number++;
                    loadComic();
                }
                return true;
            case R.id.last:
                number = numberMax;
                loadComic();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void loadComic()
    {
        if (mTask != null)
        {
            mTask.cancel(true);
        }
        mTask = (LoadComicTask) new LoadComicTask().execute(number);
    }
}
