package com.odom.signmaker

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

class AppOpenAdManager(private val application: Application) {

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var loadTime = 0L

    // 프로세스 생성 후 아직 콜드 스타트 노출을 끝내지 않았으면 true.
    // 앱이 한 번이라도 백그라운드로 가면 false가 되어 웜 복귀에는 뜨지 않는다.
    private var isColdStartPending = true

    private val prefs by lazy {
        application.getSharedPreferences("SignMakerPrefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "AppOpenAdManager"
        private const val CAP_MILLIS = 3 * 60 * 60 * 1000L        // 3시간 빈도 캡
        private const val AD_EXPIRY_MILLIS = 4 * 60 * 60 * 1000L  // 앱오픈 광고 4시간 만료
        private const val KEY_LAST_SHOWN = "last_app_open_ad_time"
        private const val KEY_FIRST_LAUNCH_DONE = "app_open_first_launch_done"
    }

    fun loadAd() {
        if (isLoadingAd || isAdAvailable()) return
        isLoadingAd = true
        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            application,
            application.getString(R.string.TEST_app_open_ad_unit_id),
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = System.currentTimeMillis()
                    Log.d(TAG, "App open ad loaded")
                    // 콜드 스타트 대기 중이고 아직 포그라운드면 바로 표시
                    maybeShowForColdStart()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingAd = false
                    Log.d(TAG, "App open ad failed to load: ${error.message}")
                }
            }
        )
    }

    private fun isAdAvailable(): Boolean {
        return appOpenAd != null &&
            (System.currentTimeMillis() - loadTime) < AD_EXPIRY_MILLIS
    }

    private fun capElapsed(): Boolean {
        val last = prefs.getLong(KEY_LAST_SHOWN, 0L)
        return (System.currentTimeMillis() - last) >= CAP_MILLIS
    }

    /** 포그라운드 진입 시 호출. */
    fun onAppForegrounded() {
        // 설치 후 첫 실행은 제외 (플래그만 세우고 스킵)
        if (!prefs.getBoolean(KEY_FIRST_LAUNCH_DONE, false)) {
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH_DONE, true).apply()
            isColdStartPending = false
            loadAd() // 다음 콜드 스타트를 위해 미리 로드
            return
        }
        maybeShowForColdStart()
        if (!isAdAvailable()) loadAd() // 아직 없으면 로드 → onAdLoaded에서 표시 재시도
    }

    /** 백그라운드 전환 시 호출. 콜드 스타트 창을 닫아 웜 복귀에는 표시 안 함. */
    fun onAppBackgrounded() {
        isColdStartPending = false
    }

    /**
     * 액티비티가 resume되어 표시 대상(currentActivity)이 생겼을 때 호출.
     * 광고가 onResume보다 먼저 로드되는 레이스에서, onAdLoaded 시점에 currentActivity가
     * 아직 null이라 건너뛴 경우를 보완한다. isColdStartPending 가드가 있어 중복/오표시는 없다.
     */
    fun onActivityAvailable() {
        maybeShowForColdStart()
    }

    private fun maybeShowForColdStart() {
        if (!isColdStartPending) return
        if (isShowingAd) return
        if (!isAdAvailable()) return
        if (!capElapsed()) { isColdStartPending = false; return }

        val activity = (application as SignMakerApplication).currentActivity ?: return
        val ad = appOpenAd ?: return

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                isShowingAd = true
                isColdStartPending = false
                prefs.edit().putLong(KEY_LAST_SHOWN, System.currentTimeMillis()).apply()
            }

            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                appOpenAd = null
                isShowingAd = false
                Log.d(TAG, "App open ad failed to show: ${error.message}")
                loadAd()
            }
        }
        ad.show(activity)
    }
}
