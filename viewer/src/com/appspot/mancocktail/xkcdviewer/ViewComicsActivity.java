package com.appspot.mancocktail.xkcdviewer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import android.widget.TextView;

import com.appspot.mancocktail.xkcdviewer.ComicContentProvider.ComicTable;
import com.appspot.mancocktail.xkcdviewer.ComicSyncServiceHelper.OnImageDownloadedListener;
import com.appspot.mancocktail.xkcdviewer.ComicSyncServiceHelper.OnSyncCompleteListener;

public final class ViewComicsActivity extends Activity implements OnSyncCompleteListener
{
    private static final String TAG = ViewComicsActivity.class.getSimpleName();

    private static final int COLUMN_ID = 0;
    private static final int COLUMN_NUMBER = 1;
    private Cursor mCursor;

    private final class LoadCursorTask extends AsyncTask<Void, Void, Cursor>
    {
        private final int NO_LAST_COMIC = -1;

        @Override
        protected Cursor doInBackground(final Void... noParams)
        {
            final String selection = isProbablyOnline() ? null : ComicTable.IMAGE_SYNC_STATE
                    + " = " + ComicTable.IMAGE_SYNC_STATE_SYNCED;
            final Cursor cursor = mResolver.query(ComicTable.CONTENT_URI,
                    new String[] { ComicTable._ID, ComicTable.NUMBER }, selection, null, null);
            if (cursor == null)
            {
                return null;
            }
            if (cursor.getCount() == 0 || isCancelled())
            {
                cursor.close();
                return null;
            }

            int lastComic = getPreferences().getInt(PREF_COMIC_LAST, NO_LAST_COMIC);
            if (lastComic != NO_LAST_COMIC)
            {
                while (cursor.moveToNext())
                {
                    if (cursor.getInt(COLUMN_NUMBER) == lastComic)
                    {
                        return cursor;
                    }
                }
            }

            cursor.moveToLast();
            return cursor;
        }

        @Override
        protected void onPostExecute(final Cursor cursor)
        {
            if (cursor != null)
            {
                if (mCursor != null)
                {
                    mCursor.close();
                }
                mCursor = cursor;
                if (!mLoadComicAttempt && !isSyncing)
                {
                    loadComic();
                }
            }
            else if (mImage.getDrawable() == null)
            {
                setMessage("No comics.");
            }
        }
    }

    private Comic mComic;

