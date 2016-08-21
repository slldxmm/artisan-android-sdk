package com.testbird.artisan.TestBirdAgent;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import com.testbird.artisan.TestBirdAgent.utils.DeviceInfoCapture;

import java.io.File;
import java.security.MessageDigest;


public class Constants {

    public static final String  JAVA_CRASH = "java_crash";
    public static final String  NATIVE_CRASH = "native_crash";
    public static final String  CAUGHT_EXCEPTION = "caught_exception";
    public static final String  ANR = "anr";
    public static final String  UNITY_CS_EXCEPTION = "unity_cs_exception";
    public static final String  UNITY_CS_CAUGHT_EXCEPTION = "unity_cs_caught_exception";
    public static final String  COCOS_LUA_EXCEPTOIN = "cocos_lua_exception";
    public static final String  COCOS_LUA_CAUGHT_EXCEPTOIN = "cocos_lua_caught_exception";
    public static final String  COCOS_JS_EXCEPTOIN = "cocos_js_exception";
    public static final String  COCOS_JS_CAUHGT_EXCEPTOIN = "cocos_js_caught_exception";
    public static final String  HTML5_JS_EXCEPTION = "html5_js_exception";
    public static final String  HTML5_JS_EXCEPTION_EXCEPTION = "html5_js_caught_exception";

    public static final String RUNTIME_JAVA = "java";
    public static final String RUNTIME_NDK = "ndk";
    public static final String RUNTIME_COCOS = "cocos";
    public static final String RUNTIME_UNITY = "unity";
    public static final String RUNTIME_HTML5 = "html5";

    /**
     * Path where crash logs and temporary files are stored.
     */
    public static String FILES_PATH = null;

    /**
     * The app's version code.
     */
    public static String APP_VERSION = null;

    /**
     * The app's version name.
     */
    public static String APP_VERSION_NAME = null;

    /**
     * The app's package name.
     */
    public static String APP_PACKAGE = null;

    /**
     * The device's OS version.
     */
    public static String ANDROID_VERSION = null;

    /**
     * The device's model name.
     */
    public static String PHONE_MODEL = null;

    /**
     * The device's model manufacturer name.
     */
    public static String PHONE_MANUFACTURER = null;

    /**
     * Unique identifier for crash reports.
     */
    public static String CRASH_IDENTIFIER = null;

    /**
     * Tag for internal logging statements.
     */
    public static final String TAG = "TestBird";

    public static final String REPORT_SUFFIX = ".report";

    public static final String SESSION_SUFFIX = ".session";

    public static String PROCESS_NAME = "null";

    /**
     * Initializes constants from the given context. The context is used to set the package name,
     * version code, and the files path.
     *
     * @param context The context to use. Usually your Activity object.
     */
    public static void loadFromContext(Context context) {
        Constants.ANDROID_VERSION = android.os.Build.VERSION.RELEASE;
        Constants.PHONE_MODEL = android.os.Build.MODEL;
        Constants.PHONE_MANUFACTURER = android.os.Build.MANUFACTURER;

        loadFilesPath(context);
        loadPackageData(context);
        loadCrashIdentifier(context);
    }

    /**
     * Helper method to set the files path. If an exception occurs, the files path will be null!
     *
     * @param context The context to use. Usually your Activity object.
     */
    private static void loadFilesPath(Context context) {
        if (context != null) {
            try {
                File file = context.getFilesDir();

                // The file shouldn't be null, but apparently it still can happen, see
                // http://code.google.com/p/android/issues/detail?id=8886
                if (file != null) {
                    PROCESS_NAME =
                        DeviceInfoCapture.getInstance()
                            .getProcessNameFromId(android.os.Process.myPid());
                    Constants.FILES_PATH = file.getAbsolutePath() + "/" + PROCESS_NAME;
                    File dir = new File(Constants.FILES_PATH);
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception thrown when accessing the files dir:");
                e.printStackTrace();
            }
        }
    }

    /**
     * Helper method to set the package name and version code. If an exception occurs, these values
     * will be null!
     *
     * @param context The context to use. Usually your Activity object.
     */
    private static void loadPackageData(Context context) {
        if (context != null) {
            try {
                PackageManager packageManager = context.getPackageManager();
                PackageInfo
                    packageInfo =
                    packageManager.getPackageInfo(context.getPackageName(), 0);
                Constants.APP_PACKAGE = packageInfo.packageName;
                Constants.APP_VERSION = "" + packageInfo.versionCode;
                Constants.APP_VERSION_NAME = packageInfo.versionName;

                int buildNumber = loadBuildNumber(context, packageManager);
                if ((buildNumber != 0) && (buildNumber > packageInfo.versionCode)) {
                    Constants.APP_VERSION = "" + buildNumber;
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception thrown when accessing the package info:");
                e.printStackTrace();
            }
        }
    }

    /**
     * Helper method to load the build number from the AndroidManifest.
     *
     * @param context        the context to use. Usually your Activity object.
     * @param packageManager an instance of PackageManager
     */
    private static int loadBuildNumber(Context context, PackageManager packageManager) {
        try {
            ApplicationInfo
                appInfo =
                packageManager
                    .getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            Bundle metaData = appInfo.metaData;
            if (metaData != null) {
                return metaData.getInt("buildNumber", 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception thrown when accessing the application info:");
            e.printStackTrace();
        }

        return 0;
    }

    /**
     * Helper method to load the crash identifier.
     *
     * @param context the context to use. Usually your Activity object.
     */
    private static void loadCrashIdentifier(Context context) {
        String
            deviceIdentifier =
            Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if ((Constants.APP_PACKAGE != null) && (deviceIdentifier != null)) {
            String
                combined =
                Constants.APP_PACKAGE + ":" + deviceIdentifier + ":" + createSalt(context);
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                byte[] bytes = combined.getBytes("UTF-8");
                digest.update(bytes, 0, bytes.length);
                bytes = digest.digest();

                Constants.CRASH_IDENTIFIER = bytesToHex(bytes);
            } catch (Throwable e) {
            }
        }
    }

    /**
     * Helper method to create a salt for the crash identifier.
     *
     * @param context the context to use. Usually your Activity object.
     */
    @SuppressLint("InlinedApi")
    @SuppressWarnings("deprecation")
    private static String createSalt(Context context) {
        String abiString;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            abiString = Build.SUPPORTED_ABIS[0];
        } else {
            abiString = Build.CPU_ABI;
        }

        String fingerprint = "HA" + (Build.BOARD.length() % 10) + (Build.BRAND.length() % 10) +
                             (abiString.length() % 10) + (Build.PRODUCT.length() % 10);
        String serial = "";
        try {
            serial = android.os.Build.class.getField("SERIAL").get(null).toString();
        } catch (Throwable t) {
        }

        return fingerprint + ":" + serial;
    }

    /**
     * Helper method to convert a byte array to the hex string. Based on
     * http://stackoverflow.com/questions/9655181/convert-from-byte-array-to-hex-string-in-java
     *
     * @param bytes a byte array
     */
    private static String bytesToHex(byte[] bytes) {
        final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
        char[] hex = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xFF;
            hex[index * 2] = HEX_ARRAY[value >>> 4];
            hex[index * 2 + 1] = HEX_ARRAY[value & 0x0F];
        }
        String result = new String(hex);
        return result.replaceAll("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
    }
}
