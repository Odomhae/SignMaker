package com.odom.signmaker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.gcacace.signaturepad.views.SignaturePad
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.material.slider.Slider
import com.odom.signmaker.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class MainActivity : AppCompatActivity() {
    private lateinit var binding : ActivityMainBinding
    lateinit var signBitmap : Bitmap

    // 뒤로가기 시 종료 확인 다이얼로그 표시
    private val callback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            showExitDialog()
        }
    }


    var permission_list = if(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU){
        arrayOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    } else {
        null
    }

    // 광고
    lateinit var mAdView : AdView
    private var mInterstitialAd: InterstitialAd? = null
    private var exitAdView: AdView? = null
    private lateinit var sharedPreferences: SharedPreferences

    // 펜 색상 팔레트 - 흰 배경에서 잘 보이는 10색
    private val penColors = intArrayOf(
        0xFF000000.toInt(), // 검정
        0xFF757575.toInt(), // 회색
        0xFFD32F2F.toInt(), // 빨강
        0xFFF57C00.toInt(), // 주황
        0xFF5D4037.toInt(), // 갈색
        0xFF388E3C.toInt(), // 초록
        0xFF00796B.toInt(), // 청록
        0xFF1976D2.toInt(), // 파랑
        0xFF303F9F.toInt(), // 남색
        0xFF7B1FA2.toInt(), // 보라
        0xFFFBC02D.toInt(), // 노랑
        0xFF03A9F4.toInt(), // 하늘
        0xFFEC407A.toInt(), // 핑크
        0xFF26A69A.toInt(), // 민트
        0xFFFF7043.toInt()  // 코랄
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root

        setContentView(view)

        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("SignMakerPrefs", Context.MODE_PRIVATE)

        checkPermission()

        // 광고 초기화 - 배너 / 전면 / 종료 다이얼로그용 배너 (onStart마다 재로드하지 않도록 onCreate에서 1회만)
        MobileAds.initialize(this) {}
        mAdView = binding.adMobView
        mAdView.loadAd(AdRequest.Builder().build())
        loadInterstitialAd()
        loadExitBannerAd()

        // top, bottom padding
        val contentView: View = this.findViewById(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(contentView, object : OnApplyWindowInsetsListener {
            override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
                val innerPadding = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                v.setPadding(0, innerPadding.top, 0, innerPadding.bottom)

                return insets
            }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val insetsController = WindowInsetsControllerCompat(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = false
         //   insetsController.isAppearanceLightNavigationBars = isLightStatusBars

            window.decorView.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_blue))
        }

        this.onBackPressedDispatcher.addCallback(this, callback)

        binding.btSave.isEnabled = false

        binding.signaturePad.setOnSignedListener(object : SignaturePad.OnSignedListener {
            override fun onStartSigning() {
                binding.btSave.isClickable = false
                binding.btSave.isEnabled = false

            }
            override fun onSigned() {
                binding.btSave.isClickable = true
                binding.btSave.isEnabled = true
            }

            override fun onClear() {
                binding.btSave.isClickable = false
                binding.btSave.isEnabled = false
            }
        })

        binding.btRedraw.setOnClickListener {
            binding.signaturePad.clear()
            
            // SharedPreferences를 사용한 다시 그리기 카운터 증가 및 전면광고 표시
            val redrawCount = sharedPreferences.getInt("redraw_count", 0) + 1
            val editor = sharedPreferences.edit()
            editor.putInt("redraw_count", redrawCount)
            editor.apply()
            
            if (redrawCount % 3 == 0) {
                showInterstitialAd()
            }
        }

        binding.btSave.setOnClickListener {
            signBitmap = binding.signaturePad.transparentSignatureBitmap //  drawToBitmap()
            saveImg(signBitmap)

            binding.signaturePad.clear()
        }

        binding.btChangecolor.setOnClickListener {
            showPenSettingsDialog()
        }

        // 저장된 펜 색/굵기 복원
        applyPenSettings()
    }

    override fun onResume() {
        super.onResume()
        mAdView.resume()
        exitAdView?.resume()
    }

    override fun onPause() {
        mAdView.pause()
        exitAdView?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        mAdView.destroy()
        exitAdView?.destroy()
        super.onDestroy()
    }

    fun saveImg(bitmap: Bitmap) {

        val directory = createAppDirectoryInDownloads()
        val fileName =  System.currentTimeMillis().toString() + ".png"
        val file = File(directory, fileName)

        try {
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()

            // Notify the media scanner about the new image
//            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
//            val contentUri = Uri.fromFile(file)
//            mediaScanIntent.data = contentUri
//            sendBroadcast(mediaScanIntent)

            MediaScannerConnection.scanFile(this, arrayOf(file.toString()),
                null, null)

            val inflater = layoutInflater
            val view: View = inflater.inflate(
                R.layout.toast_image_layout,
                findViewById<ViewGroup>(R.id.relativeLayout1)
            )
            val toast = Toast(applicationContext)
            toast.view = view
            toast.setGravity(Gravity.TOP, 0, 200)
            toast.show()

            // 다이얼로그로 갤러리 열기 선택
            val dialogView = layoutInflater.inflate(R.layout.custom_dialog, null)

            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .create()

            dialogView.findViewById<Button>(R.id.openGalleryButton).setOnClickListener {
                val uri = FileProvider.getUriForFile(
                    this,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, "image/*")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(intent)
                dialog.dismiss()
            }

            dialogView.findViewById<Button>(R.id.cancelButton).setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()

            // SharedPreferences를 사용한 서명 카운터 증가 및 전면광고 표시
            val signatureCount = sharedPreferences.getInt("signature_count", 0) + 1
            val editor = sharedPreferences.edit()
            editor.putInt("signature_count", signatureCount)
            editor.apply()
            
            // 전면광고가 나오는 회차에는 리뷰 요청을 생략해 팝업이 겹치지 않도록 함
            if (signatureCount % 3 == 0) {
                showInterstitialAd()
            } else {
                reviewApp()
            }

        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
        }

    }

    fun createAppDirectoryInDownloads(): File? {
        val downloadsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val appDirectory = File(downloadsDirectory, "SignMaker")

        if (!appDirectory.exists()) {
            val directoryCreated = appDirectory.mkdir()
            if (!directoryCreated) {
                // Failed to create the directory
                Toast.makeText(this, R.string.fail_create_folder , Toast.LENGTH_SHORT).show()
                return null
            } else {
                Toast.makeText(this, R.string.success_create_folder , Toast.LENGTH_SHORT).show()


            }
        } else {
          //  Toast.makeText(this, appDirectory.absolutePath , Toast.LENGTH_SHORT).show()
        }

        return appDirectory
    }

    fun checkPermission() {
        if (permission_list != null) {
            for (permission in permission_list!!) {
                //권한 허용 여부를 확인한다.
                val chk = checkCallingOrSelfPermission(permission)
                if (chk == PackageManager.PERMISSION_DENIED) {
                    requestPermissions(permission_list!!, 0)
                }
            }

        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        var result = true
        if (requestCode == 0) {
            for (i in grantResults.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(applicationContext, R.string.request_permission, Toast.LENGTH_LONG).show()
                    result = false
                }
            }

            if (result == false) {
                finish()
            }

        }
    }

    private fun reviewApp() {
        val manager = ReviewManagerFactory.create(this@MainActivity)
        val request: Task<ReviewInfo> = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo: ReviewInfo = task.result
                manager.launchReviewFlow(this@MainActivity, reviewInfo)
                    .addOnCompleteListener { task1: Task<Void?> ->
                        if (task1.isSuccessful) {
                            Log.d("TAG", "Review Success")
                        }
                    }
            } else {
                Log.d("TAG", "Review Error")
            }
        }
    }
    
    private fun applyPenSettings() {
        binding.signaturePad.setPenColor(sharedPreferences.getInt("pen_color", penColors[0]))
        val penWidth = sharedPreferences.getFloat("pen_width", 7f)
        binding.signaturePad.setMaxWidth(penWidth)
        binding.signaturePad.setMinWidth(penWidth * 0.4f)
    }

    // 펜 색상 10색 + 굵기 슬라이더 다이얼로그
    private fun showPenSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pen_settings, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val grid = dialogView.findViewById<GridLayout>(R.id.colorGrid)
        val density = resources.displayMetrics.density
        var selectedColor = sharedPreferences.getInt("pen_color", penColors[0])

        // "펜 굵기" 라벨 옆 미리보기 점 - 현재 색상/굵기를 점 크기로 표현
        val preview = dialogView.findViewById<View>(R.id.widthPreview)
        val slider = dialogView.findViewById<Slider>(R.id.widthSlider)
        fun updatePreview() {
            preview.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(selectedColor)
            }
            val dotSize = (slider.value * density).toInt()
            preview.layoutParams = preview.layoutParams.apply {
                width = dotSize
                height = dotSize
            }
            preview.requestLayout()
        }

        // 선택된 색상에 굵은 테두리 + 체크 표시 (체크 색은 스와치 밝기에 따라 자동 대비)
        fun updateSwatches() {
            for (i in 0 until grid.childCount) {
                val swatch = grid.getChildAt(i)
                val selected = penColors[i] == selectedColor
                swatch.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(penColors[i])
                    if (selected) {
                        setStroke((3 * density).toInt(), ContextCompat.getColor(this@MainActivity, R.color.blue))
                    } else {
                        setStroke((1 * density).toInt(), 0x33000000)
                    }
                }
                if (selected) {
                    val check = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_check_mark)?.mutate()
                    val checkColor = if (ColorUtils.calculateLuminance(penColors[i]) > 0.5) Color.BLACK else Color.WHITE
                    check?.setTint(checkColor)
                    swatch.foreground = check
                    swatch.foregroundGravity = Gravity.CENTER
                } else {
                    swatch.foreground = null
                }
            }
        }

        val size = (40 * density).toInt()
        val margin = (6 * density).toInt()
        penColors.forEach { color ->
            val swatch = View(this)
            swatch.layoutParams = GridLayout.LayoutParams().apply {
                width = size
                height = size
                setMargins(margin, margin, margin, margin)
            }
            swatch.setOnClickListener {
                selectedColor = color
                binding.signaturePad.setPenColor(color)
                sharedPreferences.edit().putInt("pen_color", color).apply()
                updateSwatches()
                updatePreview()
            }
            grid.addView(swatch)
        }
        updateSwatches()

        slider.value = sharedPreferences.getFloat("pen_width", 7f)
        slider.addOnChangeListener { _, value, _ ->
            binding.signaturePad.setMaxWidth(value)
            binding.signaturePad.setMinWidth(value * 0.4f)
            sharedPreferences.edit().putFloat("pen_width", value).apply()
            updatePreview()
        }
        updatePreview()

        dialogView.findViewById<Button>(R.id.confirmButton).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // 종료 다이얼로그에 넣을 배너를 미리 로드해둔다
    private fun loadExitBannerAd() {
        exitAdView = AdView(this).apply {
            adUnitId = getString(R.string.TEST_banner_ad_unit_id)
            setAdSize(AdSize.MEDIUM_RECTANGLE)
            loadAd(AdRequest.Builder().build())
        }
    }

    private fun showExitDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_exit, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // 미리 로드된 배너를 다이얼로그에 부착 (재사용을 위해 기존 부모에서 분리)
        exitAdView?.let { ad ->
            (ad.parent as? ViewGroup)?.removeView(ad)
            dialogView.findViewById<FrameLayout>(R.id.exitAdContainer).addView(ad)
        }

        dialogView.findViewById<Button>(R.id.exitButton).setOnClickListener {
            dialog.dismiss()
            finish()
        }

        dialogView.findViewById<Button>(R.id.cancelExitButton).setOnClickListener {
            dialog.dismiss()
        }

        // 닫힐 때 배너를 떼어내 다음 표시 때 재사용
        dialog.setOnDismissListener {
            exitAdView?.let { ad -> (ad.parent as? ViewGroup)?.removeView(ad) }
        }

        dialog.show()
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        
        InterstitialAd.load(this, getString(R.string.TEST_FULLSCREEN_ad_unit_id), adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                mInterstitialAd = interstitialAd
                Log.d("TAG", "Interstitial ad loaded")
            }
            
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.d("TAG", "Interstitial ad failed to load: ${loadAdError.message}")
                mInterstitialAd = null
            }
        })
    }
    
    private fun showInterstitialAd() {
        if (mInterstitialAd != null) {
            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("TAG", "Ad was dismissed")
                    loadInterstitialAd() // 광고가 닫힌 후 새 광고 로드
                }
                
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.d("TAG", "Ad failed to show: ${adError.message}")
                    loadInterstitialAd() // 광고 표시 실패 시 새 광고 로드
                }
                
                override fun onAdShowedFullScreenContent() {
                    Log.d("TAG", "Ad showed fullscreen content")
                    mInterstitialAd = null // 광고가 표시되면 참조 해제
                }
            }
            
            mInterstitialAd?.show(this)
        } else {
            Log.d("TAG", "Interstitial ad is not ready yet")
            loadInterstitialAd() // 광고가 준비되지 않았으면 다시 로드
        }
    }
}