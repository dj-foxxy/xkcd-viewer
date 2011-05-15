package com.appspot.mancocktail.xkcdviewer;

import java.io.File;
import java.io.FileNotFoundException;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQuery;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

public final class ComicContentProvider extends ContentProvider
{
    private static final String TAG = "ComicContentProvider";

    public static final String AUTHORITY = "com.appspot.mancocktail.xkcdviewer";

    private static final Uri CONTENT_URI_BASE =
            Uri.parse(ContentResolver.SCHEME_CONTENT + "://" + AUTHORITY);
    private static final String CONTENT_TYPE_TEMPLATE = "vnd.android.cursor.%s/vnd.mancocktail.%s";

    public static final class ComicTable implements BaseColumns
    {
        private static final String CONTENT_URI_PATH = "comics";
        public static final Uri CONTENT_URI = Uri.withAppendedPath(CONTENT_URI_BASE,
                CONTENT_URI_PATH);
        public static final String CONTENT_TYPE = String.format(CONTENT_TYPE_TEMPLATE, "dir",
                CONTENT_URI_PATH);
        public static final String CONTENT_ITEM_TYPE = String.format(CONTENT_TYPE_TEMPLATE, "item",
                CONTENT_URI_PATH);

        private static final String TABLE_NAME = "comic";

        public static final String NUMBER = "number";
        public static final String TITLE = "title";
        public static final String IMAGE_NAME = "image_name";
        public static final String IMAGE_TYPE = "image_type";
        public static final String MESSAGE = "message";
        public static final String IMAGE_SYNC_STATE = "image_sync_state";

        public static final int IMAGE_TYPE_JPEG = 0;
        public static final int IMAGE_TYPE_PNG = 1;

        public static final int IMAGE_SYNC_STATE_NOT_SYNCED = 0;
        public static final int IMAGE_SYNC_STATE_SYNCED = 1;

        private static final String DEFAULT_SORT = NUMBER + " DESC";

        public static String getExt(final int imageType)
        {
            switch (imageType)
            {
                case IMAGE_TYPE_JPEG:
                    return ".jpg";
                case IMAGE_TYPE_PNG:
                    return ".png";
                default:
                    throw new IllegalArgumentException("Invalid image type: " + imageType);
            }
        }

        public static Uri getUri(final long comicId)
        {
            return ContentUris.withAppendedId(CONTENT_URI, comicId);
        }
    }

    private final class RefCountingCursor extends SQLiteCursor
    {
        public RefCountingCursor(SQLiteDatabase db, SQLiteCursorDriver driver, String editTable,
                SQLiteQuery query)
        {
            super(db, driver, editTable, query);
            aquireDb();
        }

        @Override
        public void close()
        {
            super.close();
            Log.d(TAG, "[C] Releasing from cursor " + hashCode());
            releaseDb();
        }
    }

