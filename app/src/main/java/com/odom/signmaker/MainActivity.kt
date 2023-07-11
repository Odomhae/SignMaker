package com.odom.signmaker

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.gcacace.signaturepad.views.SignaturePad
import com.odom.signmaker.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class MainActivity : AppCompatActivity() {
    private lateinit var binding : ActivityMainBinding

    var permission_list = arrayOf(
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root

        setContentView(view)

        binding.signaturePad.setOnSignedListener(object : SignaturePad.OnSignedListener {
            override fun onStartSigning() {
                binding.btSave.isClickable = false
            }
            override fun onSigned() {
                binding.btSave.isClickable = true
            }

            override fun onClear() {
                binding.btSave.isClickable = false
            }
        })

        binding.btRedraw.setOnClickListener {
            binding.signaturePad.clear()
        }

        binding.btSave.setOnClickListener {

            val signBitmap = binding.signaturePad.signatureBitmap

            checkPermission()

            val directory = File(Environment.getExternalStorageDirectory(), "SignMaker")
            directory.mkdirs()

            val fileName =  System.currentTimeMillis().toString() + ".png"
            val file = File(directory, fileName)

            try {
                val outputStream = FileOutputStream(file)
                signBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
                outputStream.close()

                // Notify the media scanner about the new image
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                val contentUri = Uri.fromFile(file)
                mediaScanIntent.data = contentUri
                sendBroadcast(mediaScanIntent)

                Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
            }

        }

    }

    fun checkPermission() {
        for (permission in permission_list) {
            //권한 허용 여부를 확인한다.
            val chk = checkCallingOrSelfPermission(permission)
            if (chk == PackageManager.PERMISSION_DENIED) {
                //권한 허용을여부를 확인하는 창을 띄운다
                requestPermissions(permission_list, 0)
            }
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 0) {
            for (i in grantResults.indices) {
                //허용됬다면
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {

                } else {
                    Toast.makeText(applicationContext, "앱 권한 설정하세요", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }


}