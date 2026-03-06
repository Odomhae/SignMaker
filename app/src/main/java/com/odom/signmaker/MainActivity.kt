package com.odom.signmaker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.graphics.Bitmap
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
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.gcacace.signaturepad.views.SignaturePad
import com.google.android.gms.ads.AdRequest
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
import com.odom.signmaker.databinding.ActivityMainBinding
import me.jfenn.colorpickerdialog.dialogs.ColorPickerDialog
import me.jfenn.colorpickerdialog.views.picker.RGBPickerView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class MainActivity : AppCompatActivity() {
    private lateinit var binding : ActivityMainBinding
    lateinit var signBitmap : Bitmap

    // 뒤로가기 2번 종료
    var backPressTime = 0L
    private val callback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // 뒤로가기 클릭 시 실행시킬 코드
            if (System.currentTimeMillis() - backPressTime > 2000){
                backPressTime = System.currentTimeMillis()
                Toast.makeText(this@MainActivity, R.string.alert_press_close , Toast.LENGTH_SHORT).show()
            } else {
                finish()
            }
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
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root

        setContentView(view)

        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("SignMakerPrefs", Context.MODE_PRIVATE)

        checkPermission()

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
            ColorPickerDialog()
                .withTitle(resources.getString(R.string.select_pen_color))
                .clearPickers()
                .withPicker(RGBPickerView::class.java)
                .withAlphaEnabled(false)
                .withColor(resources.getColor(R.color.black)) // the default / initial color
                .withListener { dialog, color ->
                    binding.signaturePad.setPenColor(color)
                    //todo 230720
//                    binding.tvPencolor.setTextColor((resources.getColor(color)))
//                    defaultPenColor = color
                }
                .show(supportFragmentManager, "colorPicker")
        }

    }

    override fun onStart() {
        super.onStart()

        // load Banner AD
        MobileAds.initialize(this) {}
        mAdView = findViewById(R.id.adMobView)
        val adRequest = AdRequest.Builder().build()
        mAdView.loadAd(adRequest)
        
        // load Interstitial AD
        loadInterstitialAd()
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

           // Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show()
            reviewApp()

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
            
            if (signatureCount % 3 == 0) {
                showInterstitialAd()
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