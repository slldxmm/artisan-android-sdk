package com.testbird.artisan.TestBirdAgent.report;

import android.content.pm.PackageInfo;

import com.testbird.artisan.TestBirdAgent.Constants;
import com.testbird.artisan.TestBirdAgent.CrashManager;
import com.testbird.artisan.TestBirdAgent.utils.DeviceInfoCapture;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by chenxuesong on 15/11/4.
 */
public class ReportBuilder {

    private Map<String, Object> mMap;
    private JSONObject mJsonObject;

    public ReportBuilder() {
        this.mMap = new HashMap<>();
        this.mJsonObject = new JSONObject();
    }

    public String toJsonString() {
        mJsonObject = new JSONObject(mMap);
        return mJsonObject.toString();
    }

    /**
     * Build basic info for normal event. Such as session.
     *
     * @return Current ReportBuilder.
     */
    public ReportBuilder setBasicInfo() {
        DeviceInfoCapture capture = DeviceInfoCapture.getInstance();
        PackageInfo packageInfo = capture.getPackageInfo();
        mMap.put("date", System.currentTimeMillis());
        mMap.put("sdk_version", CrashManager.SDK_VERSION);
        if (packageInfo == null) {
            mMap.put("package_name", "null");
            mMap.put("app_version_name", "null");
            mMap.put("app_version_code", "null");
        } else {
            mMap.put("package_name", packageInfo.packageName);
            mMap.put("app_version_name", packageInfo.versionName);
            mMap.put("app_version_code", String.valueOf(packageInfo.versionCode));
        }
        mMap.put("os_name", capture.getPlatformName());
        mMap.put("os_version", capture.getPlatformVersion());
        mMap.put("device_brand", capture.getDeviceBrand());
        mMap.put("device_model", capture.getDeviceModel());
        mMap.put("download_id", "null");
        mMap.put("device_identifiers", new JSONObject(capture.getDeviceIdentifiers()));
        mMap.put("network_type", capture.getNetWorkType());
        return this;
    }

    /**
     * Set device current info.
     */
    public ReportBuilder setDeviceStatusInfo() {
        DeviceInfoCapture capture = DeviceInfoCapture.getInstance();
        mMap.put("battery", capture.getBatteryLevel());
        mMap.put("is_charging", capture.isCharing());
        mMap.put("free_space", capture.getInternalFreeSize());
        mMap.put("total_space", capture.getInternalTotalSize());
        mMap.put("free_ram", capture.getFreeMemory());
        mMap.put("total_ram", capture.getTotalMemory());
        mMap.put("free_ext_space", capture.getExternalFreeSize());
        mMap.put("total_ext_space", capture.getExternalTotalSize());
        mMap.put("carrier", capture.getCarrierName());
        mMap.put("is_rooted", capture.haveRoot());
        mMap.put("proximity", capture.isProximitySensorOpen());
        mMap.put("orientation", capture.getDeviceOrientation());
        mMap.put("ui_orientation", capture.getResourceOrientation());
        mMap.put("on_fouse", capture.isForeground());
        mMap.put("error_view", capture.getCurrentActivityName());
        mMap.put("gpu_model", capture.getGpuModel());
        mMap.put("gpu_vendor", capture.getGpuVendor());
        mMap.put("gpu_arch", capture.getGpuOpenGlVersion());
        mMap.put("cpu_model", capture.getCpuModel());
        mMap.put("cpu_vendor", capture.getCpuVendor());
        mMap.put("cpu_arch", capture.getCpuArch());
        mMap.put("screen_resolution", capture.getDisplayScreenResolution());
        return this;
    }

    public ReportBuilder setDownloadId(String id) {
        mMap.put("download_id", id);
        return this;
    }

    public ReportBuilder setAppKey(String appKey) {
        mMap.put("app_key", appKey);
        return this;
    }

    public ReportBuilder setChannelId(String channelId) {
        mMap.put("channel_id", channelId);
        return this;
    }

    public ReportBuilder setUserId(String userId) {
        mMap.put("user_id", userId);
        return this;
    }

    public ReportBuilder setRuntime(String code) {
        String runtime = "";
        switch (code) {
            case Constants.JAVA_CRASH:
            case Constants.CAUGHT_EXCEPTION:
            case Constants.ANR:
                runtime = Constants.RUNTIME_JAVA;
                break;
            case Constants.NATIVE_CRASH:
                runtime = Constants.RUNTIME_NDK;
                break;
            case Constants.COCOS_JS_CAUHGT_EXCEPTOIN:
            case Constants.COCOS_JS_EXCEPTOIN:
            case Constants.COCOS_LUA_CAUGHT_EXCEPTOIN:
            case Constants.COCOS_LUA_EXCEPTOIN:
                runtime = Constants.RUNTIME_COCOS;
                break;
            case Constants.UNITY_CS_CAUGHT_EXCEPTION:
            case Constants.UNITY_CS_EXCEPTION:
                runtime = Constants.RUNTIME_UNITY;
                break;
            case Constants.HTML5_JS_EXCEPTION_EXCEPTION:
            case Constants.HTML5_JS_EXCEPTION:
                runtime = Constants.RUNTIME_HTML5;
                break;
        }
        mMap.put("runtime", runtime);
        return this;
    }

    /**
     * Set report type. Such as session, crash.
     *
     * @param type type name.
     */
    public ReportBuilder setEventType(String type) {
        mMap.put("event_type", type);
        return this;
    }

    /**
     * Set event_info to report map.
     *
     * @param map contains crash stack, threads trace, event_key...
     */
    public ReportBuilder setEventInfo(Map<String, Object> map) {
        mMap.put("event_info", new JSONObject(map));
        return this;
    }
}
