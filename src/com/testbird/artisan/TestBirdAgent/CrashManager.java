/*
 * Copyright (C) 2016 TestBird  - All Rights Reserved
 * You may use, distribute and modify this code under
 * the terms of the mit license.
 */
package com.testbird.artisan.TestBirdAgent;


import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.webkit.WebView;

import com.testbird.artisan.TestBirdAgent.anrwatchdog.ANRWatchDog;
import com.testbird.artisan.TestBirdAgent.objects.CustomMetaData;
import com.testbird.artisan.TestBirdAgent.report.EventHandler;
import com.testbird.artisan.TestBirdAgent.utils.ArtisanLogUtil;
import com.testbird.artisan.TestBirdAgent.utils.DeviceInfoCapture;
import com.testbird.artisan.TestBirdAgent.utils.HttpURLConnectionBuilder;
import com.testbird.artisan.TestBirdAgent.utils.IOUtils;
import com.testbird.artisan.TestBirdAgent.utils.Util;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.List;


public class CrashManager {

    private static final int EVENT_NOT_CONFIRM = 1;
    private static final int EVENT_CONFIRMED = 2;
    private static final int MAX_RETRY = 5;
    public static final String ARTISAN_SDK = "ArtisanSdkReports";
    private static final String NATIVE_LIB_NAME = "libartisan.so";
    private static boolean sEnableHttps = false;
    private static final String HTTP_HEADER = "http://";
    private static final String HTTPS_HEADER = "https://";

    /**
     * Current sdk version.
     */
    public static String SDK_VERSION = "1.0.4";

    /**
     * App mIdentifier from Testbird Crash-Analysis.
     */
    private String mIdentifier = null;

    /**
     * App Channel Id.
     */
    private String mChannelId = "";

    /**
     * Application User Id.
     */
    private String mUserId = "";

    /**
     * Stack traces are currently submitted
     */
    private static CrashManager sInstance;
    private Context mContext;
    private EventHandler mEventHanlder;
    private Handler mHandler;
    private HandlerThread mThread;
    private ANRWatchDog mWatchDog;

    private Runnable mRunable;

    private CrashManager(Context context) {
        this.mContext = context;
        this.mEventHanlder = new EventHandler();
        this.mThread = new HandlerThread("SendCrash-Thread");
        this.mThread.start();
        this.mWatchDog = new ANRWatchDog(600000);
        // this.mWatchDog.start();
        this.mHandler = new Handler(mThread.getLooper());
        this.mRunable = new Runnable() {
            @Override
            public void run() {
                execute(mContext, false);
            }
        };
    }

