package com.appspot.mancocktail.xkcdviewer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import com.appspot.mancocktail.xkcdviewer.ComicContentProvider.ComicTable;

public class ViewImageActivity extends Activity
{
    private static final String TAG = "ViewImageActivity";

    private SharedPreferences mPrefsCache = null;

    /*
     * Do no call on the main thread!
     */
    private SharedPreferences getSharedPreferences()
    {
        if (mPrefsCache == null)
        {
            mPrefsCache = getSharedPreferences(Prefs.NAME, MODE_PRIVATE);
        }
        return mPrefsCache;
    }

    private Cursor mComics = null;
    private boolean isOnlineCursor = true;

    private final class InitialActivity extends AsyncTask<Void, Void, Void>
    {
        @Override
        protected Void doInBackground(Void... params)
        {
            final SharedPreferences prefs = getSharedPreferences();
            prefs.registerOnSharedPreferenceChangeListener(mSyncListener);
            return null;
        }

        @Override
        protected void onPostExecute(Void result)
        {
            startLoadComicsCursorTask();
        }
    }

    private final class FinaliseActivityTask extends AsyncTask<Void, Void, Void>
    {
        @Override
        protected Void doInBackground(Void... params)
        {
            final SharedPreferences prefs = getSharedPreferences();
            prefs.unregisterOnSharedPreferenceChangeListener(mSyncListener);
            Log.d(TAG, "Unregisted.");
            return null;
        }
    }

    private final class LoadComicsCursorTask extends AsyncTask<Void, Void, Cursor>
    {
        @Override
        protected Cursor doInBackground(Void... params)
        {
            final String selection = isOnlineCursor ? null : ComicTable.IMG_SYNC_STATE + '='
                    + ComicTable.IMG_SYNC_STATE_OK;

            final Cursor comics = managedQuery(ComicTable.CONTENT_URI, null, selection, null, null);
            if (comics == null)
            {
                return null;
            }

            final SharedPreferences prefs = getSharedPreferences();
            final long currentComic = prefs.getLong(Prefs.COMIC_CURRENT, -1);

            // If a comic has been viewed and we have comics, try to find it in
            // the cursor.
            if (currentComic != -1 && comics.getCount() > 0)
            {
                final int numberColumn = comics.getColumnIndexOrThrow(ComicTable.NUMBER);
                while (comics.moveToNext())
                {
                    if (comics.getLong(numberColumn) == currentComic)
                    {
                        return comics;
                    }
                }
            }

            comics.moveToLast();
            return comics;
        }

        @Override
        protected void onPostExecute(Cursor comics)
        {
            if (mComics != null)
            {
                stopManagingCursor(mComics);
                mComics.close();
            }
            mComics = comics;
            Log.d(TAG, "Cursor " + mComics);
            if (mComics != null && mComics.getCount() > 0)
            {
                startLoadComicTask();
            }
        }
    }

    private Comic mComic = null;

    private final class LoadComicTask extends AsyncTask<Cursor, Void, Comic>
    {
        private final Object mImageDownloadLock = new Object();

        @Override
        protected Comic doInBackground(Cursor... params)
        {
            final Cursor comics = params[0];

            final long number = comics.getLong(comics.getColumnIndexOrThrow(ComicTable.NUMBER));

            final Editor editor = getSharedPreferences().edit();
            editor.putLong(Prefs.COMIC_CURRENT, number);
            if (!editor.commit())
            {
                Log.w(TAG, "Failed to update current comic number.");
                return null;
            }

            final Uri comicUri = ContentUris.withAppendedId(ComicTable.CONTENT_URI, number);
            final ContentResolver resolver = getContentResolver();

            if (ComicTable.IMG_SYNC_STATE_OK != comics.getInt(comics
                    .getColumnIndexOrThrow(ComicTable.IMG_SYNC_STATE)))
            {
                // Must run in the application context.
                final Intent intent = new Intent(ViewImageActivity.this, ComicSyncService.class);
                intent.setAction(ComicSyncService.ACTION_DOWNLOAD);
                intent.setData(comicUri);
                final ContentObserver observer = new ContentObserver(null)
                {
                    @Override
                    public void onChange(boolean selfChange)
                    {
                        synchronized (mImageDownloadLock)
                        {
                            mImageDownloadLock.notify();
                        }
                    }
                };
                resolver.registerContentObserver(comicUri, false, observer);
                try
                {
                    synchronized (mImageDownloadLock)
                    {
                        startService(intent);
                        mImageDownloadLock.wait();
                    }
                }
                catch (InterruptedException e)
                {
                    Log.w(TAG, "Interrupted while waiting for download.", e);
                    return null;
                }
                finally
                {
                    resolver.unregisterContentObserver(observer);
                }
            }

            final Bitmap image;
            InputStream in = null;
            try
            {
                in = resolver.openInputStream(comicUri);
                image = BitmapFactory.decodeStream(in);
            }
            catch (FileNotFoundException e)
            {
                Log.w(TAG, "Could not open image file!");
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

            return new Comic(image, number,
                    comics.getString(comics.getColumnIndexOrThrow(ComicTable.TITLE)),
                    comics.getString(comics.getColumnIndexOrThrow(ComicTable.MESSAGE)));
        }

        @Override
        protected void onPostExecute(Comic comic)
        {
            mComic = comic;
            displayComic();
        }
    }

