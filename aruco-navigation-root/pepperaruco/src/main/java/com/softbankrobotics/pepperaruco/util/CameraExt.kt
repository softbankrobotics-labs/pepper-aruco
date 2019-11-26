package com.softbankrobotics.pepperaruco.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import com.aldebaran.qi.sdk.`object`.image.TimestampedImageHandle
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream


fun TimestampedImageHandle.getBitmap(): Bitmap {
    val encodedImageHandle = this.image
    val encodedImage = encodedImageHandle.value
    val buffer = encodedImage.data
    buffer.rewind()
    val pictureBufferSize = buffer.remaining()
    val pictureArray = ByteArray(pictureBufferSize)
    buffer.get(pictureArray)
    return BitmapFactory.decodeByteArray(pictureArray, 0, pictureBufferSize)
}

fun Bitmap.saveToExternalStorage(filename: String): Uri {
    val path = Environment.getExternalStorageDirectory().toString()
    val file = File(path, filename)
    try {
        val stream: OutputStream = FileOutputStream(file)
        this.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        stream.flush()
        stream.close()
    } catch (e: IOException){ // Catch the exception
        e.printStackTrace()
    }
    return Uri.parse(file.absolutePath)
}
