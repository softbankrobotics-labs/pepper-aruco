package com.softbankrobotics.pepperaruco.actuation

import android.graphics.Bitmap
import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.builder.TakePictureBuilder
import com.softbankrobotics.dx.pepperextras.actuation.internal.RunnableAction
import com.softbankrobotics.dx.pepperextras.image.toBitmap
import com.softbankrobotics.dx.pepperextras.util.TAG
import com.softbankrobotics.dx.pepperextras.util.asyncFuture
import com.softbankrobotics.dx.pepperextras.util.await
import com.softbankrobotics.pepperaruco.actuation.PepperRobotData.HEAD_CAMERA_DISTORTION_COEFS
import com.softbankrobotics.pepperaruco.actuation.PepperRobotData.HEAD_CAMERA_MATRIX
import com.softbankrobotics.pepperaruco.util.OpenCVUtils
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import org.opencv.android.Utils
import org.opencv.aruco.Aruco
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.lang.RuntimeException

class DetectArucoMarker internal constructor(
        private val qiContext: QiContext,
        private val config: Config): RunnableAction<Set<ArucoMarker>, DetectArucoMarker.Async>() {

    data class Config(
        var markerLength: Double = 0.15, // Aruco markers real size in meter
        var dictionary: ArucoDictionary = ArucoDictionary.DICT_4X4_50,
        var cameraMatrix: HashMap<Pair<Int, Int>, DoubleArray> = HEAD_CAMERA_MATRIX,
        var distortionCoefs: DoubleArray = HEAD_CAMERA_DISTORTION_COEFS,
        var localizationPolicy: ArucoMarkerFrameLocalizationPolicy = ArucoMarkerFrameLocalizationPolicy.DETACHED
    )

    override val _asyncInstance = Async()
    inner class Async internal constructor(): RunnableAction<Set<ArucoMarker>, DetectArucoMarker.Async>.Async() {

        override fun _run(scope: CoroutineScope): Future<Set<ArucoMarker>> = scope.asyncFuture(CoroutineName("$ACTION_NAME|_run")) {

            val takePicture = TakePictureBuilder.with(qiContext).buildAsync().await()
            val timestampedImageHandle = takePicture.async().run().await()
            val picture = timestampedImageHandle.image.async().value.await().toBitmap()
            //Log.i(TAG, "Top camera width: ${picture.width} height: ${picture.height}")
            val mat = Mat(picture.width, picture.height, CvType.CV_8UC3)
            val tempPicture = picture.copy(Bitmap.Config.RGB_565, true)
            Utils.bitmapToMat(tempPicture, mat)
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2BGR)
            val corners = mutableListOf<Mat>()
            val ids = Mat()

            Aruco.detectMarkers(mat, Aruco.getPredefinedDictionary(config.dictionary.value), corners, ids)

            val rvecs = Mat()
            val tvecs = Mat()
            val matrix = config.cameraMatrix[Pair(picture.width, picture.height)]
            if (matrix == null) {
                throw RuntimeException("No camera matrix found for image with resolution " +
                        "${picture.width}x${picture.height}")
            } else {
                Aruco.estimatePoseSingleMarkers(
                        corners,
                        config.markerLength.toFloat(),
                        OpenCVUtils.createMat(3, 3, *matrix),
                        OpenCVUtils.createMat(1, 8, *config.distortionCoefs),
                        rvecs,
                        tvecs
                )
                val markerSet = mutableSetOf<ArucoMarker>()

                for (i in 0..(ids.rows() - 1)) {
                    val id = ids[i, 0][0].toInt()
                    val corner = corners[i]
                    Log.d(TAG, "Found marker $id")
                    //Log.d(TAG, "Marker $id corners: ${corner.dump()}")
                    val arucoMarker = ArucoMarker.create(qiContext, id, config.localizationPolicy,
                            picture, corner, tvecs[i, 0], rvecs[i, 0], timestampedImageHandle.time,
                            config.markerLength, config.dictionary, config.cameraMatrix,
                            config.distortionCoefs)
                    markerSet.add(arucoMarker)
                }
                markerSet
            }
        }
    }
}