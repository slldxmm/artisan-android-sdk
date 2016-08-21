package com.testbird.artisan.TestBirdAgent.objects;

import com.testbird.artisan.TestBirdAgent.utils.ArtisanLogUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by chenxuesong on 15/11/10.
 */
public class CustomMetaData {

    private static CustomMetaData sInstance;
    private Map<String, String> mCustomKVMap;
    private StringBuffer mCustomLogBuffer;
    private static final int LOG_BUFFER_SIZE = 32 * 1024;
    private static final int SINGLE_LINE_LOG_SIZE = 1024;
    private static final int KV_PAIRS_SIZE = 32;
    private static final int KV_VALUE_SIZE = 1024;
    public static final String DEBUG = " debug ";
    public static final String ERROR = " error ";
    public static final String INFO = " info ";
    public static final String WARN = " warn ";
    public static final String VERBOSE = " verbose ";

    private CustomMetaData() {
        mCustomKVMap = new HashMap<>();
        mCustomLogBuffer = new StringBuffer();
    }

    synchronized public static CustomMetaData getInstance() {
        if (sInstance == null) {
            sInstance = new CustomMetaData();
        }
        return sInstance;
    }

    public void addCustomLog(String level, String line) {
        ArtisanLogUtil.debugLog("add custom log " + line + " level " + level);
        Date date = new Date();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss -");
        String newLine = fmt.format(date) + level + line + "\n";
        if (newLine.length() > SINGLE_LINE_LOG_SIZE) {
            ArtisanLogUtil.error("single line > " + SINGLE_LINE_LOG_SIZE);
        } else {
            if (newLine.length() + mCustomLogBuffer.toString().length() <= LOG_BUFFER_SIZE) {
                mCustomLogBuffer.append(newLine);
            } else {
                int length = newLine.length();
                int index = -1;
                while (index < length) {
                    index = mCustomLogBuffer.indexOf("\n", index + 1);
                }
                mCustomLogBuffer.delete(0, index + 1);
                mCustomLogBuffer.append(newLine);
            }
        }
        ArtisanLogUtil.print("current size is " + mCustomLogBuffer.toString().length());
    }

    public void addCustomKeyValue(String key, String value) {
        ArtisanLogUtil
            .debugLog("add custom key size " + key.length() + " value size " + value.length());
        ArtisanLogUtil.debugLog("add custom key=" + key + " value=" + value);
        if ((key.length() + value.length()) > KV_VALUE_SIZE
            || mCustomKVMap.size() >= KV_PAIRS_SIZE) {
            ArtisanLogUtil
                .error("you have add 32 k-v pairs or key + value's length > " + KV_VALUE_SIZE);
        } else {
            mCustomKVMap.put(key, value);
        }
    }

    public void removeCustomKeyValue(String key) {
        ArtisanLogUtil.debugLog("remove custom key=" + key);
        mCustomKVMap.remove(key);
    }

    public void clearCustomKeyValue() {
        ArtisanLogUtil.debugLog("clear all key pairs");
        mCustomKVMap.clear();
    }

    public String getCustomLogs() {
        return mCustomLogBuffer.toString();
    }

    public Map getCustomKeyValueMap() {
        return mCustomKVMap;
    }
}
