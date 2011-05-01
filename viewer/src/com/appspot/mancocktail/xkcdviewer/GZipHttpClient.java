package com.appspot.mancocktail.xkcdviewer;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import android.util.Log;

public class GZipHttpClient extends DefaultHttpClient
{
    private static final String TAG = "GZipHttpClient";

    private static class GZipDecompressingEntity extends HttpEntityWrapper
    {
        public GZipDecompressingEntity(final HttpEntity entity)
        {
            super(entity);
        }

        @Override
        public InputStream getContent() throws IOException
        {
            return new GZIPInputStream(wrappedEntity.getContent());
        }

        @Override
        public long getContentLength()
        {
            return -1;
        }
    }

    public GZipHttpClient()
    {
        super();
        addInterceptors();
    }

    public GZipHttpClient(final ClientConnectionManager conman,
            final HttpParams params)
    {
        super(conman, params);
        addInterceptors();
    }

    public GZipHttpClient(final HttpParams params)
    {
        super(params);
        addInterceptors();
    }

    private void addInterceptors()
    {
        addRequestInterceptor(new HttpRequestInterceptor()
        {
            public void process(final HttpRequest request, final HttpContext context)
                    throws HttpException, IOException
            {
                if (!request.containsHeader("Accept-Encoding"))
                {
                    request.addHeader("Accept-Encoding", "gzip");
                }
            }
        });

        addResponseInterceptor(new HttpResponseInterceptor()
        {
            public void process(final HttpResponse response, final HttpContext context)
                    throws HttpException, IOException
            {
                final Header codecs = response.getEntity().getContentEncoding();
                if (codecs != null)
                {
                    for (final HeaderElement codec : codecs.getElements())
                    {
                        if (codec.getName().equalsIgnoreCase("gzip"))
                        {
                            Log.v(TAG, "Using GZIP.");
                            response.setEntity(new GZipDecompressingEntity(response.getEntity()));
                            return;
                        }
                    }
                }
            }
        });
    }
}
