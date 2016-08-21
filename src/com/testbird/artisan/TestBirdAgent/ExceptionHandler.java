package com.testbird.artisan.TestBirdAgent;

import com.testbird.artisan.TestBirdAgent.anrwatchdog.ANRError;

import java.lang.Thread.UncaughtExceptionHandler;


public class ExceptionHandler implements UncaughtExceptionHandler {

    private static final String EXCEPTION_ANR_TYPE = "anr";
    private static final String EXCEPTION_JAVA_CRASH_TYPE = "java_crash";
    private static final String EVENT_ERROR_TYPE = "error";

    private boolean ignoreDefaultHandler = false;
    private UncaughtExceptionHandler defaultExceptionHandler;

    public ExceptionHandler(UncaughtExceptionHandler defaultExceptionHandler,
                            boolean ignoreDefaultHandler) {
        this.defaultExceptionHandler = defaultExceptionHandler;
        this.ignoreDefaultHandler = ignoreDefaultHandler;
    }

    public static void saveException(Throwable exception) {
        String message = exception.getMessage();
        if (message != null && message.equals(ANRError.ANR_MESSAGE)) {
            CrashManager.getInstance().getEventHanlder()
                .saveException(exception, EVENT_ERROR_TYPE, EXCEPTION_ANR_TYPE);
        } else {
            CrashManager.getInstance().getEventHanlder()
                .saveException(exception, EVENT_ERROR_TYPE, EXCEPTION_JAVA_CRASH_TYPE);
        }
    }

    public void uncaughtException(Thread thread, Throwable exception) {
        CrashManager.getInstance().cancelAnrWatchDog();
        if (Constants.FILES_PATH == null) {
            // If the files path is null, the exception can't be stored
            // Always call the default handler instead
            defaultExceptionHandler.uncaughtException(thread, exception);
        } else {
            saveException(exception);
            if (!ignoreDefaultHandler) {
                defaultExceptionHandler.uncaughtException(thread, exception);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        }
    }
}
