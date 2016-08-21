package com.testbird.artisan.TestBirdAgent.report;

import android.content.Context;

import com.testbird.artisan.TestBirdAgent.Constants;
import com.testbird.artisan.TestBirdAgent.CrashManager;
import com.testbird.artisan.TestBirdAgent.objects.CustomMetaData;
import com.testbird.artisan.TestBirdAgent.utils.ArtisanLogUtil;
import com.testbird.artisan.TestBirdAgent.utils.DeviceInfoCapture;
import com.testbird.artisan.TestBirdAgent.utils.IOUtils;
import com.testbird.artisan.TestBirdAgent.utils.LogCatCollector;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Created by chenxuesong on 15/11/10.
 */
public class EventHandler {

    private static final String PATH = "/session-holder/";

    public void saveException(Throwable exception, String type, String code) {
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);
        exception.printStackTrace(printWriter);
        saveException(result.toString(), type, code, null,
                      exception.getClass().getName(), exception.getLocalizedMessage());
        IOUtils.closeQuietly(result);
        IOUtils.closeQuietly(printWriter);
    }

    public void saveException(String exception, String type, String code, String signal,
                              String name, String message) {
        BufferedWriter write = null;
        CrashManager crashManager = CrashManager.getInstance();
        try {
            // Create filename from a random uuid
            String filename = UUID.randomUUID().toString();
            String
                path =
                Constants.FILES_PATH + "/" + filename + Constants.REPORT_SUFFIX;
            ArtisanLogUtil.debugLog(
                "Current app_key " + crashManager.getIdentifier() + " channel is " + crashManager
                    .getChannelId());
            ArtisanLogUtil.print("Writing unhandled exception to: " + path);

            // Write the report to disk
            write = new BufferedWriter(new FileWriter(path));
            Map<String, Object> map = new HashMap<String, Object>();
            Map<Thread, StackTraceElement[]> threadMap = Thread.getAllStackTraces();
            List<Map> threadInfoList = new ArrayList<>();
            for (Map.Entry<Thread, StackTraceElement[]> entry : threadMap.entrySet()) {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < entry.getValue().length; i++) {
                    builder.append(entry.getValue()[i].toString() + "\n");
                }
                Thread thread = entry.getKey();
                ThreadInfo threadInfo = new ThreadInfo();
                threadInfo.stack = builder.toString();
                threadInfo.id = String.valueOf(thread.getId());
                threadInfo.crashed = thread.getId() == Thread.currentThread().getId();
                threadInfo.group = thread.getThreadGroup().getName();
                threadInfo.level = String.valueOf(thread.getPriority());
                threadInfo.name = thread.getName();
                threadInfo.state = thread.getState().toString();
                threadInfoList.add(threadInfo.toMap());
            }
            Map<String, Object> crashReportMap = new HashMap<>();
            crashReportMap.put("process", DeviceInfoCapture.getInstance()
                .getProcessNameFromId(android.os.Process.myPid()));
            crashReportMap.put("crash_stack", exception);
            JSONArray threadInfoArray = new JSONArray();
            for (Map item : threadInfoList) {
                threadInfoArray.put(new JSONObject(item));
            }
            crashReportMap.put("all_threads", threadInfoArray);
            crashReportMap.put("crash_at", System.currentTimeMillis());
            crashReportMap.put("signal", signal);
            crashReportMap.put("exception_name", name != null ? name : "null");
            crashReportMap.put("exception_message", message != null ? message : "null");
            map.put("event_code", code);
            map.put("event_detail", new JSONObject(crashReportMap));
            map.put("sys_log", LogCatCollector.collectLogCat());
            List<Map> userTrace = DeviceInfoCapture.getInstance().getActivityTracks();
            JSONArray userTraceArray = new JSONArray();
            for (Map item : userTrace) {
                userTraceArray.put(new JSONObject(item));
            }
            map.put("user_trace", userTraceArray);
            map.put("custom_log", CustomMetaData.getInstance().getCustomLogs());
            map.put("custom_keys",
                    new JSONObject(CustomMetaData.getInstance().getCustomKeyValueMap()));
            ReportBuilder builder = new ReportBuilder();
            builder.setBasicInfo().setDeviceStatusInfo().setEventInfo(map)
                .setRuntime(code)
                .setUserId(crashManager.getUserId())
                .setAppKey(crashManager.getIdentifier())
                .setChannelId(crashManager.getChannelId())
                .setEventType(type)
                .setDownloadId("null");
            String json = builder.toJsonString();
            write.write(json);
            write.flush();
            IOUtils.closeQuietly(write);
            CrashManager.getInstance().startSendEvent();
        } catch (Exception another) {
            ArtisanLogUtil.error("Error saving exception stacktrace!\n", another);
        } finally {
            IOUtils.closeQuietly(write);
        }
    }

    public void saveSessionEvent() {

        BufferedWriter write = null;
        try {
            // Create filename from a random uuid
            String filename = UUID.randomUUID().toString();
            CrashManager crashManager = CrashManager.getInstance();
            String
                path =
                Constants.FILES_PATH + "/" + filename
                + Constants.SESSION_SUFFIX;
            ArtisanLogUtil.print("Writing session event to: " + path);

            // Write the report to disk
            write = new BufferedWriter(new FileWriter(path));
            ReportBuilder builder = new ReportBuilder();
            Map<String, Object> map = new HashMap<>();
            map.put("event_code", "session");
            builder.setBasicInfo().setEventInfo(map).setUserId(crashManager.getUserId())
                .setAppKey(crashManager.getIdentifier())
                .setChannelId(crashManager.getChannelId())
                .setEventType("normal")
                .setDownloadId("null");
            String json = builder.toJsonString();
            write.write(json);
            write.flush();
            IOUtils.closeQuietly(write);
            CrashManager.getInstance().startSendEvent();
        } catch (Exception another) {
            ArtisanLogUtil.error("Error saving session report!\n", another);
        } finally {
            IOUtils.closeQuietly(write);
        }
    }

    public boolean isSessionHolder(Context context) {
        String processName = DeviceInfoCapture.getInstance().getProcessNameFromId(
            android.os.Process.myPid());
        File file = new File(context.getFilesDir() + PATH);
        boolean ret = false;
        if (file.exists()) {
            File[] list = file.listFiles();
            for (File tmp : list) {
                if (tmp.getName().equals(processName)) {
                    ret = true;
                }
            }
        } else {
            File holderFile = new File(context.getFilesDir() + PATH + processName);
            file.mkdir();
            try {
                holderFile.createNewFile();
                ret = true;
            } catch (IOException e) {
                e.printStackTrace();
                ret = false;
            }
        }
        return ret;
    }
}
