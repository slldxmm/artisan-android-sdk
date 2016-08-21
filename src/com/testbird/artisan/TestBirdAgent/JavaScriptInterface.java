package com.testbird.artisan.TestBirdAgent;

import android.text.TextUtils;
import android.webkit.JavascriptInterface;

import com.testbird.artisan.TestBirdAgent.utils.ArtisanLogUtil;
import com.testbird.artisan.TestBirdAgent.utils.DeviceInfoCapture;

/**
 * Created by chenxuesong on 16/5/3.
 */
public class JavaScriptInterface {

    private static final int INFO = 1;
    private static final int MIN_LENGTH = 1;
    private static final String JS_EXCEPTION_TYPE = "html5_js_exception";
    private static final String JS_CAUGHT_EXCEPTION_TYPE = "html5_js_caught_exception";

    private static String sLastUrl = "";

    @JavascriptInterface
    public void test() {
        ArtisanLogUtil.print("artisan has one test.");
    }

    @JavascriptInterface
    public void ReportException(String name, String reason, String source, String lineno, boolean caught) {
        ArtisanLogUtil.print(name + " " + reason + " " + source + " " + lineno);
        String stacktrace = name + "\n" + reason + "\n" + source + ":" + lineno;
        if (caught) {
            CrashManager.getInstance()
                .submitEngineException(name, reason, stacktrace, JS_CAUGHT_EXCEPTION_TYPE);
        }else {
            CrashManager.getInstance()
                .submitEngineException(name, reason, stacktrace, JS_EXCEPTION_TYPE);
        }
    }

    @JavascriptInterface
    public void ReportException(String name, String reason, String source, String lineno) {
        ArtisanLogUtil.print(name + " " + reason + " " + source + " " + lineno);
        String stacktrace = name + "\n" + reason + "\n" + source + ":" + lineno;
        CrashManager.getInstance()
                .submitEngineException(name, reason, stacktrace, JS_EXCEPTION_TYPE);
    }

    @JavascriptInterface
    public void setUserId(String user) {
        CrashManager.getInstance().setUserId(user);
    }

    @JavascriptInterface
    public void addCustomLog(String line) {
        ArtisanPlugin.addCustomLog(line, INFO);
    }

    @JavascriptInterface
    public void setCustomValue(String key, String value) {
        ArtisanPlugin.addCustomKey(key, value);
    }

    @JavascriptInterface
    public void removeCustomKey(String key) {
        ArtisanPlugin.deleteCustomKey(key);
    }

    @JavascriptInterface
    public void clearCustomKeys() {
        ArtisanPlugin.clearCustomKeys();
    }

    @JavascriptInterface
    public static void insertCurrentUrl(String url) {
        DeviceInfoCapture.getInstance().insertUrlTrack(url);
    }

    public static boolean isLast(String url) {
        if (url == null || TextUtils.equals(sLastUrl, url)) {
            return true;
        } else {
            ArtisanLogUtil.debugLog("last is " + sLastUrl + " current is " + url);
            sLastUrl = url;
            return false;
        }
    }
}
