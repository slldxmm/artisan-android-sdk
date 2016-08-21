package com.testbird.artisan.TestBirdAgent;

import android.content.Context;
import android.webkit.JavascriptInterface;

import com.testbird.artisan.TestBirdAgent.utils.ArtisanLogUtil;

/**
 * Created by chenxuesong on 15/11/25.
 */
public class ArtisanPlugin {

    private static final int INFO = 1;
    private static final int VERBOSE = 2;
    private static final int WARN = 3;
    private static final int DEBUG = 4;
    private static final int ERROR = 5;

    private static Context sContext = null;

    public static void setContext(Context context) {
        ArtisanLogUtil.debugLog("set context with " + context);
        sContext = context;
    }

    public static void registerSdk(String appKey, String channel) {
        CrashManager.register(sContext, appKey, channel);
    }

    public static void addCustomLog(String line, int type) {
        CrashManager manager = CrashManager.getInstance();
        switch (type) {
            case INFO:
                manager.addInfoLog(line);
                break;
            case VERBOSE:
                manager.addVerboseLog(line);
                break;
            case WARN:
                manager.addWarnLog(line);
                break;
            case DEBUG:
                manager.addDebugLog(line);
                break;
            case ERROR:
                manager.addErrorLog(line);
                break;
            default:
                break;
        }
    }

    public static void addCustomKey(String key, String value) {
        CrashManager.addCustomKeyPair(key, value);
    }

    public static void clearCustomLog() {

    }

    public static void deleteCustomKey(String key) {
        CrashManager.removeCustomKeyPair(key);
    }

    public static void clearCustomKeys() {
        CrashManager.clearCustomKeyPairs();
    }

    public static void setUserId(String userId) {
        CrashManager.getInstance().setUserId(userId);
    }

    public static void submitCrash(String name, String reason, String exp, String type) {
        CrashManager.getInstance().submitEngineException(name, reason, exp, type);
    }
}
