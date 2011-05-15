package com.appspot.mancocktail.xkcdviewer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;

import com.appspot.mancocktail.xkcdviewer.ComicContentProvider.ComicTable;

public class ComicSyncServiceHelper
{
    @SuppressWarnings("unused")
    private static final String TAG = "ComicSyncServericHelper";

    public interface OnImageDownloadedListener
    {
        void onImageDownloaded(Intent requestIntent);
    }

    private static final Set<Long> sDownloadingImages = new HashSet<Long>();

    private static final Map<Long, Set<OnImageDownloadedListener>> sOnImageDownloadedListeners =
            new HashMap<Long, Set<OnImageDownloadedListener>>();

    public interface OnSyncCompleteListener
    {
        void onSyncComplete(Intent requestIntent);
    }

    private static boolean isSyncing = false;

    private static final Set<OnSyncCompleteListener> sOnSyncCompleteListeners =
            new HashSet<OnSyncCompleteListener>();

    public static boolean downloadImageAndRegister(final Context context, final long comicId,
            final OnImageDownloadedListener listener)
    {
        synchronized (sOnImageDownloadedListeners)
        {
            downloadImage(context, comicId);
            return registerOnImageDownloadedListener(listener, comicId);
        }
    }

    public static boolean downloadImage(final Context context, final long comicId)
    {
        final Intent intent = new Intent(context, ComicSyncService.class);
        intent.setAction(ComicSyncService.ACTION_DOWNLOAD);
        intent.setData(ComicTable.getUri(comicId));
        synchronized (sOnImageDownloadedListeners)
        {
            if (context.startService(intent) != null)
            {
                sDownloadingImages.add(comicId);
                return true;
            }
            return false;
        }
    }

    public static boolean syncAndRegister(final Context context,
            final OnSyncCompleteListener listener)
    {
        synchronized (sOnSyncCompleteListeners)
        {
            sync(context);
            return registerOnSyncCompleteListener(listener);
        }
    }

    public static boolean sync(final Context context)
    {
        final Intent intent = new Intent(context, ComicSyncService.class);
        intent.setAction(Intent.ACTION_SYNC);
        synchronized (sOnSyncCompleteListeners)
        {
            if (!isSyncing)
            {
                isSyncing = context.startService(intent) != null;
            }
            return isSyncing;
        }
    }

    public static boolean registerOnImageDownloadedListener(
            final OnImageDownloadedListener listener, final long comicId)
    {
        synchronized (sOnImageDownloadedListeners)
        {
            final Set<OnImageDownloadedListener> listeners;
            if (sOnImageDownloadedListeners.containsKey(comicId))
            {
                listeners = sOnImageDownloadedListeners.get(comicId);
            }
            else
            {
                listeners = new HashSet<OnImageDownloadedListener>(1);
            }
            sOnImageDownloadedListeners.put(comicId, listeners);
            listeners.add(listener);
            return sDownloadingImages.contains(comicId);
        }
    }

    public static void unregisterOnImageDownloadedListener(OnImageDownloadedListener listener)
    {
        synchronized (sOnImageDownloadedListeners)
        {
            sOnImageDownloadedListeners.remove(listener);
        }
    }

    public static boolean registerOnSyncCompleteListener(OnSyncCompleteListener listener)
    {
        synchronized (sOnSyncCompleteListeners)
        {
            sOnSyncCompleteListeners.add(listener);
            return isSyncing;
        }
    }

    public static void unregisterOnSyncCompleteListener(OnSyncCompleteListener listener)
    {
        synchronized (sOnSyncCompleteListeners)
        {
            sOnSyncCompleteListeners.remove(listener);
        }
    }

    // Must not be called on the main thread.
    public static void handledIntent(final Intent requestIntent)
    {
        if (ComicSyncService.ACTION_DOWNLOAD.equals(requestIntent.getAction()))
        {
            final long comicId = ContentUris.parseId(requestIntent.getData());
            synchronized (sOnImageDownloadedListeners)
            {
                sDownloadingImages.remove(comicId);
                notifyOnImageDownloadListeners(sOnImageDownloadedListeners.get(comicId),
                        requestIntent);
            }
        }
        else if (Intent.ACTION_SYNC.equals(requestIntent.getAction()))
        {
            synchronized (sOnSyncCompleteListeners)
            {
                isSyncing = false;
                notifyOnSyncCompleteListeners(sOnSyncCompleteListeners, requestIntent);
            }
        }
    }

    private static void notifyOnImageDownloadListeners(
            final Set<OnImageDownloadedListener> listeners, final Intent requestIntent)
    {
        if (listeners != null)
        {
            for (final OnImageDownloadedListener listener : listeners)
            {
                listener.onImageDownloaded(requestIntent);
            }
        }
    }

    private static void notifyOnSyncCompleteListeners(final Set<OnSyncCompleteListener> listeners,
            final Intent requestIntent)
    {
        if (listeners != null)
        {
            for (OnSyncCompleteListener listener : listeners)
            {
                listener.onSyncComplete(requestIntent);
            }
        }
    }

    private ComicSyncServiceHelper()
    {
        throw new AssertionError();
    }
}
