package com.dexterous.flutterlocalnotifications;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry.PluginRegistrantCallback;
import io.flutter.view.FlutterCallbackInformation;
import io.flutter.view.FlutterMain;
import io.flutter.view.FlutterNativeView;
import io.flutter.view.FlutterRunArguments;

public class NotificationService extends JobIntentService implements MethodChannel.MethodCallHandler {
    private static final String TAG = "NotificationService";
    private static final String BACKGROUND_CHANNEL = "dexterous.com/flutter/local_notifications_background";
    private static final int JOB_ID = (int)UUID.randomUUID().getMostSignificantBits();
    private static final String NOTIFICATIONSERVICE_INITIALIZED_METHOD = "NotificationService.initialized";
    private static AtomicBoolean started = new AtomicBoolean(false);
    private static PluginRegistrantCallback pluginRegistrantCallback;
    private MethodChannel backgroundChannel;
    private ArrayDeque onShowNotificationQueue = new ArrayDeque<Map<String, Object>>();
    private FlutterNativeView backgroundFlutterView;
    public static void enqueueWork(Context context, Intent intent) {
        enqueueWork(context, NotificationService.class, JOB_ID, intent);
    }

    private void startNotificationService(Context context) {
        synchronized (started) {
            FlutterMain.ensureInitializationComplete(context, null);
            long callbackHandle = context.getSharedPreferences(
                    FlutterLocalNotificationsPlugin.SHARED_PREFERENCES_KEY,
                    Context.MODE_PRIVATE)
                    .getLong(FlutterLocalNotificationsPlugin.CALLBACK_DISPATCHER_HANDLE_KEY, 0);
            FlutterCallbackInformation callbackInfo = FlutterCallbackInformation.lookupCallbackInformation(callbackHandle);
            if (callbackInfo == null) {
                Log.e(TAG, "Fatal: failed to find callback");
                return;
            }
            backgroundFlutterView = new FlutterNativeView(context, true);
            FlutterRunArguments args = new FlutterRunArguments();
            args.bundlePath = FlutterMain.findAppBundlePath(context);
            args.entrypoint = callbackInfo.callbackName;
            args.libraryPath = callbackInfo.callbackLibraryPath;
            backgroundFlutterView.runFromBundle(args);
            if (pluginRegistrantCallback != null) {
                pluginRegistrantCallback.registerWith(backgroundFlutterView.getPluginRegistry());
            }

        }
        backgroundChannel = new MethodChannel(backgroundFlutterView, BACKGROUND_CHANNEL);
        backgroundChannel.setMethodCallHandler(this);
        Log.i(TAG, "NotificationService started");
    }


    public static void setPluginRegistrant(PluginRegistrantCallback callback) {
        pluginRegistrantCallback = callback;
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "NotificationService onCreate");
        super.onCreate();
        startNotificationService(this);
    }


    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        Log.i(TAG, "NotificationService onHandleWork");
        switch(intent.getAction()) {
            case FlutterLocalNotificationsPlugin.SHOW_NOTIFICATION_ACTION:
                Log.i(TAG, "NotificationService intent action " + FlutterLocalNotificationsPlugin.SHOW_NOTIFICATION_ACTION);
                synchronized(started) {
                    HashMap<String, Object> callbackArgs = (HashMap<String, Object>) intent.getSerializableExtra(FlutterLocalNotificationsPlugin.ON_SHOW_NOTIFICATION_METHOD_ARGS);
                    /*if (!started.get()) {
                        Log.i(TAG, "NotificationService queued callback");
                        onShowNotificationQueue.add(callbackArgs);
                    } else {
                        Log.i(TAG, "NotificationService invoke callback");
                        backgroundChannel.invokeMethod(FlutterLocalNotificationsPlugin.ON_SHOW_NOTIFICATION_METHOD, callbackArgs);
                    }*/
                    onShowNotificationQueue.add(callbackArgs);
                }
                break;
        }
    }


    @Override
    public void onMethodCall(MethodCall call, MethodChannel.Result result) {
        Log.i(TAG, "NotificationService method call: " + call.method);
        if(call.method.equals(NOTIFICATIONSERVICE_INITIALIZED_METHOD)) {
            synchronized (started) {
                while(!onShowNotificationQueue.isEmpty()) {
                    Log.i(TAG, "NotificationService invoke queued callback");
                    backgroundChannel.invokeMethod(FlutterLocalNotificationsPlugin.ON_SHOW_NOTIFICATION_METHOD, onShowNotificationQueue.remove());
                }
                started.set(true);
                result.success(null);
            }
        } else {
            result.notImplemented();
        }
    }
}
