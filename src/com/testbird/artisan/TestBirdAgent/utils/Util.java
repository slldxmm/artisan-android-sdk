package com.testbird.artisan.TestBirdAgent.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.TextUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;


public class Util {


    public static final String APP_IDENTIFIER_PATTERN = "[0-9a-f]+";
    public static final int APP_IDENTIFIER_LENGTH = 8;
    public static final String APP_IDENTIFIER_KEY = "com.artisan.android.appIdentifier";
    public static final String LOG_IDENTIFIER = "Artisan";
    private static final Pattern
        appIdentifierPattern =
        Pattern.compile(APP_IDENTIFIER_PATTERN, Pattern.CASE_INSENSITIVE);

    /**
     * Returns the given param URL-encoded.
     *
     * @param param a string to encode
     * @return the encoded param
     */
    public static String encodeParam(String param) {
        try {
            return URLEncoder.encode(param, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // UTF-8 should be available, so just in case
            e.printStackTrace();
            return "";
        }
    }

    /**
     * Returns true if the Fragment API is supported (should be on Android 3.0+).
     *
     * @return true if the Fragment API is supported
     */
    @SuppressLint("NewApi")
    public static Boolean fragmentsSupported() {
        try {
            return (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB)
                   && classExists("android.app.Fragment");
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    /**
     * Sanitizes an app identifier or throws an exception if it can't be sanitized.
     *
     * @param appIdentifier the app identifier to sanitize
     * @return the sanitized app identifier
     * @throws java.lang.IllegalArgumentException if the app identifier can't be sanitized because
     *                                            of unrecoverable input character errors
     */
    public static String sanitizeAppIdentifier(String appIdentifier)
        throws IllegalArgumentException {

        if (appIdentifier == null) {
            throw new IllegalArgumentException("App Key must not be null.");
        }

        String sAppIdentifier = appIdentifier.trim();

        if (sAppIdentifier.length() != APP_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(
                "App Key length must be " + APP_IDENTIFIER_LENGTH + " characters.");
        }

        return sAppIdentifier;
    }

    /**
     * Converts a map of parameters to a HTML form entity.
     *
     * @param params the parameters
     * @return an URL-encoded form string ready for use in a HTTP post
     * @throws UnsupportedEncodingException when your system does not know how to handle the UTF-8
     *                                      charset
     */
    public static String getFormString(Map<String, String> params)
        throws UnsupportedEncodingException {
        List<String> protoList = new ArrayList<String>();
        for (String key : params.keySet()) {
            String value = params.get(key);
            key = URLEncoder.encode(key, "UTF-8");
            value = URLEncoder.encode(value, "UTF-8");
            protoList.add(key + "=" + value);
        }
        return TextUtils.join("&", protoList);
    }

    /**
     * Helper method to safely check whether a class exists at runtime.
     *
     * @param className the full-qualified class name to check for
     * @return whether the class exists
     */
    public static boolean classExists(String className) {
        try {
            return Class.forName(className) != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static String getAppIdentifier(Context context) {
        return getManifestString(context, APP_IDENTIFIER_KEY);
    }

    public static String getManifestString(Context context, String key) {
        return getBundle(context).getString(key);
    }

    private static Bundle getBundle(Context context) {
        Bundle bundle;
        try {
            bundle =
                context.getPackageManager().getApplicationInfo(context.getPackageName(),
                                                               PackageManager.GET_META_DATA).metaData;
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
        return bundle;
    }

    public static boolean isConnectedToNetwork(Context context) {
        ConnectivityManager
            connectivityManager =
            (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
        return false;
    }

}
