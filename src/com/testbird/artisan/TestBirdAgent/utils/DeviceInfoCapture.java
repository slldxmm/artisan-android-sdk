package com.testbird.artisan.TestBirdAgent.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.opengl.GLES10;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLES31;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.OrientationEventListener;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by chenxuesong on 15/11/3.
 */
public class DeviceInfoCapture {

    private Context mContext;
    private TelephonyManager mTelephonyManager;
    private WindowManager mWindowManager;
    private BroadcastReceiver mBatInfoReceiver;
    private OrientationEventListener mOrientationEventListener;
    private int mBatLevel;
    private boolean mIsCharging;
    private int mDeviceOrientation;
    private String mCurrentActivityName;
    private List<Map> mActivityTracks;
    private static DeviceInfoCapture sInstance;
    private final static int DEFAULT_BATTERY_LEVEL = 0;
    private final static int DEFAULT_CHARGING_STATUS = BatteryManager.BATTERY_STATUS_NOT_CHARGING;
    private final static String PLATFORM_NAME = "android";
    private static final String NETWORK_CLASS_2_G = "2G";
    private static final String NETWORK_CLASS_3_G = "3G";
    private static final String NETWORK_CLASS_4_G = "4G";
    private static final String NETWORK_CLASS_UNKNOWN = "unknown";
    private static final String NETWORK_CLASS_WIFI = "wifi";
    private static final String DEFAULT_MAC_ADDRESS = "null";
    private static final String DEFAULT_CARRIER_NAME = "null";
    private static final String ORIENTATION_PORTTAIT = "Portrait";
    private static final String ORIENTATION_LANDSCAPE = "Landscape";
    private static final String ORIENTATION_DEFAULT = "N/A";
    private static final int KB = 1024;
    private static final int MB = 1024 * KB;
    private static final int ACTIVITY_TRACK_SIZE = 32;
    private static final int FIRST_ACTIVITY_EVENT_INDEX = 1;
    private String mGpu = "";
    private String mGpuVendor = "";
    private String mGpuOpenGLVersion = "";

    private DeviceInfoCapture(Context context) {
        mContext = context;
        mTelephonyManager =
            (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mWindowManager =
            (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mActivityTracks = new ArrayList<>();
        insertActivityTrack("Session Start");
        initGpuInfo();
    }

    public synchronized static DeviceInfoCapture getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("must be initialized before use!");
        }
        return sInstance;
    }

    public synchronized static void init(Context context) {
        if (sInstance == null) {
            sInstance = new DeviceInfoCapture(context.getApplicationContext());
            if (context instanceof Application) {
                sInstance.registerActivityLifeCallback((Application) context);
            }
            sInstance.registerOrientationListener();
            sInstance.registerBatteryLevelListener();
        }
    }

