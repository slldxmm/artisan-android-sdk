# TestBird 崩溃分析(Artisan) Android SDK 使用指南

**最新版本1.0.4 更新内容**

1. 支持H5插件，可收集H5 JS异常；
2. 修复启动次数统计bug

## 一、快速集成
TestBird 崩溃分析(Artisan) Android SDK，支持Android 2.3 及以上版本。

集成具体方法如下:

### Step 1. 创建App

* 请注册TestBird账户并登陆产品后台，点这里[注册登陆](https://dt.testbird.com/signup/signup.html/)；
	
* 通过“添加应用”按钮上传应用的apk文件，添加您的新应用；
	
![](/../master/img/new-app.png)

* 添加应用后，在页面下方的“崩溃分析”面板中**获取您的AppKey**，初始化SDK时需要使用；
	
![](/../master/img/android-get-appkey.png)


### Step 2. 下载SDK

* [下载SDK](/../master/sdk-download.md)并解压，支持收集Java异常导致的应用崩溃
    
* 如果您的应用是基于HTML的Hybird App，还可以[下载集成HTML Plugin](/../master/sdk-download.md)，支持收集javascript异常

* 如果您的工程有Native代码（C/C++）或者集成了其他第三方SO库，需要监控Native崩溃，建议下载Artisan的NDK动态库，[下载NDK](/../master/sdk-download.md)

### Step 3. 导入SDK

* **Eclipse**，导入拷贝TestbirdAgent-1.0.4.jar到libs目录.

![](/../master/img/eclipse.jpg)

* **Android Studio**

	- 添加libs目录,并拷贝TestBirdAgent-1.0.4.jar到libs目录

	- 打开module setting，选择dependencies页面，选择添加file dependency，选中TestbirdAgent-1.0.4.jar文件
	
![](/../master/img/modulesettings.png)

### Step 4. 导入NDK

* 若您的应用中使用了C/C++开发模块，或接入了第三方的NDK，则需要捕获C/C++的底层异常，还需要引入NDK的SO库文件

* **Eclipse**，拷贝需要支持的arch文件夹到libs目录

* **Android Studio**，则在build.gradle中添加jniLibs.srcDirs字段

* 注意，集成TestBird SO库时，请只保留支持的架构SO库

![](/../master/img/import-native.png)

### Step 5. 参数配置
在AndroidManifest.xml中添加权限：

        <uses-permission android:name="android.permission.READ_PHONE_STATE"/>
        <uses-permission android:name="android.permission.INTERNET"/>
        <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
        <uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
        <uses-permission android:name="android.permission.READ_LOGS"/>

请避免混淆TestBird Artisan，同时为了定位问题更加方便精准，需要在还原后的堆栈中显示行号和源文件的信息。需要在项目工程的Proguard混淆文件中添加以下配置：

   	 	-keepattributes SourceFile,LineNumberTable
    	-keep public class com.testbird.artisan.TestBirdAgent.**{*;}

### Step 6. 初始化SDK

SDK初始化需要继承Application对象，并在OnCreate函数中进行初始化。

        public class MyApplication extends Application {
            @Override
            public void onCreate() {
                   super.onCreate();
                   // 初始化TestBird崩溃分析SDK
                   CrashManager.register(this, "创建App时得到的AppKey");

                   // 打开SDK Logcat日志输出，默认是关闭状态
                   CrashManager.setDebug(true);
            }
        }
**注意：**请先关闭其他第三方SDK的崩溃捕获接口，然后再初始化TestBird SDK，或将TestBird SDK初始化代码放到最后进行初始化，避免冲突。

## 二、SDK调试
SDK提供了崩溃测试函数。

- 如果启动日志中出现"I/Testbird: testbird agent init complete."语句，则表示SDK初始化成功。

![](/../master/img/log-init.jpg)

#### 为确保SDK正常工作，请触发一次崩溃，并检查日志和Web分析报表

**注意：多数时候需要重启一次应用，崩溃信息才能够上报成功**

- 调用CrashManager.setDebug(true)，打开SDK Logcat日志输出，默认是关闭状态
- 触发java crash，调用CrashManager.testJavaCrash()
- 触发native crash，调用CrashManager.testNativeCrash()
- 如果Logcat中出现"Writing unhandled exception to:"语句则表示SDK记录崩溃成功.

![](/../master/img/log-crash.jpg)

## 三、符号化文件上传

### 3.1 Java符号化文件

如果项目使用了Proguard混淆代码，将自动生成mapping.txt文件。TestBird会用mapping.txt进行错误堆栈还原,帮助快速定位问题。所以要优先上传该文件，根据开发环境不同，可以从以下路径找到mapping.txt。

- Android Studio: 在 projectname/app/build/outputs/mapping/目录下

- Eclipse: 在 projectname/proguard/目录下

**上传文件：**请通过崩溃分析应用设置中的“版本管理”直接上传mapping.txt，各个版本需要分别上传符号化文件。


### 3.2 Native符号化文件

Native错误堆栈还原,需要使用编译过程中生成的obj文件.压缩obj/local文件夹下的所有文件为zip文件，并上传。

## 四、API说明

### 4.1 初始化SDK
启用TestBirdAgent，注册的APP Key。还可以注册渠道ID，以便监控分析不同渠道APK包的表现，默认channelId为空。

    public static void register(Context context, String appKey, String channelId)

### 4.2 设置User ID
调用该方法，设置当前使用App的用户账号，以便跟踪用户反馈，找出对应的崩溃或异常。

    public static void setUserId(String userId)

### 4.3 自定义Log日志
调用一下方法，添加不同日志级别的自定义Log日志，该Log会随崩溃堆栈等信息收集到崩溃报表中。自定义Log日志缓存Buffer是32KB。

    public static void addVerboseLog(String line)·
    public static void addInfoLog(String line)
    public static void addDebugLog(String line)
    public static void addWarnLog(String line)
    public static void addErrorLog(String line)


### 4.4 自定义键值对参数
#### 添加键值对
调用该方法后，将向缓存中添加一条键值对参数，参数将会被收集到崩溃报表中。最多设置32条自定义键值对，每对最大1 KB。

    public static void addCustomKeyPair(String key, Object value)

#### 移除键值对

    public static void removeCustomKeyPair(String key)

#### 清除键值对

    public static void clearCustomKeyPairs()

### 4.5 SDK日志输出控制
将SDK设置为debug模式后，TestBirdAgent会输出Logcat日志，默认为关闭状态。

    public static void setDebug(Boolean isDebug)

### 4.6 主动上报catch的异常
将开发者代码中catch住的异常，进行主动上报。手动上报的异常将单独显示在分析报表的“异常”栏目中。

    public static void submitException(Throwable throwable)

### 4.7 设置传输方式为HTTPs
将SDK的通信方式改变为HTTPs，默认方式为HTTP。

    public static void enableHttps()