    private ImageView mImage;

    private final BroadcastReceiver mNetworkStateChangeReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(final Context context, final Intent intent)
        {
            final boolean shouldBeOnline = shouldBeOnlineCursor();
            if (isOnlineCursor != shouldBeOnline)
            {
                isOnlineCursor = shouldBeOnline;
                Log.d(TAG, "Reloading cursor. Online? " + isOnlineCursor);
                startLoadComicsCursorTask();
            }
        }
    };

    private final OnSharedPreferenceChangeListener mSyncListener = new OnSharedPreferenceChangeListener()
    {
        @Override
        public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences,
                final String key)
        {
            if (Prefs.LAST_SYNC.equals(key))
            {
                Log.d(TAG, "Sync finished, reloading cursor.");
                startLoadComicsCursorTask();
            }
        }
    };

    private InitialActivity mInitialActivityTask = null;

    @Override
    protected void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mImage = (ImageView) findViewById(R.id.comic);

        isOnlineCursor = shouldBeOnlineCursor();
        registerReceiver(mNetworkStateChangeReceiver, new IntentFilter(
                ConnectivityManager.CONNECTIVITY_ACTION));
        mInitialActivityTask = (InitialActivity) new InitialActivity().execute((Void) null);

    }

    private boolean shouldBeOnlineCursor()
    {
        final NetworkInfo info = ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE))
                .getActiveNetworkInfo();
        return info != null && info.getState() == NetworkInfo.State.CONNECTED;
    }

    private LoadComicsCursorTask mLoadComicsCursorTask = null;

    private void startLoadComicsCursorTask()
    {
        if (mLoadComicsCursorTask != null)
        {
            mLoadComicsCursorTask.cancel(true);
        }
        mLoadComicsCursorTask = (LoadComicsCursorTask) new LoadComicsCursorTask()
                .execute((Void) null);
    }

    private LoadComicTask mLoadComicTask = null;

    private void startLoadComicTask()
    {
        if (mLoadComicTask != null)
        {
            mLoadComicTask.cancel(true);
        }
        mLoadComicTask = (LoadComicTask) new LoadComicTask().execute(mComics);
    }

    private void displayComic()
    {
        if (mComic != null)
        {
            mImage.setImageBitmap(mComic.getImage());
        }
    }

    private static final int DIALOG_ID_COMIC_INFO = 1;
    private static final String DIALOG_EXTRA_TITLE = "title";
    private static final String DIALOG_EXTRA_MESSAGE = "message";

    @Override
    protected Dialog onCreateDialog(final int id, final Bundle args)
    {
        switch (id)
        {
            case DIALOG_ID_COMIC_INFO:
                return new AlertDialog.Builder(this)
                        .setTitle(args.getString(DIALOG_EXTRA_TITLE))
                        .setMessage(args.getString(DIALOG_EXTRA_MESSAGE))
                        .create();
            default:
                return super.onCreateDialog(id, args);
        }
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
                if (mComics != null && mComics.moveToFirst())
                {
                    startLoadComicTask();
                }
                return true;
            case R.id.prev:
                if (mComics != null && !mComics.isFirst() && mComics.moveToPrevious())
                {
                    startLoadComicTask();
                }
                return true;
            case R.id.rand:
                if (mComics != null
                        && mComics.moveToPosition((int) (Math.random() * mComics.getCount())))
                {
                    startLoadComicTask();
                }
                return true;
            case R.id.next:
                if (mComics != null && !mComics.isLast() && mComics.moveToNext())
                {
                    startLoadComicTask();
                }
                return true;
            case R.id.last:
                if (mComics != null && mComics.moveToLast())
                {
                    startLoadComicTask();
                }
                return true;
            case R.id.sync:
                final Intent intent = new Intent(this, ComicSyncService.class);
                intent.setAction(Intent.ACTION_SYNC);
                startService(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onImageClick(final View view)
    {
        if (mComic == null)
            return;

        final Bundle dialogArgs = new Bundle(2);
        dialogArgs.putString(DIALOG_EXTRA_TITLE, mComic.getTitle());
        dialogArgs.putString(DIALOG_EXTRA_MESSAGE, mComic.getMessage());
        removeDialog(DIALOG_ID_COMIC_INFO);
        showDialog(DIALOG_ID_COMIC_INFO, dialogArgs);
    }

    @Override
    protected void onPause()
    {
        if (mInitialActivityTask != null)
        {
            mInitialActivityTask.cancel(true);
        }
        if (mLoadComicsCursorTask != null)
        {
            mLoadComicsCursorTask.cancel(true);
        }
        if (mLoadComicTask != null)
        {
            mLoadComicTask.cancel(true);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy()
    {
        unregisterReceiver(mNetworkStateChangeReceiver);
        new FinaliseActivityTask().execute((Void) null);
        super.onDestroy();
    }
}
