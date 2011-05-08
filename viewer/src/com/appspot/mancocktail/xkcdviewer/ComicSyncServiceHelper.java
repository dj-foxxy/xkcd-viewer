package com.appspot.mancocktail.xkcdviewer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;

import com.appspot.mancocktail.xkcdviewer.ComicContentProvider.ComicTable;

public class ComicSyncServiceHelper {
    @SuppressWarnings("unused")
    private static final String TAG = "ComicSyncServericHelper";

    public interface OnImageDownloadedListener {
        void onImageDownloaded(Intent requestIntent);
    }

    private static final Set<Long> sDownloadingImages = new HashSet<Long>();

    private static final Map<Long, Set<OnImageDownloadedListener>> sOnImageDownloadedListeners =
            new HashMap<Long, Set<OnImageDownloadedListener>>();

    public interface OnSyncCompleteListener {
        void onSyncComplete(Intent requestIntent);
    }

    private static boolean isSyncing = false;

    private static final Set<OnSyncCompleteListener> sOnSyncCompleteListeners =
            new HashSet<OnSyncCompleteListener>();

    public static boolean downloadImageAndRegister(Context context, long comicId,
            OnImageDownloadedListener listener) {
        synchronized (sOnImageDownloadedListeners) {
            downloadImage(context, comicId);
            return registerOnImageDownloadedListener(listener, comicId);
        }
    }

    public static void downloadImage(Context context, long comicId) {
        Intent intent = new Intent(context, ComicSyncService.class);
        intent.setAction(ComicSyncService.ACTION_DOWNLOAD);
        intent.setData(ComicTable.getUri(comicId));
        synchronized (sOnImageDownloadedListeners) {
            sDownloadingImages.add(comicId);
            context.startService(intent);
        }
    }

    public static boolean syncAndRegister(Context context, OnSyncCompleteListener listener) {
        synchronized (sOnSyncCompleteListeners) {
            sync(context);
            return registerOnSyncCompleteListener(listener);
        }
    }

    public static void sync(Context context) {
        Intent intent = new Intent(context, ComicSyncService.class);
        intent.setAction(Intent.ACTION_SYNC);
        synchronized (sOnSyncCompleteListeners) {
            // Only start a sync if one is currently not running.
            if (!isSyncing) {
                isSyncing = true;
                context.startService(intent);
            }
        }
    }

    public static boolean registerOnImageDownloadedListener(OnImageDownloadedListener listener,
            long comicId) {
        synchronized (sOnImageDownloadedListeners) {
            Set<OnImageDownloadedListener> listeners;
            if (sOnImageDownloadedListeners.containsKey(comicId))
                listeners = sOnImageDownloadedListeners.get(comicId);
            else
                listeners = new HashSet<OnImageDownloadedListener>(1);
            sOnImageDownloadedListeners.put(comicId, listeners);
            listeners.add(listener);

            return sDownloadingImages.contains(comicId);
        }
    }

    public static void unregisterOnImageDownloadedListener(OnImageDownloadedListener listener) {
        synchronized (sOnImageDownloadedListeners) {
            sOnImageDownloadedListeners.remove(listener);
        }
    }

    public static boolean registerOnSyncCompleteListener(OnSyncCompleteListener listener) {
        synchronized (sOnSyncCompleteListeners) {
            sOnSyncCompleteListeners.add(listener);
            return isSyncing;
        }
    }

    public static void unregisterOnSyncCompleteListener(OnSyncCompleteListener listener) {
        synchronized (sOnSyncCompleteListeners) {
            sOnSyncCompleteListeners.remove(listener);
        }
    }

    // Must not be called on the main thread.
    public static void handledIntent(Intent requestIntent) {
        if (ComicSyncService.ACTION_DOWNLOAD.equals(requestIntent.getAction()))
            synchronized (sOnImageDownloadedListeners) {
                long comicId = ContentUris.parseId(requestIntent.getData());
                sDownloadingImages.remove(comicId);
                notifyOnImageDownloadListeners(sOnImageDownloadedListeners.get(comicId),
                        requestIntent);
            }
        else if (Intent.ACTION_SYNC.equals(requestIntent.getAction()))
            synchronized (sOnSyncCompleteListeners) {
                isSyncing = false;
                notifyOnSyncCompleteListeners(sOnSyncCompleteListeners, requestIntent);
            }
    }

    private static void notifyOnImageDownloadListeners(Set<OnImageDownloadedListener> listeners,
            Intent requestIntent) {
        if (listeners != null)
            for (OnImageDownloadedListener listener : listeners)
                listener.onImageDownloaded(requestIntent);
    }

    private static void notifyOnSyncCompleteListeners(Set<OnSyncCompleteListener> listeners,
            Intent requestIntent) {
        if (listeners != null)
            for (OnSyncCompleteListener listener : listeners)
                listener.onSyncComplete(requestIntent);
    }

    private ComicSyncServiceHelper() {
        throw new AssertionError();
    }
}
