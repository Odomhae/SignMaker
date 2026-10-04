package com.odom.signmaker

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.MobileAds

class SignMakerApplication : Application(),
    Application.ActivityLifecycleCallbacks,
    DefaultLifecycleObserver {

    // 현재 화면에 보이는 액티비티 (앱오픈 광고 표시 대상) - Task 3에서 사용
    var currentActivity: Activity? = null
        private set

    lateinit var appOpenAdManager: AppOpenAdManager
        private set

    override fun onCreate() {
        super<Application>.onCreate()
        MobileAds.initialize(this) {}
        appOpenAdManager = AppOpenAdManager(this)
        appOpenAdManager.loadAd()
        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    // ProcessLifecycleOwner: 앱이 포그라운드로 진입할 때 호출
    override fun onStart(owner: LifecycleOwner) {
        appOpenAdManager.onAppForegrounded()
    }

    override fun onStop(owner: LifecycleOwner) {
        appOpenAdManager.onAppBackgrounded()
    }

    // --- ActivityLifecycleCallbacks: currentActivity 추적 ---
    override fun onActivityResumed(activity: Activity) { currentActivity = activity }
    override fun onActivityPaused(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
