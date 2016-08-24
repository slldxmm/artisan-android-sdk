/*
 * Copyright (C) 2016 TestBird  - All Rights Reserved
 * You may use, distribute and modify this code under
 * the terms of the mit license.
 */
package com.testbird.artisan.TestBirdAgent.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * <h3>Description</h3>
 *
 * Builder class for HttpURLConnection.
 *
 * <h3>License</h3>
 *
 * <pre>
 * Copyright (c) 2011-2015 Bit Stadium GmbH
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 * </pre>
 *
 * @author Matthias Wenz
 */
public class HttpURLConnectionBuilder {

    private static final int DEFAULT_TIMEOUT = 2 * 60 * 1000;
    public static final String DEFAULT_CHARSET = "UTF-8";

    private final String mUrlString;

    private String mRequestMethod;
    private String mRequestBody;
    private SimpleMultipartEntity mMultipartEntity;
    private int mTimeout = DEFAULT_TIMEOUT;

    private final Map<String, String> mHeaders;

    public HttpURLConnectionBuilder(String urlString) {
        mUrlString = urlString;
        mHeaders = new HashMap<String, String>();
    }

    public HttpURLConnectionBuilder setRequestMethod(String requestMethod) {
        mRequestMethod = requestMethod;
        return this;
    }

    public HttpURLConnectionBuilder setRequestBody(String requestBody) {
        mRequestBody = requestBody;
        return this;
    }

    public HttpURLConnectionBuilder writeFormFields(Map<String, String> fields) {
        try {
            String formString = getFormString(fields, DEFAULT_CHARSET);
            setHeader("Content-Type", "application/x-www-form-urlencoded");
            setRequestBody(formString);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public HttpURLConnectionBuilder writeFromJsonString(String json) {
        setHeader("Content-Type", "application/json");
        setHeader("Accept-Encoding", "gzip");
        setRequestBody(json);
        return this;
    }

    public HttpURLConnectionBuilder writeMultipartData(Map<String, String> fields, Context context,
                                                       List<Uri> attachmentUris) {
        try {
            mMultipartEntity = new SimpleMultipartEntity();
            mMultipartEntity.writeFirstBoundaryIfNeeds();

            for (String key : fields.keySet()) {
                mMultipartEntity.addPart(key, fields.get(key));
            }

            for (int i = 0; i < attachmentUris.size(); i++) {
                Uri attachmentUri = attachmentUris.get(i);
                boolean lastFile = (i == attachmentUris.size() - 1);

                InputStream input = context.getContentResolver().openInputStream(attachmentUri);
                String filename = attachmentUri.getLastPathSegment();
                mMultipartEntity.addPart("attachment" + i, filename, input, lastFile);
            }
            mMultipartEntity.writeLastBoundaryIfNeeds();

            setHeader("Content-Type",
                      "multipart/form-data; boundary=" + mMultipartEntity.getBoundary());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return this;
    }

    public HttpURLConnectionBuilder setTimeout(int timeout) {
        if (timeout < 0) {
            throw new IllegalArgumentException("Timeout has to be positive.");
        }
        mTimeout = timeout;
        return this;
    }

    public HttpURLConnectionBuilder setHeader(String name, String value) {
        mHeaders.put(name, value);
        return this;
    }

    /**
     *
     * @return
     */
    public HttpURLConnection build() {
        HttpURLConnection connection;
        try {
            URL url = new URL(mUrlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(mTimeout);
            connection.setReadTimeout(mTimeout);
            connection.setRequestProperty("Content-Encoding", "gzip");
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD) {
                connection.setRequestProperty("Connection", "close");
            }
            if (!TextUtils.isEmpty(mRequestMethod)) {
                connection.setRequestMethod(mRequestMethod);
                if (!TextUtils.isEmpty(mRequestBody) || mRequestMethod.equalsIgnoreCase("POST")
                    || mRequestMethod.equalsIgnoreCase("PUT")) {
                    connection.setDoOutput(true);
                }
            }
            for (String name : mHeaders.keySet()) {
                connection.setRequestProperty(name, mHeaders.get(name));
            }

            if (!TextUtils.isEmpty(mRequestBody)) {
                OutputStream outputStream = connection.getOutputStream();
                byte[] array = gzip(mRequestBody.getBytes());
                if (array == null) {
                    ArtisanLogUtil.error("gzip compress error.");
                } else {
                    outputStream.write(array);
                    outputStream.flush();
                }
                outputStream.close();
            }

            if (mMultipartEntity != null) {
                connection.setRequestProperty("Content-Length",
                                              String.valueOf(mMultipartEntity.getContentLength()));
                BufferedOutputStream
                    outputStream =
                    new BufferedOutputStream(connection.getOutputStream());
                byte[] array = gzip(mMultipartEntity.getOutputStream().toByteArray());
                if (array == null) {
                    ArtisanLogUtil.error("gzip compress error.");
                } else {
                    outputStream.write(array);
                    outputStream.flush();
                }
                outputStream.close();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return connection;
    }

    private static byte[] gzip(byte[] input) {
        GZIPOutputStream gzipOS = null;
        try {
            ByteArrayOutputStream byteArrayOS = new ByteArrayOutputStream();
            gzipOS = new GZIPOutputStream(byteArrayOS);
            gzipOS.write(input);
            gzipOS.flush();
            gzipOS.close();
            gzipOS = null;
            return byteArrayOS.toByteArray();
        } catch (Exception e) {
            //
            return null;
        } finally {
            if (gzipOS != null) {
                try {
                    gzipOS.close();
                } catch (Exception ignored) {

                }
            }
        }
    }

    private static String getFormString(Map<String, String> params, String charset)
        throws UnsupportedEncodingException {
        List<String> protoList = new ArrayList<String>();
        for (String key : params.keySet()) {
            String value = params.get(key);
            key = URLEncoder.encode(key, charset);
            value = URLEncoder.encode(value, charset);
            protoList.add(key + "=" + value);
        }
        return TextUtils.join("&", protoList);
    }

}
