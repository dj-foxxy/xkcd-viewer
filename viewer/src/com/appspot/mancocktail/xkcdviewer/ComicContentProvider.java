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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

public class ComicContentProvider extends ContentProvider
{
    private static final String TAG = "ComicContentProvider";

    public static final String AUTHORITY = "com.appspot.mancocktail.xkcdviewer";

    private static final Uri CONTENT_URI_BASE =
            Uri.parse(ContentResolver.SCHEME_CONTENT + "://" + AUTHORITY);
    private static final String CONTENT_TYPE_TEMPLATE = "vnd.android.cursor.%s/vnd.mancocktail.%s";

    public static class ComicTable implements BaseColumns
    {
        private static final String CONTENT_URI_PATH = "comics";
        public static final Uri CONTENT_URI = Uri.withAppendedPath(CONTENT_URI_BASE,
                CONTENT_URI_PATH);
        public static final String CONTENT_TYPE = String.format(CONTENT_TYPE_TEMPLATE, "dir",
                CONTENT_URI_PATH);
        public static final String CONTENT_ITEM_TYPE = String.format(CONTENT_TYPE_TEMPLATE, "item",
                CONTENT_URI_PATH);

        private static final String TABLE_NAME = "comic";

        public static final String NUMBER = _ID;
        public static final String TITLE = "title";
        public static final String IMG_NAME = "img_name";
        public static final String IMG_TYPE = "img_type";
        public static final String MESSAGE = "message";
        public static final String IS_VALID = "is_valid";
        public static final String IMG_SYNC_STATE = "img_sync_state";

        public static final int IMG_TYPE_JPEG = 0;
        public static final int IMG_TYPE_PNG = 1;

        public static final int IMG_SYNC_STATE_OK = 0;
        public static final int IMG_SYNC_STATE_SYNCING = 1;
        public static final int IMG_SYNC_STATE_ERROR = 2;

        private static final String DEFAULT_SORT = NUMBER + " DESC";

        public static String getExt(final int imgType)
        {
            switch (imgType)
            {
                case IMG_TYPE_JPEG:
                    return ".jpg";
                case IMG_TYPE_PNG:
                    return ".png";
                default:
                    throw new IllegalArgumentException("Invalid image type: " + imgType);
            }
        }
    }

    private static class ComicDbOpenHelper extends SQLiteOpenHelper
    {
        private static final String DATABASE_NAME = "comics.db";
        private static final int DATABASE_VERSION = 8;

