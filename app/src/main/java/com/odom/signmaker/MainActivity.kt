package com.odom.signmaker

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.odom.signmaker.databinding.ActivityMainBinding
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class MainActivity : AppCompatActivity() {
    private lateinit var binding : ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root

        setContentView(view)

        binding.btRedraw.setOnClickListener {
            Toast.makeText(this, "sef" , Toast.LENGTH_SHORT).show()
            binding.signaturePad.clear()
        }

        binding.btSave.setOnClickListener {

            val intent = Intent(Intent.ACTION_SEND)

            val signBitmap = binding.signaturePad.signatureBitmap
            val signBitSvg = binding.signaturePad.signatureSvg

            val screenshotUri: Uri = Uri.parse(
                getImageUri(
                    this,
                    signBitmap//viewToBitmap(binding.signaturePad)
                ).toString()
            )

            intent.type = ("image/*")
            intent.putExtra(Intent.EXTRA_STREAM, screenshotUri)
            startActivity(Intent.createChooser(intent, "Share image"))
        }


    }

    private fun saveImageExternal(image: Bitmap): Uri? {
        //TODO - Should be processed in another thread
        var uri: Uri? = null

        try {
            val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "to-share.png")
            val stream = FileOutputStream(file)
            image.compress(Bitmap.CompressFormat.PNG, 90, stream)
            stream.close()
            uri = Uri.fromFile(file)

        } catch (e: IOException) {
            Log.d("TAG", "IOException while trying to write file for sharing: " + e.message)
        }

        return uri
    }

    // 뷰를 비트맵으로 변환
    fun viewToBitmap(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        view.draw(canvas)
        return bitmap
    }

    // 비트맵 이미지 Uri 가져오기
    // 권한 설정 요 EXTERNAL_STORAGE
    fun getImageUri(context: Context, image: Bitmap): Uri? {
        val bytes = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.JPEG, 100, bytes)

        val path: String = MediaStore.Images.Media.insertImage(
            context.contentResolver, image, "Title", null )

        return Uri.parse(path)
    }
}