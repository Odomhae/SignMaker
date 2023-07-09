package com.odom.signmaker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import java.io.ByteArrayOutputStream

object Utils {

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