    private final class LoadComicTask extends AsyncTask<Long, Void, Comic> implements
            OnImageDownloadedListener
    {
        @Override
        protected void onPreExecute()
        {
            if (mImage.getDrawable() == null)
            {
                mMessage.setText("Loading comic.");
                mMessage.setVisibility(View.VISIBLE);
            }
        }

        private final int COLUMN_ID = 0;
        private final int COLUMN_NUMBER = 1;
        private final int COLUMN_TITLE = 2;
        private final int COLUMN_MESSAGE = 3;
        private final int COLUMN_IMAGE_SYNC_STATE = 4;

        private final Object mImageDowndloadLock = new Object();

        @Override
        protected Comic doInBackground(final Long... params)
        {
            final Uri comic = ComicTable.getUri(params[0]);

            final Cursor cursor = mResolver.query(comic,
                    new String[] {
                            ComicTable._ID,
                            ComicTable.NUMBER,
                            ComicTable.TITLE,
                            ComicTable.MESSAGE,
                            ComicTable.IMAGE_SYNC_STATE },
                    null, null, null);

            if (cursor == null)
            {
                return null;
            }
            if (cursor.getCount() != 1 || !cursor.moveToFirst())
            {
                cursor.close();
                return null;
            }

            final int number = cursor.getInt(COLUMN_NUMBER);
            if (cursor.getInt(COLUMN_IMAGE_SYNC_STATE) == ComicTable.IMAGE_SYNC_STATE_NOT_SYNCED)
            {
                Log.d(TAG, "Syncing image " + cursor.getLong(COLUMN_ID));
                synchronized (mImageDowndloadLock)
                {
                    ComicSyncServiceHelper.downloadImageAndRegister(ViewComicsActivity.this,
                            cursor.getLong(COLUMN_ID), this);
                    try
                    {
                        mImageDowndloadLock.wait();
                    }
                    catch (InterruptedException e)
                    {
                        cursor.close();
                        return null;
                    }
                    finally
                    {
                        ComicSyncServiceHelper.unregisterOnImageDownloadedListener(this);
                    }
                }
            }

            final String title = cursor.getString(COLUMN_TITLE);
            final String message = cursor.getString(COLUMN_MESSAGE);
            cursor.close();

            Bitmap image = null;
            InputStream is = null;
            try
            {
                is = mResolver.openInputStream(comic);
                // If null, decoding failed.
                image = BitmapFactory.decodeStream(is);
            }
            catch (FileNotFoundException e)
            {
                Log.w(TAG, "Comic + " + number + " image not found.", e);
            }
            finally
            {
                if (is != null)
                {
                    try
                    {
                        is.close();
                    }
                    catch (IOException e)
                    {
                        Log.w(TAG, "Failed to close comic " + number + " image file", e);
                    }
                }
            }
            getPreferences().edit().putInt(PREF_COMIC_LAST, number).commit();
            return new Comic(image, number, title, message);
        }

        @Override
        public void onImageDownloaded(Intent requestIntent)
        {
            synchronized (mImageDowndloadLock)
            {
                mImageDowndloadLock.notify();
            }
        }

        @Override
        protected void onPostExecute(final Comic comic)
        {
            if (comic == null)
            {
                return;
            }
            mComic = comic;

            final Bitmap image = mComic.getImage();
            if (image != null)
            {
                mImage.setImageBitmap(image);
                setMessage(null);
            }
            else
            {
                mImage.setImageBitmap(null);
                setMessage("Couldn't load image. Sorry.");
            }
        }
    }

    private ContentResolver mResolver;
    private ImageView mImage;
    private TextView mMessage;