    private final class RefCountingCursorFactory implements CursorFactory
    {
        @Override
        public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery,
                String editTable, SQLiteQuery query)
        {
            return new RefCountingCursor(db, masterQuery, editTable, query);
        }
    }

    private static final int URI_COMICS = 0;
    private static final int URI_COMIC = 1;
    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static
    {
        sUriMatcher.addURI(AUTHORITY, ComicTable.CONTENT_URI_PATH, URI_COMICS);
        sUriMatcher.addURI(AUTHORITY, ComicTable.CONTENT_URI_PATH + "/#", URI_COMIC);
    }

    private final class ComicDbOpenHelper extends SQLiteOpenHelper
    {
        private static final String DATABASE_NAME = "comics.db";
        private static final int DATABASE_VERSION = 13;

        public ComicDbOpenHelper(Context context)
        {
            super(context, DATABASE_NAME, new RefCountingCursorFactory(), DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db)
        {
            //@formatter:off
            final String sql =

                "CREATE TABLE " + ComicTable.TABLE_NAME + "\n" +
                "(\n\t" +
                    ComicTable._ID              + " INTEGER PRIMARY KEY AUTOINCREMENT,\n\t" +
                    ComicTable.NUMBER           + " INTEGER UNIQUE      NOT NULL,\n\t" +
                    ComicTable.TITLE            + " TEXT    NOT NULL,\n\t" +
                    ComicTable.IMAGE_NAME       + " TEXT    NOT NULL,\n\t" +
                    ComicTable.IMAGE_TYPE       + " INTEGER NOT NULL,\n\t" +
                    ComicTable.MESSAGE          + " TEXT    NOT NULL,\n\t" +
                    ComicTable.IMAGE_SYNC_STATE + " INTEGER NOT NULL " +
                        "DEFAULT " + ComicTable.IMAGE_SYNC_STATE_NOT_SYNCED + "\n" +
                ");";

            //@formatter:on
            Log.v(TAG, sql);
            db.execSQL(sql);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
        {
            Log.w(TAG, "Updating DB from version " + oldVersion + " to verison " + newVersion);
            db.execSQL("DROP TABLE IF EXISTS " + ComicTable.TABLE_NAME);
            onCreate(db);
        }
    }

    private Context mContext;
    private ContentResolver mResolver;
    private ComicDbOpenHelper mOpenHelper;

    @Override
    public boolean onCreate()
    {
        mContext = getContext();
        mResolver = mContext.getContentResolver();
        mOpenHelper = new ComicDbOpenHelper(mContext);
        return true;
    }

    private final Object mDbRefUpdateLock = new Object();
    private int dbRefCount = 0;
    private SQLiteDatabase mDb = null;

    private void aquireDb()
    {
        synchronized (mDbRefUpdateLock)
        {
            if (dbRefCount == 0)
            {
                /*
                 * Always use a writable database, even for queries. This stops
                 * write operations mucking up read operations. It's not an
                 * ideal solution because a writable database consumes more
                 * memory, time and power but it's better than the program
                 * crashing.
                 */
                mDb = mOpenHelper.getWritableDatabase();
            }

            dbRefCount++;
            Log.d(TAG, "[A] Database aquired, ref. count " + dbRefCount);
        }
    }

    private void releaseDb()
    {
        if (dbRefCount <= 0)
        {
            throw new IllegalStateException(
                    "Database released more times that it has been aquired");
        }
        synchronized (mDbRefUpdateLock)
        {
            if (--dbRefCount == 0)
            {
                mDb.close();
                mDb = null;
            }
            Log.d(TAG, "[R] Database released, ref. count " + dbRefCount);
        }
    }

    @Override
    public Uri insert(final Uri uri, final ContentValues values)
    {
        if (sUriMatcher.match(uri) != URI_COMICS)
        {
            throw new IllegalArgumentException("Invalid insertion URI: " + uri);
        }

        final long comicId;
        aquireDb();
        try
        {
            comicId = mDb.insertOrThrow(ComicTable.TABLE_NAME, ComicTable.TITLE, values);
        }
        finally
        {
            releaseDb();
        }

        final Uri comicUri = ComicTable.getUri(comicId);
        mResolver.notifyChange(comicUri, null);
        return comicUri;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder)
    {
        final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        switch (sUriMatcher.match(uri))
        {
            case URI_COMIC:
                qb.appendWhere(ComicTable._ID + "=" + ContentUris.parseId(uri));
            case URI_COMICS:
                qb.setTables(ComicTable.TABLE_NAME);
                if (!TextUtils.isEmpty(sortOrder))
                {
                    sortOrder = ComicTable.DEFAULT_SORT;
                }
                break;
            default:
                throw new IllegalArgumentException("Illegal URI: " + uri);
        }

        final Cursor cursor;
        aquireDb();
        try
        {
            cursor = qb.query(mDb, projection, selection, selectionArgs, null, null, sortOrder);
        }
        finally
        {
            releaseDb();
        }

        if (cursor != null)
        {
            cursor.setNotificationUri(mResolver, uri);
        }

        return cursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs)
    {
        return updateOrDelete(uri, values, selection, selectionArgs, true);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs)
    {
        return updateOrDelete(uri, null, selection, selectionArgs, false);
    }

    private int updateOrDelete(final Uri uri, final ContentValues values, String selection,
            final String[] selectionArgs, final boolean doUpdate)
    {
        final int matchCode = sUriMatcher.match(uri);

        if (matchCode != URI_COMIC && matchCode != URI_COMICS)
        {
            throw new IllegalArgumentException("Illegal URI: " + uri);
        }

        if (matchCode == URI_COMIC)
        {
            selection = ComicTable._ID + '=' + ContentUris.parseId(uri)
                    + (TextUtils.isEmpty(selection) ? "" : " AND (" + selection + ')');
        }

        final int count;
        aquireDb();
        try
        {
            if (doUpdate)
            {
                count = mDb.update(ComicTable.TABLE_NAME, values, selection, selectionArgs);
            }
            else
            {
                count = mDb.delete(ComicTable.TABLE_NAME, selection, selectionArgs);
            }
        }
        finally
        {
            releaseDb();
        }

        mResolver.notifyChange(uri, null);

        return count;
    }

    @Override
    public ParcelFileDescriptor openFile(final Uri comicUri, final String mode)
            throws FileNotFoundException
    {
        if (sUriMatcher.match(comicUri) != URI_COMIC)
        {
            throw new FileNotFoundException("Must be comic URI, got " + comicUri);
        }
        if (!comicExists(comicUri))
        {
            throw new FileNotFoundException("No comic exists at " + comicUri);
        }
        return ParcelFileDescriptor.open(new File(getImagePath(comicUri)), modeToModeBits(mode));
    }

    private boolean comicExists(final Uri comic) throws FileNotFoundException
    {
        Cursor cursor = null;
        try
        {
            cursor = query(comic, new String[] { "count(1)" }, null, null, null);
            if (cursor == null)
            {
                throw new FileNotFoundException("Could not determine if comic exits.");
            }
            return cursor.getCount() == 1;
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }
    }

    private String getImagePath(final Uri comic) throws FileNotFoundException
    {
        final File externalFilesDir = mContext.getExternalFilesDir(null);
        if (externalFilesDir == null)
        {
            throw new FileNotFoundException("External files directory is currently not avaliable.");
        }
        return externalFilesDir.getPath() + File.separatorChar + ContentUris.parseId(comic);
    }

    @Override
    public String getType(final Uri uri)
    {
        switch (sUriMatcher.match(uri))
        {
            case URI_COMICS:
                return ComicTable.CONTENT_TYPE;
            case URI_COMIC:
                return ComicTable.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Illegal URI: " + uri);
        }
    }

    // Copied from hidden method within ContentResolver.
    private static int modeToModeBits(final String mode) throws FileNotFoundException
    {
        if ("r".equals(mode))
        {
            return ParcelFileDescriptor.MODE_READ_ONLY;
        }
        else if ("w".equals(mode) || "wt".equals(mode))
        {
            return ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_TRUNCATE;
        }
        else if ("wa".equals(mode))
        {
            return ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_APPEND;
        }
        else if ("rw".equals(mode))
        {
            return ParcelFileDescriptor.MODE_READ_WRITE
                    | ParcelFileDescriptor.MODE_CREATE;
        }
        else if ("rwt".equals(mode))
        {
            return ParcelFileDescriptor.MODE_READ_WRITE
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_TRUNCATE;
        }
        else
        {
            throw new FileNotFoundException("Bad mode for : " + mode);
        }
    }
}