    private void registerBatteryLevelListener() {
        mBatInfoReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctxt, Intent intent) {
                mBatLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, DEFAULT_BATTERY_LEVEL);
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                                                DEFAULT_CHARGING_STATUS);
                mIsCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                              status == BatteryManager.BATTERY_STATUS_FULL;
            }
        };
        mContext.registerReceiver(mBatInfoReceiver,
                                  new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void registerOrientationListener() {
        mOrientationEventListener = new OrientationEventListener(mContext,
                                                                 SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                mDeviceOrientation = orientation;
            }
        };
        if (mOrientationEventListener.canDetectOrientation()) {
            mOrientationEventListener.enable();
        }
    }

    private void registerActivityLifeCallback(Application application) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            ArtisanLogUtil.debugLog("register activitylifecycle callbacks.");
            application
                .registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                        insertActivityTrack(activity.getLocalClassName() + "#OnCreate");
                    }

                    @Override
                    public void onActivityStarted(Activity activity) {
                        // ignore
                    }

                    @Override
                    public void onActivityResumed(Activity activity) {
                        mCurrentActivityName = activity.getLocalClassName();
                        insertActivityTrack(activity.getLocalClassName() + "#OnResume");
                    }

                    @Override
                    public void onActivityPaused(Activity activity) {
                        // ignore
                    }

                    @Override
                    public void onActivityStopped(Activity activity) {
                        // ignore
                    }

                    @Override
                    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                        // ignore
                    }

                    @Override
                    public void onActivityDestroyed(Activity activity) {
                        // ignore
                    }
                });
        }
    }

    public String getPlatformName() {
        return PLATFORM_NAME;
    }

    public String getPlatformVersion() {
        return Build.VERSION.RELEASE;
    }

    public String getDeviceBrand() {
        return Build.BRAND;
    }

    public String getDeviceModel() {
        return Build.MODEL;
    }

    /**
     * Map contains android id & imei & mac etc.
     */
    public Map<String, Object> getDeviceIdentifiers() {
        Map<String, Object> map = new HashMap<>();
        String android_id = Settings.Secure.getString(mContext.getContentResolver(),
                                                      Settings.Secure.ANDROID_ID);
        map.put("android_id", android_id);
        map.put("device_id", mTelephonyManager.getDeviceId());
        map.put("mac", getMacAddress());
        return map;
    }

    public String getMacAddress() {
        WifiManager wifi = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        if (wifi == null) {
            return DEFAULT_MAC_ADDRESS;
        } else {
            WifiInfo info = wifi.getConnectionInfo();
            if (info == null) {
                return DEFAULT_MAC_ADDRESS;
            } else {
                return info.getMacAddress();
            }
        }
    }

    public String getDisplayScreenResolution() {
        int width, height = 0;
        DisplayMetrics dm = new DisplayMetrics();
        android.view.Display display = mWindowManager.getDefaultDisplay();
        display.getMetrics(dm);
        width = dm.widthPixels;
        height = dm.heightPixels;
        if (height > width) {
            return width + "x" + height;
        } else {
            return height + "x" + width;
        }

    }

    public int getBatteryLevel() {
        return mBatLevel;
    }

    public boolean isCharing() {
        return mIsCharging;
    }

    public long getExternalFreeSize() {
        String state = Environment.getExternalStorageState();
        if (state.equals(Environment.MEDIA_MOUNTED)) {
            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
            long bytesAvailable = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                bytesAvailable = stat.getAvailableBytes();
            } else {
                bytesAvailable = (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();
            }
            long megAvailable = bytesAvailable / (KB);
            return megAvailable;
        } else {
            return 0;
        }
    }

    public long getExternalTotalSize() {
        String state = Environment.getExternalStorageState();
        if (state.equals(Environment.MEDIA_MOUNTED)) {
            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
            long bytesTotal = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                bytesTotal = stat.getTotalBytes();
            } else {
                bytesTotal = (long) stat.getBlockCount() * (long) stat.getBlockSize();
            }
            long megTotal = bytesTotal / (KB);
            return megTotal;
        } else {
            return 0;
        }
    }

    public long getInternalFreeSize() {
        StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
        long bytesAvailable = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            bytesAvailable = stat.getAvailableBytes();
        } else {
            bytesAvailable = (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();
        }
        long megAvailable = bytesAvailable / (KB);
        return megAvailable;
    }

    public long getInternalTotalSize() {
        StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
        long bytesTotal = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            bytesTotal = stat.getTotalBytes();
        } else {
            bytesTotal = (long) stat.getBlockCount() * (long) stat.getBlockSize();
        }
        long megTotal = bytesTotal / (KB);
        return megTotal;
    }

    private MemoryInfo getMemoryInfo() {
        ActivityManager activityManager = (ActivityManager) mContext.getSystemService(
            Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo;
    }

    public long getFreeMemory() {
        return getMemoryInfo().availMem / KB;
    }

    public long getTotalMemory() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            return getMemoryInfo().totalMem / KB;
        } else {
            String meminfoPath = "/proc/meminfo";
            String totalMemLine;
            String[] arrayOfString;
            FileReader localFileReader = null;
            BufferedReader localBufferedReader = null;
            long totalMemory = 0;
            try {
                localFileReader = new FileReader(meminfoPath);
                localBufferedReader = new BufferedReader(localFileReader);
                totalMemLine = localBufferedReader.readLine();
                arrayOfString = totalMemLine.split("\\s+");
                totalMemory = Integer.valueOf(arrayOfString[1]).intValue() / KB;
                localBufferedReader.close();
                localFileReader.close();
            } catch (IOException e) {
                // ignore
            } finally {
                try {
                    if (localBufferedReader != null) {
                        localBufferedReader.close();
                    }
                    if (localFileReader != null) {
                        localFileReader.close();
                    }
                } catch (IOException e) {
                    // ignore
                }
            }
            return totalMemory;
        }
    }

    private String getNetWorkClass(int networkType) {
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:
                return NETWORK_CLASS_2_G;
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return NETWORK_CLASS_3_G;
            case TelephonyManager.NETWORK_TYPE_LTE:
                return NETWORK_CLASS_4_G;
            default:
                return NETWORK_CLASS_UNKNOWN;
        }
    }

    public String getNetWorkType() {
        ConnectivityManager connectMgr = (ConnectivityManager) mContext
            .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectMgr != null) {
            NetworkInfo info = connectMgr.getActiveNetworkInfo();
            if (info != null) {
                if (info.getType() == ConnectivityManager.TYPE_WIFI) {
                    return NETWORK_CLASS_WIFI;
                } else {
                    return getNetWorkClass(info.getSubtype());
                }
            }
        }
        return NETWORK_CLASS_UNKNOWN;
    }

    public String getCarrierName() {
        if (mTelephonyManager == null) {
            return DEFAULT_CARRIER_NAME;
        } else {
            String simString = mTelephonyManager.getNetworkOperatorName();
            if (simString == null) {
                return DEFAULT_CARRIER_NAME;
            } else {
                return simString;
            }
        }
    }

    public boolean haveRoot() {
        return RootDetect.isDeviceRooted();
    }

    public boolean isProximitySensorOpen() {
        SensorManager mSensorManager = (SensorManager) mContext.getSystemService(
            Context.SENSOR_SERVICE);
        Sensor mProxSensor = mSensorManager.getDefaultSensor(SensorManager.SENSOR_PROXIMITY);
        return mProxSensor == null ? false : true;
    }

    public String getDeviceOrientation() {
        if (mDeviceOrientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
            return ORIENTATION_DEFAULT;
        } else if ((mDeviceOrientation >= 0 && mDeviceOrientation <= 45) || (
            mDeviceOrientation >= 135 && mDeviceOrientation <= 225)) {
            return ORIENTATION_PORTTAIT;
        } else {
            return ORIENTATION_LANDSCAPE;
        }
    }

    public String getResourceOrientation() {
        int current = mContext.getResources().getConfiguration().orientation;
        if (current == Configuration.ORIENTATION_PORTRAIT) {
            return ORIENTATION_PORTTAIT;
        } else {
            return ORIENTATION_LANDSCAPE;
        }
    }

    public boolean isForeground() {
        ActivityManager
            activityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo>
            appProcesses = activityManager.getRunningAppProcesses();
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.processName.equals(mContext.getPackageName())) {
                return appProcess.importance
                       == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
            }
        }
        return false;
    }

    public PackageInfo getPackageInfo() {
        PackageManager packageManager = mContext.getPackageManager();
        PackageInfo packInfo = null;
        try {
            packInfo =
                packageManager.getPackageInfo(mContext.getApplicationContext().getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            // ignore
        }
        return packInfo;
    }

    /**
     * Can't get cpu vendor, so get manufacturer instead.
     */
    public String getCpuVendor() {
        return Build.MANUFACTURER;
    }

    public String getCpuModel() {
        String path = "/proc/cpuinfo";
        File tempNodeFile = new File(path);
        BufferedReader reader = null;
        String tempString;
        String cpuSerial = "null";
        try {
            reader = new BufferedReader(new FileReader(tempNodeFile));
            while ((tempString = reader.readLine()) != null) {
                if (tempString.contains("Hardware")) {
                    break;
                }
            }
            if (tempString != null && tempString.contains("Hardware")) {
                String[] array = tempString.split(":");
                if (array.length > 1) {
                    cpuSerial = array[1].trim();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(reader);
        }
        if (cpuSerial.equals("null")) {
            cpuSerial = Build.HARDWARE;
        }
        return cpuSerial;
    }

    public String getCpuArch() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT_WATCH) {
            String abis[] = Build.SUPPORTED_ABIS;
            StringBuilder builder = new StringBuilder();
            for (String abi : abis) {
                builder.append(abi + ",");
            }
            String result = builder.toString();
            return result.substring(0, result.length() - 1);
        } else {
            return Build.CPU_ABI;
        }
    }

    private void initGpuInfo() {
        Handler handler = new Handler(mContext.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mGpuVendor = GLES20.glGetString(GLES20.GL_VENDOR);
                mGpu = GLES20.glGetString(GLES20.GL_RENDERER);
                mGpuOpenGLVersion = GLES20.glGetString(GLES20.GL_VERSION);
            }
        }, 3000);
    }

    /**
     * OpenGL ES 2.0 - This API specification is supported by Android 2.2 (API level 8) and higher.
     * So we can use GLES20 api to get gpu info.
     */
    public String getGpuVendor() {
        return mGpuVendor;
    }

    public String getGpuModel() {
        return mGpu;
    }

    public String getGpuOpenGlVersion() {
        return mGpuOpenGLVersion;
    }

    public String getCurrentActivityName() {
        return mCurrentActivityName;
    }

    /**
     * Get process Name with process id.
     *
     * @param pid Process id.
     * @return Process Name.
     */
    public String getProcessNameFromId(int pid) {
        ActivityManager manager
            = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo processInfo : manager.getRunningAppProcesses()) {
            if (processInfo.pid == pid) {
                return processInfo.processName;
            }
        }
        return "null";
    }

    private void insertActivityTrack(String event) {
        ActivityTrack track = new ActivityTrack();
        track.event = event;
        track.time = System.currentTimeMillis();
        if (mActivityTracks.size() >= ACTIVITY_TRACK_SIZE) {
            mActivityTracks.remove(FIRST_ACTIVITY_EVENT_INDEX);
        }
        mActivityTracks.add(track.toMap());
        ArtisanLogUtil.debugLog(track.event + " " + mActivityTracks.size());
    }

    public void insertUrlTrack(String url) {
        ArtisanLogUtil.print("insert " + url);
        insertActivityTrack(url);
    }

    public List<Map> getActivityTracks() {
        return mActivityTracks;
    }
}