    synchronized public static CrashManager getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("must call register function before use it.");
        }
        return sInstance;
    }

    public static void enableHttps() {
        sEnableHttps = true;
    }

    public static void disableHttps() {
        sEnableHttps = false;
    }

    public void startSendEvent() {
        this.mHandler.post(mRunable);
    }

    public void cancelAnrWatchDog() {
        //mWatchDog.cancel();
    }

    public static void register(Context context, String appKey) {
        register(context, appKey, null);
    }

    public static void register(Context context, String appKey,
                                String channelId) {
        if (sInstance == null) {
            sInstance = new CrashManager(context);
        }
        sInstance.initialize(context, appKey, channelId);
    }

    /**
     * Initializes the crash manager, but does not handle crash log. Use this method only if you
     * want to split the process into two parts, i.e. when your app has multiple entry points. You
     * need to call the method 'execute' at some point after this method.
     *
     * @param context       The context to use. Usually your Activity object.
     * @param appIdentifier App ID of your app on testbird crash-analysis..
     * @param channeId      ChannelId for your app.
     */
    public void initialize(Context context, String appIdentifier, String channeId) {
        ArtisanLogUtil.debugLog("init with " + appIdentifier + " channel " + channeId + " start.");
        DeviceInfoCapture.init(context);
        if (searchNativeLib(new File("/data/data/" + context.getPackageName()), NATIVE_LIB_NAME)) {
            ArtisanLogUtil.debugLog("libartisan.so found.");
            System.loadLibrary("artisan");
            initJniObject();
        } else {
            ArtisanLogUtil.debugLog("libartisan.so not found.");
        }
        initialize(context, appIdentifier, channeId, false);
        ArtisanLogUtil.debugLog("init " + appIdentifier + " channel " + channeId + " complete.");
        ArtisanLogUtil.print("testbird agent init complete.");
    }

    private boolean searchNativeLib(File path, String name) {
        File[] files = path.listFiles();
        boolean isFound = false;
        for (File file : files) {
            if (file.isDirectory()) {
                isFound = searchNativeLib(file, name);
                if (isFound) {
                    break;
                }
            } else {
                if (file.getName().equals(name)) {
                    isFound = true;
                    break;
                }
            }
        }
        return isFound;
    }

    /**
     * Executes the crash manager. You need to call this method if you have used the method
     * 'initialize' before. If context is not an instance of Activity (or a subclass of it), crashes
     * will be sent automatically.
     *
     * @param context The context to use. Usually your Activity object.
     */
    public static void execute(Context context, boolean ignoreDefault) {
        Boolean ignoreDefaultHandler = ignoreDefault;
        WeakReference<Context> weakContext = new WeakReference<Context>(context);
        int foundOrSend = hasReport(weakContext);
        if (foundOrSend == EVENT_NOT_CONFIRM || foundOrSend == EVENT_CONFIRMED) {
            sendCrashes(weakContext, ignoreDefaultHandler);
        } else {
            registerHandler(weakContext, ignoreDefaultHandler);
        }
    }

    private static String pullConfig() {
        DeviceInfoCapture capture = DeviceInfoCapture.getInstance();
        PackageInfo packageInfo = capture.getPackageInfo();
        String
            param =
            String.format("?appKey=%s&verCode=%d&verName=%s",
                          CrashManager.getInstance().getIdentifier(), packageInfo.versionCode,
                          packageInfo.versionName);
        String result = "";
        HttpURLConnection
            connection =
            new HttpURLConnectionBuilder(
                getConfigURLString() + param)
                .setRequestMethod("GET")
                .build();
        try {
            int code = connection.getResponseCode();
            ArtisanLogUtil.debugLog("pullConfig ret code is " + code);
            if (code == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection
                                                                                 .getInputStream()));
                String lines = "";
                while ((lines = in.readLine()) != null) {
                    result += lines;
                }
                ArtisanLogUtil.debugLog(result);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

    private static boolean isReportOn() {
        String result = pullConfig();
        try {
            JSONObject json = new JSONObject(result);
            if (json == null) {
                return true;
            } else {
                Boolean reported = (Boolean) json.get("reported");
                ArtisanLogUtil.debugLog("reported " + reported);
                return reported;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return true;
    }

    /**
     * Checks if there are any saved stack traces in the files dir.
     *
     * @param weakContext The context to use. Usually your Activity object.
     * @return 0 if there are no stack traces, 1 if there are any new stack traces, 2 if there are
     * confirmed stack traces
     */
    public static int hasReport(WeakReference<Context> weakContext) {
        String[] filenames = searchForReport();
        List<String> confirmedFilenames = null;
        int result = 0;
        if ((filenames != null) && (filenames.length > 0)) {
            try {
                Context context = null;
                if (weakContext != null) {
                    context = weakContext.get();
                    if (context != null) {
                        SharedPreferences
                            preferences =
                            context.getSharedPreferences(ARTISAN_SDK, Context.MODE_PRIVATE);
                        confirmedFilenames =
                            Arrays.asList(
                                preferences.getString("ConfirmedFilenames", "").split("\\|"));
                    }
                }
            } catch (Exception e) {
                // Just in case, we catch all exceptions here
            }
            if (confirmedFilenames != null) {
                result = EVENT_CONFIRMED;
                for (String filename : filenames) {
                    if (!confirmedFilenames.contains(filename)) {
                        result = EVENT_NOT_CONFIRM;
                        break;
                    }
                }
            } else {
                result = EVENT_NOT_CONFIRM;
            }
        }
        return result;
    }

    /**
     * Submits all stack traces in the files dir to Artisan.
     *
     * @param weakContext The context to use. Usually your Activity object.
     */
    public static void submitStackTraces(WeakReference<Context> weakContext) {
        String[] list = searchForReport();
        Boolean successful = false;
        Boolean isReportOn = isReportOn();
        if ((list != null) && (list.length > 0)) {
            ArtisanLogUtil.debugLog("Found " + list.length + " report(s).");
            ArtisanLogUtil.debugLog("target server is " + getCollectURLString());
            for (int index = 0; index < list.length; index++) {
                successful = false;
                HttpURLConnection urlConnection = null;
                try {
                    // Read contents of report
                    String filename = Constants.FILES_PATH + "/" + list[index];
                    if (filename.endsWith(Constants.REPORT_SUFFIX) && !isReportOn) {
                        ArtisanLogUtil.debugLog("ReportOn = false. Report not send.");
                        deleteStackTrace(weakContext, filename);
                        deleteRetryCounter(weakContext, list[index], MAX_RETRY);
                        continue;
                    }
                    String report = contentsOfFile(weakContext, filename);
                    ArtisanLogUtil.debugLog("content " + report + " " + filename);
                    if (report.length() > 0) {
                        // Transmit report with POST request
                        ArtisanLogUtil.dumpReport(report);
                        urlConnection = new HttpURLConnectionBuilder(getCollectURLString())
                            .setRequestMethod("POST")
                            .writeFromJsonString(report)
                            .build();

                        int responseCode = urlConnection.getResponseCode();
                        ArtisanLogUtil.debugLog("Transmit ret code is " + responseCode);
                        successful =
                            (responseCode == HttpURLConnection.HTTP_ACCEPTED
                             || responseCode == HttpURLConnection.HTTP_CREATED
                             || responseCode == HttpURLConnection.HTTP_OK);

                    } else {
                        successful = true;
                        ArtisanLogUtil.debugLog("Content is null.");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (urlConnection != null) {
                        urlConnection.disconnect();
                    }
                    if (successful) {
                        ArtisanLogUtil.debugLog("Transmission succeeded : " + list[index]);
                        deleteStackTrace(weakContext, Constants.FILES_PATH + "/" + list[index]);
                        deleteRetryCounter(weakContext, list[index], MAX_RETRY);
                    } else {
                        ArtisanLogUtil.debugLog(
                            "Transmission failed, will retry on next register() call");
                        updateRetryCounter(weakContext, list[index], MAX_RETRY);
                    }
                }
            }
        }
    }

    /**
     * Private method to initialize the crash manager. This method has an additional parameter to
     * decide whether to register the exception handler at the end or not.
     */
    private void initialize(Context context, String appIdentifier,
                            String channelId, boolean ignoreDefaultHandler) {
        if (context != null) {
            this.mIdentifier = Util.sanitizeAppIdentifier(appIdentifier);
            this.mChannelId = channelId;
            Constants.loadFromContext(context);
            if (mIdentifier == null) {
                this.mIdentifier = Constants.APP_PACKAGE;
            }
            WeakReference<Context> weakContext = new WeakReference<Context>(context);
            registerHandler(weakContext, ignoreDefaultHandler);
            makeSession();
        }
    }

    /**
     * Starts thread to send crashes to Artisan, then registers the exception handler.
     */
    private static void sendCrashes(final WeakReference<Context> weakContext,
                                    final boolean ignoreDefaultHandler) {
        saveConfirmedStackTraces(weakContext);
        registerHandler(weakContext, ignoreDefaultHandler);

        Context ctx = weakContext.get();
        if (ctx != null && !Util.isConnectedToNetwork(ctx)) {
            // Not connected to network, not trying to submit stack traces
            return;
        }
        submitStackTraces(weakContext);
    }

    /**
     * Registers the exception handler.
     */
    private static void registerHandler(WeakReference<Context> weakContext,
                                        boolean ignoreDefaultHandler) {
        if ((Constants.APP_VERSION != null) && (Constants.APP_PACKAGE != null)) {
            // Get current handler
            UncaughtExceptionHandler currentHandler = Thread.getDefaultUncaughtExceptionHandler();
            if (currentHandler != null) {
                ArtisanLogUtil.debugLog(
                    "Current handler class = " + currentHandler.getClass().getName());
            }
            // Update listener if already registered, otherwise set new handler
            if (currentHandler instanceof ExceptionHandler) {
                // ignore
            } else {
                Thread.setDefaultUncaughtExceptionHandler(
                    new ExceptionHandler(currentHandler, ignoreDefaultHandler));
            }
        } else {
            ArtisanLogUtil
                .debugLog("Exception handler not set because version or package is null.");
        }
    }

    /**
     * Returns the complete URL for the Artisan API.
     */
    private static String getCollectURLString() {
        if (sEnableHttps) {
            ArtisanLogUtil
                .debugLog("current server address is " + HTTPS_HEADER + BuildConfig.SERVER_URL);
            return HTTPS_HEADER + BuildConfig.SERVER_URL + "crash";
        } else {
            ArtisanLogUtil
                .debugLog("current server address is " + HTTP_HEADER + BuildConfig.SERVER_URL);
            return HTTP_HEADER + BuildConfig.SERVER_URL + "crash";
        }
    }

    private static String getConfigURLString() {
        if (sEnableHttps) {
            ArtisanLogUtil.debugLog(
                "current config server url " + HTTPS_HEADER + BuildConfig.SERVER_URL);
            return HTTPS_HEADER + BuildConfig.SERVER_URL + "config";
        } else {
            ArtisanLogUtil.debugLog(
                "current config server url " + HTTP_HEADER + BuildConfig.SERVER_URL);
            return HTTP_HEADER + BuildConfig.SERVER_URL + "config";
        }
    }

    /**
     * Update the retry attempts count for this crash stacktrace.
     */
    private static void updateRetryCounter(WeakReference<Context> weakContext, String filename,
                                           int maxRetryAttempts) {
        if (maxRetryAttempts == -1) {
            return;
        }

        Context context = null;
        if (weakContext != null) {
            context = weakContext.get();
            if (context != null) {
                SharedPreferences
                    preferences =
                    context.getSharedPreferences(ARTISAN_SDK, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();

                int retryCounter = preferences.getInt("RETRY_COUNT: " + filename, 0);
                ArtisanLogUtil.debugLog("current retry count is " + retryCounter);
                if (retryCounter >= maxRetryAttempts) {
                    ArtisanLogUtil.debugLog("retry count beyond max " + maxRetryAttempts);
                    deleteStackTrace(weakContext, filename);
                    deleteRetryCounter(weakContext, filename, maxRetryAttempts);
                } else {
                    editor.putInt("RETRY_COUNT: " + filename, retryCounter + 1);
                    editor.apply();
                }
            }
        }
    }

    /**
     * Delete the retry counter if stacktrace is uploaded or retry limit is reached.
     */
    private static void deleteRetryCounter(WeakReference<Context> weakContext, String filename,
                                           int maxRetryAttempts) {
        Context context = null;
        if (weakContext != null) {
            context = weakContext.get();
            if (context != null) {
                SharedPreferences
                    preferences =
                    context.getSharedPreferences(ARTISAN_SDK, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.remove("RETRY_COUNT: " + filename);
                editor.apply();
            }
        }
    }

    /**
     * Deletes the give filename and all corresponding files (same name, different extension).
     */
    private static void deleteStackTrace(WeakReference<Context> weakContext, String filename) {
        Context context = null;
        ArtisanLogUtil.debugLog("delete file " + filename);
        if (weakContext != null) {
            context = weakContext.get();
            if (context != null) {
                File file = new File(filename);
                if (file.exists()) {
                    file.delete();
                }
            }
        }
    }

    /**
     * Returns the content of a file as a string.
     */
    private static String contentsOfFile(WeakReference<Context> weakContext, String filename) {
        Context context = null;
        if (weakContext != null) {
            context = weakContext.get();
            if (context != null) {
                StringBuilder contents = new StringBuilder();
                BufferedReader reader = null;
                try {
                    File file = new File(filename);
                    reader =
                        new BufferedReader(new FileReader(file));
                    String line = null;
                    while ((line = reader.readLine()) != null) {
                        contents.append(line);
                        contents.append(System.getProperty("line.separator"));
                    }
                } catch (FileNotFoundException e) {
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    IOUtils.closeQuietly(reader);
                }

                return contents.toString();
            }
        }

        return null;
    }

    /**
     * Saves the list of the stack traces' file names in shared preferences.
     */
    private static void saveConfirmedStackTraces(WeakReference<Context> weakContext) {
        Context context = null;
        if (weakContext != null) {
            context = weakContext.get();
            if (context != null) {
                try {
                    String[] filenames = searchForReport();
                    SharedPreferences
                        preferences =
                        context.getSharedPreferences(ARTISAN_SDK, Context.MODE_PRIVATE);
                    Editor editor = preferences.edit();
                    editor.putString("ConfirmedFilenames", joinArray(filenames, "|"));
                    editor.apply();
                } catch (Exception e) {
                    // Just in case, we catch all exceptions here
                }
            }
        }
    }

    /**
     * Returns a string created by each element of the array, separated by delimiter.
     */
    private static String joinArray(String[] array, String delimiter) {
        StringBuffer buffer = new StringBuffer();
        for (int index = 0; index < array.length; index++) {
            buffer.append(array[index]);
            if (index < array.length - 1) {
                buffer.append(delimiter);
            }
        }
        return buffer.toString();
    }

    /**
     * Searches .report files and returns then as array.
     */
    private static String[] searchForReport() {
        if (Constants.FILES_PATH != null) {
            ArtisanLogUtil.debugLog("Looking for exceptions in: " + Constants.FILES_PATH);

            // Try to create the files folder if it doesn't exist
            File dir = new File(Constants.FILES_PATH + "/");
            if (!dir.exists()) {
                return new String[0];
            }

            // Filter for ".report" files
            FilenameFilter reportFilter = new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(Constants.REPORT_SUFFIX);
                }
            };
            // Filter for ".session" files
            FilenameFilter sessionFilter = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String filename) {
                    return filename.endsWith(Constants.SESSION_SUFFIX);
                }
            };
            String[] reportList = dir.list(reportFilter);
            String[] sessionList = dir.list(sessionFilter);
            int orgLength = sessionList.length;
            String[] totalList = Arrays.copyOf(sessionList, reportList.length + sessionList.length);
            System.arraycopy(reportList, 0, totalList, orgLength, reportList.length);
            return totalList;
        } else {
            ArtisanLogUtil.debugLog("Can't search for report as file path is null.");
            return null;
        }
    }

    public static void setDebug(boolean isDebug) {
        ArtisanLogUtil.setDebug(isDebug);
    }

    public static void addVerboseLog(String line) {
        ArtisanLogUtil.debugLog("add log -v " + line);
        CustomMetaData.getInstance().addCustomLog(CustomMetaData.VERBOSE, line);
    }

    public static void addDebugLog(String line) {
        ArtisanLogUtil.debugLog("add log -d " + line);
        CustomMetaData.getInstance().addCustomLog(CustomMetaData.DEBUG, line);
    }

    public static void addWarnLog(String line) {
        ArtisanLogUtil.debugLog("add log -w " + line);
        CustomMetaData.getInstance().addCustomLog(CustomMetaData.WARN, line);
    }

    public static void addErrorLog(String line) {
        ArtisanLogUtil.debugLog("add log -e " + line);
        CustomMetaData.getInstance().addCustomLog(CustomMetaData.ERROR, line);
    }

    public static void addInfoLog(String line) {
        ArtisanLogUtil.debugLog("add log -i " + line);
        CustomMetaData.getInstance().addCustomLog(CustomMetaData.INFO, line);
    }

    public static void addCustomKeyPair(String key, String value) {
        ArtisanLogUtil.debugLog("add key " + key + " value " + value);
        CustomMetaData.getInstance().addCustomKeyValue(key, value);
    }

    public static void removeCustomKeyPair(String key) {
        ArtisanLogUtil.debugLog("remove key " + key);
        CustomMetaData.getInstance().removeCustomKeyValue(key);
    }

    public static void clearCustomKeyPairs() {
        CustomMetaData.getInstance().clearCustomKeyValue();
    }

    public EventHandler getEventHanlder() {
        return mEventHanlder;
    }

    public String getIdentifier() {
        return mIdentifier;
    }

    public String getChannelId() {
        return mChannelId;
    }

    public static void setUserId(String userId) {
        ArtisanLogUtil.debugLog("set userid " + userId);
        sInstance.mUserId = userId;
    }

    public String getUserId() {
        return mUserId;
    }

    public void makeSession() {
        if (mEventHanlder.isSessionHolder(mContext)) {
            mEventHanlder.saveSessionEvent();
        } else {
            ArtisanLogUtil.print("current process not session holder.");
        }
    }

    public void insertTrack(String msg) {
        DeviceInfoCapture.getInstance().insertUrlTrack(msg);
    }

    public static void testJavaCrash() {
        String str = null;
        ArtisanLogUtil.print("test java crash");
        str.length();
    }

    public static void testAnrException() {
        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            // ignore
        }

    }

    public static void testNativeCrash() {
        makeNativeCrash();
    }

    /**
     * Submit exception that user want to analysis to server.
     *
     * @param throwable exception throwable.
     */
    public static void submitException(Throwable throwable) {
        if (throwable == null) {
            ArtisanLogUtil.error("throwable is null");
        } else {
            sInstance.mEventHanlder.saveException(throwable, "error", "caught_exception");
        }
    }

    /**
     * Submit native exception.
     *
     * @param trace native stack trace.
     */
    public void submitNativeException(String trace, String signal) {
        ArtisanLogUtil.debugLog("native trace is \n" + trace);
        mEventHanlder.saveException(trace, "error", "native_crash", signal, null, null);
    }

    /**
     * Submit engine exception.
     */

    public void submitEngineException(String name, String reason, String trace, String type) {
        ArtisanLogUtil.debugLog("submit engine crash trace : " + trace);
        mEventHanlder.saveException(trace, "error", type, "", name, reason);
    }

    public static void enableJavaScriptMonitor(final WebView webView, boolean auto) {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new JavaScriptInterface(), "ArtisanMonitor");
        ArtisanLogUtil.debugLog("Is auto inject javascript for artisan " + auto);
        if (!JavaScriptInterface.isLast(webView.getUrl())) {
            DeviceInfoCapture.getInstance().insertUrlTrack(webView.getUrl());
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            ArtisanLogUtil.error("js monitor not support sdk_int < 19.");
        } else {
            if (auto) {
                webView.evaluateJavascript(
                    "if (!!window.ArtisanInited) {\n"
                    + "    console.log(\"artisan js has inited.\");\n"
                    + "} else {\n"
                    + "    window.onerror = function(message, source, lineno, colno, error) {\n"
                    + "        var reg = /(\\\\w+):\\\\s*(.+)/i;\n"
                    + "        var name, reason;\n"
                    + "        if (reg.test(message)) {\n"
                    + "            name = RegExp.$1;\n"
                    + "            reason = RegExp.$2;\n"
                    + "        } else {\n"
                    + "            name = message;\n"
                    + "            reason = 'unknown';\n"
                    + "        }\n"
                    + "        ArtisanMonitor.ReportException(name, reason, source, lineno, false);\n"
                    + "    }\n"
                    + "    var Artisan = {\n"
                    + "        setUserId: function(userId) {\n"
                    + "            ArtisanMonitor.setUserId(userId);\n"
                    + "        },\n"
                    + "        addCustomLog: function(log) {\n"
                    + "            ArtisanMonitor.addCustomLog(log);\n"
                    + "        },\n"
                    + "        setCustomValue: function(key, value) {\n"
                    + "            ArtisanMonitor.setCustomValue(key, value);\n"
                    + "        },\n"
                    + "        removeCustomKey: function(key) {\n"
                    + "            ArtisanMonitor.removeCustomKey(key);\n"
                    + "        },\n"
                    + "        clearCustomKeys: function() {\n"
                    + "            ArtisanMonitor.clearCustomKeys();\n"
                    + "        },\n"
                    + "        reportCaughtException: function(error) {\n"
                    + "            var name = error.name;\n"
                    + "            var reason = error.message;\n"
                    + "            var source = error.fileName;\n"
                    + "            var lineno = error.lineNumber;\n"
                    + "            var caught = true;\n"
                    + "            if ((!source || !lineno) && error.stack) {\n"
                    + "                var reg = /file:\\/\\/([^:]+)\\s*:\\s*([\\d]+)/;\n"
                    + "                if (reg.test(error.stack)) {\n"
                    + "                    source = 'file://' + RegExp.$1;\n"
                    + "                    lineno = RegExp.$2;\n"
                    + "                }\n"
                    + "            }\n"
                    + "            if (!source) {\n"
                    + "                source = 'unknown';\n"
                    + "            }\n"
                    + "            if (!lineno) {\n"
                    + "                lineno = '0';\n"
                    + "            }\n"
                    + "            ArtisanMonitor.ReportException(name, reason, source, lineno, caught);\n"
                    + "        }\n"
                    + "    }\n"
                    + "    window.ArtisanInited = true;\n"
                    + "};\n", null);
            } else {
                webView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < 3; i++) {
                            int progress = webView.getProgress();
                            if (progress == 100) {
                                webView.loadUrl("javascript: window.androidReady()", null);
                            } else {
                                try {
                                    Thread.sleep(2000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }, 500);

            }
        }
    }

    private static native String makeNativeCrash();

    private static native void initJniObject();
}
