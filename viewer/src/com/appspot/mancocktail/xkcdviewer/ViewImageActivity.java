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
    private static final int DIALOG_ID_COMIC_INFO = 1;
    private static final String DIALOG_EXTRA_TITLE = "title";
    private static final String DIALOG_EXTRA_MESSAGE = "message";
    private boolean isOnlineCursor = true;
    private Comic mComic;
    private ConnectivityManager mConnectivityManager;
    private Cursor mCursor;
    private ImageView mImage;
    private LoadComicTask mLoadComicTask;
    private LoadComicsCursorTask mLoadComicsCursorTask;
    private LoadSharedPreferences mInitialActivityTask;
    private SharedPreferences mPrefsCache;

    private final class LoadSharedPreferences extends AsyncTask<Void, Void, SharedPreferences>
    {
        @Override
        protected SharedPreferences doInBackground(Void... params)
        {
            return getSharedPreferences(Prefs.NAME, MODE_PRIVATE);
        }

        @Override
        protected void onPostExecute(SharedPreferences prefs)
        {
            mPrefsCache = prefs;
            prefs.registerOnSharedPreferenceChangeListener(mSyncListener);
            startLoadComicsCursorTask();
        }
    }

    private final class LoadComicsCursorTask extends AsyncTask<Void, Void, Cursor>
    {
        @Override
        protected Cursor doInBackground(Void... params)
        {
            final String selection = isOnlineCursor ? null : ComicTable.IMG_SYNC_STATE + "="
                    + ComicTable.IMG_SYNC_STATE_OK;

            final Cursor comics = managedQuery(ComicTable.CONTENT_URI, null, selection, null, null);
            if (comics == null)
            {
                return null;
            }

            final long currentComic = mPrefsCache.getLong(Prefs.COMIC_CURRENT, -1);

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
            if (mCursor != null)
            {
                stopManagingCursor(mCursor);
                mCursor.close();
            }
            mCursor = comics;
            Log.d(TAG, "Cursor " + mCursor);
            if (mCursor != null && mCursor.getCount() > 0)
            {
                startLoadComicTask();
            }
        }
    }

    private final class LoadComicTask extends AsyncTask<Cursor, Void, Comic>
    {
        private final Object mImageDownloadLock = new Object();

        @Override
        protected Comic doInBackground(Cursor... params)
        {
            final Cursor comics = params[0];

            final long number = comics.getLong(comics.getColumnIndexOrThrow(ComicTable.NUMBER));

            final Editor editor = mPrefsCache.edit();
            editor.putLong(Prefs.COMIC_CURRENT, number);
            if (!editor.commit())
            {
                Log.w(TAG, "Failed to update current comic number.");
                return null;
            }

            final Uri comicUri = ContentUris.withAppendedId(ComicTable.CONTENT_URI, number);
            final ContentResolver resolver = getContentResolver();

            Log.d(TAG, "Sync state: " + comics.getInt(comics
                    .getColumnIndexOrThrow(ComicTable.IMG_SYNC_STATE)));
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

    @Override
    protected void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mImage = (ImageView) findViewById(R.id.comic);

        mConnectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        isOnlineCursor = shouldBeOnlineCursor();
        registerReceiver(mNetworkStateChangeReceiver, new IntentFilter(
                ConnectivityManager.CONNECTIVITY_ACTION));
        mCursor = (Cursor) getLastNonConfigurationInstance();
        mInitialActivityTask = (LoadSharedPreferences) new LoadSharedPreferences()
                .execute((Void) null);

    }

    private boolean shouldBeOnlineCursor()
    {
        final NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();
        return info != null && info.getState() == NetworkInfo.State.CONNECTED;
    }

    private void startLoadComicsCursorTask()
    {
        if (mLoadComicsCursorTask != null)
        {
            mLoadComicsCursorTask.cancel(true);
        }
        mLoadComicsCursorTask = (LoadComicsCursorTask) new LoadComicsCursorTask()
                .execute((Void) null);
    }

    private void startLoadComicTask()
    {
        if (mLoadComicTask != null)
        {
            mLoadComicTask.cancel(true);
        }
        mLoadComicTask = (LoadComicTask) new LoadComicTask().execute(mCursor);
    }

    private void displayComic()
    {
        if (mComic != null)
        {
            mImage.setImageBitmap(mComic.getImage());
        }
    }

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
        Log.d(TAG, "Inflating menu");
        getMenuInflater().inflate(R.menu.view_menu, menu);
        return true;
    }

    @Override
    public Object onRetainNonConfigurationInstance()
    {
        return mCursor;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.first:
                if (mCursor != null && !mCursor.isFirst() && mCursor.moveToFirst())
                {
                    startLoadComicTask();
                }
                return true;
            case R.id.prev:
                if (mCursor != null && !mCursor.isFirst() && mCursor.moveToPrevious())
                {
                    startLoadComicTask();
                }
                return true;
            case R.id.rand:
                if (mCursor != null
                        && mCursor.moveToPosition((int) (Math.random() * mCursor.getCount())))
                {
                    startLoadComicTask();
                }
                return true;
            case R.id.next:
                if (mCursor != null && !mCursor.isLast() && mCursor.moveToNext())
                {
                    startLoadComicTask();
                }
                return true;
            case R.id.last:
                if (mCursor != null && mCursor.moveToLast())
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

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu)
    {
        menu.getItem(0).setEnabled(mCursor != null && !mCursor.isFirst());
        menu.getItem(1).setEnabled(mCursor != null && !mCursor.isFirst());
        menu.getItem(2).setEnabled(mCursor != null && mCursor.getCount() > 0);
        menu.getItem(3).setEnabled(mCursor != null && !mCursor.isLast());
        menu.getItem(4).setEnabled(mCursor != null && !mCursor.isLast());
        menu.getItem(5).setEnabled(isOnlineCursor);
        return super.onPrepareOptionsMenu(menu);
    }

    public void onImageClick(final View view)
    {
        if (mComic == null)
            return;

        final Bundle dialogArgs = new Bundle(2);
        dialogArgs.putString(DIALOG_EXTRA_TITLE,
                "[" + mComic.getNumber() + "] " + mComic.getTitle());
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
        if (mPrefsCache != null)
        {
            mPrefsCache.unregisterOnSharedPreferenceChangeListener(mSyncListener);
        }
        super.onDestroy();
    }
}
