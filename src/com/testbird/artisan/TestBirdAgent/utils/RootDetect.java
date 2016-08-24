/*
 * Copyright (C) 2016 TestBird  - All Rights Reserved
 * You may use, distribute and modify this code under
 * the terms of the mit license.
 */
package com.testbird.artisan.TestBirdAgent.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Created by chenxuesong on 16/1/11.
 */

public class RootDetect {

    private static String LOG_TAG = RootDetect.class.getName();

    public static boolean isDeviceRooted() {
        return checkBinary() ? true : checkRootApkFiles();
    }

    private static boolean checkRootApkFiles() {
        try {
            File superuser = new File("/system/app/Superuser.apk");
            File supersu = new File("/system/app/SuperSU.apk");
            return (superuser.exists() || supersu.exists()) ? true : false;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static boolean checkBinary() {
        try {
            File bin = new File("/system/bin/su");
            File xbin = new File("/system/xbin/su");
            return (bin.exists() || xbin.exists()) ? true : false;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static boolean checkRootBinary() {
        if (new ExecShell().executeCommand(ExecShell.SHELL_CMD.check_su_binary) != null) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean checkRootWithSuCmd() {
        try {
            Runtime.getRuntime().exec("su");
            return true;
        } catch (IOException localIOException) {
            return false;
        }
    }

    public static class ExecShell {

        private static final String LOG_TAG = ExecShell.class.getName();

        public static enum SHELL_CMD {
            check_su_binary(new String[]{"/system/xbin/which", "su"}),;

            String[] command;

            SHELL_CMD(String[] command) {
                this.command = command;
            }
        }

        public ArrayList<String> executeCommand(SHELL_CMD shellCmd) {
            String line;
            ArrayList<String> fullResponse = new ArrayList<>();
            Process localProcess;
            try {
                localProcess = Runtime.getRuntime().exec(shellCmd.command);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            BufferedReader
                in =
                new BufferedReader(new InputStreamReader(localProcess.getInputStream()));
            try {
                while ((line = in.readLine()) != null) {
                    ArtisanLogUtil.debugLog("--> Line received: " + line);
                    fullResponse.add(line);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            ArtisanLogUtil.debugLog("--> Full response was: " + fullResponse);
            return fullResponse;
        }
    }
}