        public ComicDbOpenHelper(Context context)
        {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db)
        {
            //@formatter:off
            final String sql =
            "CREATE TABLE " + ComicTable.TABLE_NAME + '\n' +
            "(\n\t" +
                ComicTable.NUMBER         + " INTEGER PRIMARY KEY,\n\t" +
                ComicTable.TITLE          + " TEXT,\n\t" +
                ComicTable.IMG_NAME       + " TEXT,\n\t" +
                ComicTable.IMG_TYPE       + " INTEGER,\n\t" +
                ComicTable.MESSAGE        + " TEXT,\n\t" +
                ComicTable.IMG_SYNC_STATE + " INTEGER NOT NULL DEFAULT "
                            + ComicTable.IMG_SYNC_STATE_SYNCING + '\n' +
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

    private static final int URI_COMICS = 0;
    private static final int URI_COMIC = 1;
    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static
    {
        sUriMatcher.addURI(AUTHORITY, ComicTable.CONTENT_URI_PATH, URI_COMICS);
        sUriMatcher.addURI(AUTHORITY, ComicTable.CONTENT_URI_PATH + "/#", URI_COMIC);
    }

    private ComicDbOpenHelper mOpenHelper;

    @Override
    public boolean onCreate()
    {
        mOpenHelper = new ComicDbOpenHelper(getContext());
        return true;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values)
    {
        if (sUriMatcher.match(uri) != URI_COMICS)
        {
            throw new IllegalArgumentException("Invalid insertion URI: " + uri);
        }
        SQLiteDatabase db = null;
        final long comicId;
        try
        {
            db = mOpenHelper.getWritableDatabase();
            comicId = db.insertOrThrow(ComicTable.TABLE_NAME, ComicTable.TITLE, values);
        }
        finally
        {
            if (db != null)
            {
                db.close();
            }
        }
        final Uri comicUri = ContentUris.withAppendedId(ComicTable.CONTENT_URI, comicId);
        getContext().getContentResolver().notifyChange(comicUri, null);
        Log.d(TAG, "Returning " + comicUri);
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
                qb.appendWhere(ComicTable.NUMBER + '=' + ContentUris.parseId(uri));
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

        SQLiteDatabase db = null;
        Cursor cursor = null;
        try
        {
            db = mOpenHelper.getReadableDatabase();
            cursor = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
            return cursor;
        }
        catch (final RuntimeException e)
        {
            try
            {
                if (cursor != null)
                {
                    cursor.close();
                }
            }
            finally
            {
                if (db != null)
                {
                    db.close();
                }
            }
            throw e;
        }
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

    private int updateOrDelete(Uri uri, ContentValues values, String selection,
            String[] selectionArgs, boolean doUpdate)
    {
        final int matchCode = sUriMatcher.match(uri);
        if (matchCode != URI_COMIC && matchCode != URI_COMICS)
        {
            throw new IllegalArgumentException("Illegal URI: " + uri);
        }

        if (matchCode == URI_COMIC)
        {
            selection = ComicTable.NUMBER + '=' + ContentUris.parseId(uri)
                    + (TextUtils.isEmpty(selection) ? "" : " AND (" + selection + ')');
        }

        SQLiteDatabase db = null;
        final int count;
        try
        {
            db = mOpenHelper.getWritableDatabase();
            if (doUpdate)
            {
                count = db.update(ComicTable.TABLE_NAME, values, selection, selectionArgs);
            }
            else
            {
                count = db.delete(ComicTable.TABLE_NAME, selection, selectionArgs);
            }
        }
        finally
        {
            if (db != null)
            {
                db.close();
            }
        }

        /*
         * This MUST be called after the database is closed. Otherwise, you get
         * a bunch of race conditions.
         */
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public ParcelFileDescriptor openFile(final Uri comicUri, final String mode)
            throws FileNotFoundException
    {
        if (sUriMatcher.match(comicUri) != URI_COMIC)
        {
            throw new FileNotFoundException("Invalid URI: " + comicUri);
        }

        int fdMode;
        if (mode.equals("r"))
        {
            fdMode = ParcelFileDescriptor.MODE_READ_ONLY;
        }
        else if (mode.equals("w"))
        {
            fdMode = ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE;
        }
        else if (mode.equals("rw"))
        {
            fdMode = ParcelFileDescriptor.MODE_READ_WRITE
                    | ParcelFileDescriptor.MODE_CREATE;
        }
        else if (mode.equals("rwt"))
        {
            fdMode = ParcelFileDescriptor.MODE_READ_WRITE
                    | ParcelFileDescriptor.MODE_TRUNCATE
                    | ParcelFileDescriptor.MODE_CREATE;
        }
        else
        {
            throw new FileNotFoundException("Unsupported mode: " + mode);
        }

        // Check that the comic exists.
        Cursor cursor = null;
        try
        {
            cursor = getContext().getContentResolver().query(comicUri, new String[] { "count(1)" },
                    null, null, null);
            if (cursor == null || cursor.getCount() != 1)
            {
                throw new FileNotFoundException("No comic exists at " + comicUri);
            }
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }

        final String dir = getExternalFilesDir();
        return ParcelFileDescriptor.open(
                new File(dir + File.separatorChar + ContentUris.parseId(comicUri)), fdMode);
    }

    private String getExternalFilesDir() throws FileNotFoundException
    {
        final File externalFilesDir = getContext().getExternalFilesDir(null);
        if (externalFilesDir != null)
        {
            return externalFilesDir.getPath();
        }
        throw new FileNotFoundException("External files directory is currently not avaliable.");
    }

    @Override
    public String getType(Uri uri)
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
}
