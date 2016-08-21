package com.testbird.artisan.TestBirdAgent.anrwatchdog;

/**
 * Created by chenxuesong on 15/12/10.
 */


import android.os.Looper;

import java.util.HashMap;
import java.util.Map;


public class ANRError extends Error {

    public static final String ANR_MESSAGE = "Application Not Responding";

    private static class ANRThrowable extends Throwable {
        private ANRThrowable(String name) {
            super(name);
        }
    }


    private static final long sSerialVersionUID = 1L;

    private final Map<Thread, StackTraceElement[]> mStackTraces;

    private ANRError(ANRThrowable st, Map<Thread, StackTraceElement[]> stackTraces) {
        super(ANR_MESSAGE, st);
        mStackTraces = stackTraces;
    }

    /**
     * @return all the reported threads and stack traces.
     */
    public Map<Thread, StackTraceElement[]> getStackTraces() {
        return mStackTraces;
    }

    @Override
    public Throwable fillInStackTrace() {
        setStackTrace(new StackTraceElement[]{});
        return this;
    }

    static ANRError NewMainOnly() {
        final Thread mainThread = Looper.getMainLooper().getThread();
        final StackTraceElement[] mainStackTrace = mainThread.getStackTrace();

        final HashMap<Thread, StackTraceElement[]>
            stackTraces =
            new HashMap<Thread, StackTraceElement[]>(1);
        stackTraces.put(mainThread, mainStackTrace);
        ANRThrowable tst = new ANRThrowable(mainThread.getName());
        tst.setStackTrace(mainStackTrace);
        return new ANRError(tst, stackTraces);
    }
}