    @Override
    protected void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.view_comics);
        mResolver = getContentResolver();
        mImage = (ImageView) findViewById(R.id.image);
        mMessage = (TextView) findViewById(R.id.message);
    }

    // Not called on the main thread.
    @Override
    public void onSyncComplete(Intent requestIntent)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                // By setting it here, we do not need to make it volatile.
                isSyncing = false;
                loadCursor();
            }
        });
    }

    private BroadcastReceiver mNetworkStateChangeReceiver = new BroadcastReceiver()
    {
        // Called on the main thread.
        @Override
        public void onReceive(final Context context, final Intent intent)
        {
            Log.d(TAG, "[N] Recieved network state change. Ignoring? "
                    + isInitialStickyBroadcast());
            if (!isInitialStickyBroadcast())
            {
                loadCursor();
            }
        }
    };

    private boolean isSyncing;

    @Override
    protected void onResume()
    {
        super.onResume();
        isSyncing = ComicSyncServiceHelper.registerOnSyncCompleteListener(this);
        if (isSyncing)
        {
            setMessage("Syncing, please wait.");
        }
        registerReceiver(mNetworkStateChangeReceiver, new IntentFilter(
                ConnectivityManager.CONNECTIVITY_ACTION));
        loadCursor();
    }

    @Override
    protected void onPause()
    {
        ComicSyncServiceHelper.unregisterOnSyncCompleteListener(this);
        unregisterReceiver(mNetworkStateChangeReceiver);
        if (mLoadCursorTask != null)
        {
            mLoadCursorTask.cancel(true);
        }
        if (mLoadComicTask != null)
        {
            mLoadComicTask.cancel(true);
        }
        if (mCursor != null)
        {
            mCursor.close();
            mCursor = null;
        }
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu)
    {
        getMenuInflater().inflate(R.menu.view_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu)
    {
        final boolean hasRows = mCursor != null && mCursor.getCount() > 0;
        menu.getItem(0).setEnabled(hasRows && !mCursor.isFirst()); // First
        menu.getItem(1).setEnabled(hasRows && !mCursor.isFirst()); // Prev
        menu.getItem(2).setEnabled(hasRows && mCursor.getCount() >= 2); // Rand
        menu.getItem(3).setEnabled(hasRows && !mCursor.isLast()); // Next
        menu.getItem(4).setEnabled(hasRows && !mCursor.isLast()); // Last
        menu.getItem(5).setEnabled(isProbablyOnline()); // Sync
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.first:
                if (mCursor != null && !mCursor.isFirst() && mCursor.moveToFirst())
                {
                    loadComic();
                }
                return true;
            case R.id.prev:
                if (mCursor != null && !mCursor.isFirst() && mCursor.moveToPrevious())
                {
                    loadComic();
                }
                return true;
            case R.id.rand:
                if (mCursor != null && mCursor.moveToPosition(getDifferentRandPosition()))
                {
                    loadComic();
                }
                return true;
            case R.id.next:
                if (mCursor != null && !mCursor.isLast() && mCursor.moveToNext())
                {
                    loadComic();
                }
                return true;
            case R.id.last:
                if (mCursor != null && mCursor.moveToLast())
                {
                    loadComic();
                }
                return true;
            case R.id.sync:
                ComicSyncServiceHelper.sync(this);
                if (mImage.getDrawable() == null && !mLoadComicAttempt)
                {
                    setMessage("Syncing, please wait.");
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private int getDifferentRandPosition()
    {
        if (mCursor == null || mCursor.getCount() < 2)
        {
            throw new IllegalStateException("Cannot get suitable ID from current cursor.");
        }
        int id = (int) (Math.random() * (mCursor.getCount() - 1));
        if (id >= mCursor.getPosition())
        {
            id++;
        }
        return id;
    }

    private static final int DIALOG_ID_COMIC_INFO = 0;
    private static final String DIALOG_EXTRA_TITLE = "title";
    private static final String DIALOG_EXTRA_MESSAGE = "message";

    @Override
    protected Dialog onCreateDialog(int id, Bundle args)
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

    public void onImageClick(View view)
    {
        final Bundle dialogArgs = new Bundle(2);
        dialogArgs.putString(DIALOG_EXTRA_TITLE,
                "[" + mComic.getNumber() + "] " + mComic.getTitle());
        dialogArgs.putString(DIALOG_EXTRA_MESSAGE, mComic.getMessage());
        removeDialog(DIALOG_ID_COMIC_INFO);
        showDialog(DIALOG_ID_COMIC_INFO, dialogArgs);
    }

    private static final String PREF_COMIC_LAST = "comic_last";
    private final Object mGetPreferencesLock = new Object();
    private SharedPreferences mPreferences = null;

    private SharedPreferences getPreferences()
    {
        synchronized (mGetPreferencesLock)
        {
            if (mPreferences == null)
            {
                mPreferences = getPreferences(MODE_PRIVATE);
            }
        }
        return mPreferences;
    }

    private boolean isProbablyOnline()
    {
        final ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        final NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        return info != null && info.getState() == NetworkInfo.State.CONNECTED;
    }

    private LoadCursorTask mLoadCursorTask = null;

    private void loadCursor()
    {
        if (mLoadCursorTask != null)
        {
            mLoadCursorTask.cancel(true);
        }
        mLoadCursorTask = (LoadCursorTask) new LoadCursorTask().execute((Void) null);
    }

    private boolean mLoadComicAttempt = false;
    private LoadComicTask mLoadComicTask = null;

    private void loadComic()
    {
        if (mLoadComicTask != null)
        {
            mLoadComicTask.cancel(true);
        }
        mLoadComicTask = (LoadComicTask) new LoadComicTask().execute(mCursor.getLong(COLUMN_ID));
        mLoadComicAttempt = true;
    }

    private void setMessage(final CharSequence message)
    {
        if (message != null)
        {
            mMessage.setText(message);
            mMessage.setVisibility(View.VISIBLE);
        }
        else
        {
            mMessage.setVisibility(View.GONE);
        }
    }
}
