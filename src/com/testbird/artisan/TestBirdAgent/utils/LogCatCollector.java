package com.testbird.artisan.TestBirdAgent.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by chenxuesong on 15/11/9.
 */
public class LogCatCollector {

    private static final int DEFAULT_LINES = 200;

    /**
     * Collect android adb logcat output.
     *
     * @return Android log with custom size.
     */
    public static String collectLogCat() {
        StringBuilder builder = new StringBuilder();
        Process process = null;
        BufferedReader bufferedReader = null;
        try {
            String[] command = new String[]{"logcat", "-v", "threadtime"};
            process = Runtime.getRuntime().exec(command);
            bufferedReader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            String line;
            int count = 0;
            // sleep 0.3s for bufferedreader ready.
            Thread.sleep(300);
            while (bufferedReader.ready() && count < DEFAULT_LINES) {
                line = bufferedReader.readLine();
                if (line == null) {
                    break;
                } else {
                    builder.append(line + "\n");
                    count++;
                }
            }
        } catch (IOException | InterruptedException ex) {
            // ignore
        } finally {
            if (process != null) {
                try {
                    if (bufferedReader != null) {
                        bufferedReader.close();
                    }
                    process.getInputStream().close();
                    process.destroy();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        return builder.toString();
    }
}
