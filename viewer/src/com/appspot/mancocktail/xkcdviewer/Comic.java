package com.appspot.mancocktail.xkcdviewer;

import android.graphics.Bitmap;

public class Comic
{
    private final Bitmap mImage;
    private final long mNumber;
    private final String mTitle;
    private final String mMessage;

    public Comic(final Bitmap image, final long number, final String title, final String message)
    {
        mImage = image;
        mNumber = number;
        mTitle = title;
        mMessage = message;
    }

    public Bitmap getImage()
    {
        return mImage;
    }

    public long getNumber()
    {
        return mNumber;
    }

    public String getTitle()
    {
        return mTitle;
    }

    public String getMessage()
    {
        return mMessage;
    }
}
