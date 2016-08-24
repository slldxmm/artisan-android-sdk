/*
 * Copyright (C) 2016 TestBird  - All Rights Reserved
 * You may use, distribute and modify this code under
 * the terms of the mit license.
 */
package com.testbird.artisan.TestBirdAgent.anrwatchdog;

/**
 * Created by chenxuesong on 15/12/10.
 */

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.testbird.artisan.TestBirdAgent.utils.ArtisanLogUtil;

/**
 * A watchdog timer thread that detects when the UI thread has frozen.
 */

public class ANRWatchDog extends Thread {

    public interface ANRListener {

        public void onAppNotResponding(ANRError error);
    }

    public interface InterruptionListener {

        public void onInterrupted(InterruptedException exception);
    }

    private static final int DEFAULT_ANR_TIMEOUT = 5000;

    private static final ANRListener DEFAULT_ANR_LISTENER = new ANRListener() {
        @Override
        public void onAppNotResponding(ANRError error) {
            ArtisanLogUtil.debugLog("Anr detected! ");
            throw error;
        }
    };

    private static final InterruptionListener
        DEFAULT_INTERRUPTION_LISTENER =
        new InterruptionListener() {
            @Override
            public void onInterrupted(InterruptedException exception) {
                Log.w("ANRWatchdog", "Interrupted: " + exception.getMessage());
            }
        };

    private ANRListener mAnrListener = DEFAULT_ANR_LISTENER;
    private InterruptionListener mInterruptionListener = DEFAULT_INTERRUPTION_LISTENER;

    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final int mTimeoutInterval;

    private boolean mLogThreadsWithoutStackTrace = false;
    private boolean mIsCanceled;

    private volatile int mTick = 0;

    private final Runnable mTicker = new Runnable() {
        @Override
        public void run() {
            mTick = (mTick + 1) % 10;
        }
    };

    /**
     * Constructs a watchdog that checks the ui thread every {@value #DEFAULT_ANR_TIMEOUT}
     * milliseconds
     */
    public ANRWatchDog() {
        this(DEFAULT_ANR_TIMEOUT);
    }

    /**
     * Constructs a watchdog that checks the ui thread every given interval
     *
     * @param timeoutInterval The interval, in milliseconds, between to checks of the UI thread. It
     *                        is therefore the maximum time the UI may freeze before being reported
     *                        as ANR.
     */
    public ANRWatchDog(int timeoutInterval) {
        super();
        this.mIsCanceled = false;
        mTimeoutInterval = timeoutInterval;
    }

    /**
     * Sets an interface for when an ANR is detected. If not set, the default behavior is to throw
     * an error and crash the application.
     *
     * @param listener The new listener or null
     * @return itself for chaining.
     */
    public ANRWatchDog setANRListener(ANRListener listener) {
        if (listener == null) {
            mAnrListener = DEFAULT_ANR_LISTENER;
        } else {
            mAnrListener = listener;
        }
        return this;
    }

    /**
     * Sets an interface for when the watchdog thread is interrupted. If not set, the default
     * behavior is to just log the interruption message.
     *
     * @param listener The new listener or null.
     * @return itself for chaining.
     */
    public ANRWatchDog setInterruptionListener(InterruptionListener listener) {
        if (listener == null) {
            mInterruptionListener = DEFAULT_INTERRUPTION_LISTENER;
        } else {
            mInterruptionListener = listener;
        }
        return this;
    }

    public void setLogThreadsWithoutStackTrace(boolean logThreadsWithoutStackTrace) {
        mLogThreadsWithoutStackTrace = logThreadsWithoutStackTrace;
    }

    @Override
    public void run() {
        setName("|ANR-WatchDog|");

        int lastTick;
        while (!isInterrupted()) {
            lastTick = mTick;
            mUiHandler.post(mTicker);
            try {
                Thread.sleep(mTimeoutInterval);
            } catch (InterruptedException e) {
                mInterruptionListener.onInterrupted(e);
                return;
            }

            // If the main thread has not handled mTicker, it is blocked. ANR.
            if (mTick == lastTick) {
                if (isCancel()) {
                    return;
                }else {
                    ANRError error;
                    error = ANRError.NewMainOnly();
                    mAnrListener.onAppNotResponding(error);
                    return;
                }
            }
        }
    }

    synchronized public void cancel() {
        mIsCanceled = true;
    }

    synchronized private boolean isCancel() {
        return mIsCanceled;
    }
}
