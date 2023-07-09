package com.odom.signmaker

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.odom.signmaker.Utils.getImageUri
import com.odom.signmaker.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {
    private lateinit var binding : ActivityMainBinding

    var permission_list = arrayOf<String>(
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root

        setContentView(view)

        binding.btRedraw.setOnClickListener {
            binding.signaturePad.clear()
        }

        binding.btSave.setOnClickListener {

            val intent = Intent(Intent.ACTION_SEND)

            val signBitmap = binding.signaturePad.signatureBitmap
            val signBitSvg = binding.signaturePad.signatureSvg

            checkPermission()

            val screenshotUri: Uri = Uri.parse(
                getImageUri(
                    this, signBitmap
                ).toString()
            )

            intent.type = ("image/*")
            intent.putExtra(Intent.EXTRA_STREAM, screenshotUri)
            startActivity(Intent.createChooser(intent, "Share image"))
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
                    Toast.makeText(this, "sazz" , Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(applicationContext, "앱 권한 설정하세요", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }


}