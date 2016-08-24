/*
 * Copyright (C) 2016 TestBird  - All Rights Reserved
 * You may use, distribute and modify this code under
 * the terms of the mit license.
 */
package com.testbird.artisan.TestBirdAgent.utils;

import android.util.Log;

import com.testbird.artisan.TestBirdAgent.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Created by chenxuesong on 15/12/9.
 */
public class ArtisanLogUtil {

    private static boolean sIsDebug = true;
    private static final String EVENT_INFO = "event_info";
    private static final String EVENT_CODE = "event_code";
    private static final String EVENT_CODE_SESSION = "session";
    private static final String SYS_LOG = "sys_log";
    private static final String SIMPLE_LOG = "this is sys log";

    public static boolean isDebug() {
        return sIsDebug;
    }

    public static void setDebug(boolean debug) {
        sIsDebug = debug;
    }

    public static void debugLog(String msg) {
        if (isDebug()) {
            Log.d(Constants.TAG, msg);
        }
    }

    public static void print(String msg) {
        Log.i(Constants.TAG, msg);
    }

    public static void error(String msg) {
        if (isDebug()) {
            Log.e(Constants.TAG, msg);
        }
    }

    public static void error(String msg, Throwable throwable) {
        if (isDebug()) {
            Log.e(Constants.TAG, msg, throwable);
        }
    }

    public static void dumpReport(String msg) {
        try {
            JSONObject jsonObject = new JSONObject(msg);
            JSONObject detail = (JSONObject) jsonObject.get(EVENT_INFO);
            if (detail.get(EVENT_CODE).equals(EVENT_CODE_SESSION)) {
                debugLog(msg);
                return;
            } else {
                detail.put(SYS_LOG, SIMPLE_LOG);
                jsonObject.put(EVENT_INFO, detail);
                String tmp = jsonObject.toString();
                Utils.splitAndLog(Constants.TAG, tmp);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static class Utils {

        /**
         * Divides a string into chunks of a given character size.
         *
         * @param text                  String text to be sliced
         * @param sliceSize             int Number of characters
         * @return ArrayList<String>   Chunks of strings
         */
        private static final int MAX_SIZE = 1024;

        public static ArrayList<String> splitString(String text, int sliceSize) {
            ArrayList<String> textList = new ArrayList<String>();
            String aux;
            int left = -1, right = 0;
            int charsLeft = text.length();
            while (charsLeft != 0) {
                left = right;
                if (charsLeft >= sliceSize) {
                    right += sliceSize;
                    charsLeft -= sliceSize;
                    aux = text.substring(left, right);
                } else {
                    right = text.length();
                    aux = text.substring(left, right);
                    charsLeft = 0;
                }
                textList.add(aux);
            }
            return textList;
        }

        /**
         * Divides a string into chunks.
         *
         * @param text String text to be sliced
         * @return ArrayList<String>
         */
        public static ArrayList<String> splitString(String text) {
            return splitString(text, MAX_SIZE);
        }

        /**
         * Divides the string into chunks for displaying them into the Eclipse's LogCat.
         *
         * @param text The text to be split and shown in LogCat
         * @param tag  The tag in which it will be shown.
         */
        public static void splitAndLog(String tag, String text) {
            ArrayList<String> messageList = Utils.splitString(text);
            for (String message : messageList) {
                if (isDebug()) {
                    Log.d(tag, message);
                }
            }
        }
    }
}
