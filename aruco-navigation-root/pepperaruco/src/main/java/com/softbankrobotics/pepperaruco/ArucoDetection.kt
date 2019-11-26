package com.softbankrobotics.pepperaruco

import android.graphics.Bitmap
import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.Promise
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.AttachedFrame
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.`object`.actuation.FreeFrame
import com.aldebaran.qi.sdk.`object`.actuation.LookAtMovementPolicy
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.`object`.geometry.Vector3
import com.aldebaran.qi.sdk.builder.LookAtBuilder
import com.aldebaran.qi.sdk.builder.TakePictureBuilder
import com.aldebaran.qi.sdk.builder.TransformBuilder
import com.softbankrobotics.pepperaruco.util.*
import com.softbankrobotics.pepperaruco.util.LookAtUtils.detectWhenLookingAtFrame
import com.softbankrobotics.pepperaruco.util.LookAtUtils.lookAtTransformFromSphericalCoord
import com.softbankrobotics.pepperaruco.util.OpenCVUtils.createMat
import org.opencv.android.Utils
import org.opencv.aruco.Aruco
import org.opencv.aruco.Aruco.*
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.concurrent.atomic.AtomicBoolean

data class LookAroundConfig(
    var lookAtPolicy: LookAtMovementPolicy = LookAtMovementPolicy.HEAD_ONLY,
    var lookAtTransform: List<Transform> = doubleArrayOf(-90.0, -60.0, -30.0, 0.0, 30.0, 60.0, 90.0)
        .map{ lookAtTransformFromSphericalCoord(
            SphericalCoord(
                1.0,
                Math.toRadians(90.0),
                Math.toRadians(it)
            )
        ) }
)

enum class ArucoDictionary(val value: Int) {
    DICT_4X4_50(Aruco.DICT_4X4_50),
    DICT_4X4_100(Aruco.DICT_4X4_100),
    DICT_4X4_250(Aruco.DICT_4X4_250),
    DICT_4X4_1000(Aruco.DICT_4X4_1000),
    DICT_5X5_50(Aruco.DICT_5X5_50),
    DICT_5X5_100(Aruco.DICT_5X5_100),
    DICT_5X5_250(Aruco.DICT_5X5_250),
    DICT_5X5_1000(Aruco.DICT_5X5_1000),
    DICT_6X6_50(Aruco.DICT_6X6_50),
    DICT_6X6_100(Aruco.DICT_6X6_100),
    DICT_6X6_250(Aruco.DICT_6X6_250),
    DICT_6X6_1000(Aruco.DICT_6X6_1000),
    DICT_7X7_50(Aruco.DICT_7X7_50),
    DICT_7X7_100(Aruco.DICT_7X7_100),
    DICT_7X7_250(Aruco.DICT_7X7_250),
    DICT_7X7_1000(Aruco.DICT_7X7_1000),
    DICT_ARUCO_ORIGINAL(Aruco.DICT_ARUCO_ORIGINAL),
    DICT_APRILTAG_16h5(Aruco.DICT_APRILTAG_16h5),
    DICT_APRILTAG_25h9(Aruco.DICT_APRILTAG_25h9),
    DICT_APRILTAG_36h10(Aruco.DICT_APRILTAG_36h10),
    DICT_APRILTAG_36h11(Aruco.DICT_APRILTAG_36h11),
}

data class DetectArucoConfig(
    var markerLength: Double = 0.15, // Aruco markers real size in meter
    var dictionary: ArucoDictionary = ArucoDictionary.DICT_4X4_50,
    var cameraMatrix: HashMap<Pair<Int, Int>, DoubleArray> = PepperHeadCamera.matrix,
    var distortionCoefs: DoubleArray = PepperHeadCamera.distortionCoefs
)

interface OnArucoMarkerDetectedListerner {
    fun onArucoMarkerDetected(arucoMarker: ArucoMarker, detectionData: ArucoMarkerDetectionData)
}

class ArucoDetection private constructor(val qiContext: QiContext) {

    val markers = mutableMapOf<Int, ArucoMarker>()

    private fun getOrCreateMarker(id: Int, image: Bitmap, corners: Mat, tvec: DoubleArray, rvec: DoubleArray, timestamp: Long): Future<ArucoMarker> {
        val detectionData = ArucoMarkerDetectionData(image, corners, tvec, rvec, timestamp)
        val gazeToMarker = computeGazeToMarkerTransform(tvec, rvec)
        val marker = markers.get(id)
        if (marker == null) {
            return computeMarkerToMapOrigin(gazeToMarker, timestamp).andThenCompose { markerToMap ->
                createFrameAttachedToMap(markerToMap.inverse()).andThenCompose { markerAttachedFrame ->
                    markerAttachedFrame.async().frame().andThenApply { markerFrame ->
                        val m = ArucoMarker(
                            id,
                            markerFrame,
                            mutableListOf(detectionData),
                            ArucoMarkerMappingData(markerToMap, markerAttachedFrame)
                        )
                        markers[id] = m
                        callOnArucoMarkerDetectedListerners(m, detectionData)
                        m
                    }
                }
            }
        } else {
            return updateDriftFromMarker(
                marker.mappingData.markerToMapOrigin,
                gazeToMarker,
                timestamp
            ).andThenApply {
                callOnArucoMarkerDetectedListerners(marker, detectionData)
                marker
            }
        }
    }

    private fun computeGazeToMarkerTransform(tvec: DoubleArray, rvec: DoubleArray): Transform {
        // Switch from opencv to naoqi coordinate system: gazeFrame (x, y, z) == OpenCV cameraAxis (z, -x, -y)
        val cameraToArucoTranslation = Vector3(tvec[2], -tvec[0], -tvec[1])
        val wx = rvec[2]
        val wy = -rvec[0]
        val wz = -rvec[1]
        // Usefull converter:
        // - https://www.andre-gaschler.com/rotationconverter/
        // Theta: angle of rotation camera to target
        val theta = Math.sqrt(wx*wx+wy*wy+wz*wz)
        // Vector of rotation camera to target: (wx / theta, wy / theta, wz / theta)
        val cameraToArucoRotation = Rotation(wx / theta, wy / theta, wz / theta, theta).toQuaternion()

        val cameraToArucoTransform = Transform(cameraToArucoRotation, cameraToArucoTranslation)
        val gazeToArucoTransform = PepperHeadCamera.gazeToCameraTransform * cameraToArucoTransform

        return gazeToArucoTransform
    }

    private val onDetectedListeners: MutableList<OnArucoMarkerDetectedListerner> = mutableListOf()

    fun addOnArucoMarkerDetectedListener(listener: OnArucoMarkerDetectedListerner) {
        onDetectedListeners.add(listener)
    }

    fun removeOnArucoMarkerDetectedListerner(listener: OnArucoMarkerDetectedListerner) {
        onDetectedListeners.remove(listener)
    }

    fun removeAllOnArucoMarkerDetectedListeners() {
        onDetectedListeners.clear()
    }

    private fun callOnArucoMarkerDetectedListerners(marker: ArucoMarker, data: ArucoMarkerDetectionData) {
        onDetectedListeners.forEach { listener ->
            listener.onArucoMarkerDetected(marker, data)
        }
    }

    fun detectMarkerWithTopCamera(config: DetectArucoConfig = DetectArucoConfig()): Future<Set<ArucoMarker>> {
        val promise = Promise<Set<ArucoMarker>>()
        TakePictureBuilder.with(qiContext).buildAsync().thenConsume { takePictureFuture ->
            if (takePictureFuture.hasError())
                promise.setError(takePictureFuture.errorMessage)
            else {
                takePictureFuture.value.async().run().andThenConsume { timestampedImageHandle ->
                    val picture = timestampedImageHandle.getBitmap()
                    Log.i(TAG, "Top camera width: ${picture.width} height: ${picture.height}")
                    val mat = Mat(picture.width, picture.height, CvType.CV_8UC3)
                    val tempPicture = picture.copy(Bitmap.Config.RGB_565, true)
                    Utils.bitmapToMat(tempPicture, mat)
                    Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2BGR)
                    val corners = mutableListOf<Mat>()
                    val ids = Mat()
                    detectMarkers(mat, getPredefinedDictionary(config.dictionary.value), corners, ids)
                    val rvecs = Mat()
                    val tvecs = Mat()
                    val cameraMatrix = config.cameraMatrix[Pair(picture.width, picture.height)]
                    if (cameraMatrix == null) {
                        promise.setError("No camera matrix found for image with resolution ${picture.width}x${picture.height}")
                    }
                    else {
                        estimatePoseSingleMarkers(
                            corners,
                            config.markerLength.toFloat(),
                            createMat(3, 3, *cameraMatrix),
                            createMat(1, 8, *config.distortionCoefs),
                            rvecs,
                            tvecs
                        )
                        val markerSet = mutableSetOf<ArucoMarker>()

                        val futureList = (0 until ids.rows()).map { i ->
                            val id = ids[i, 0][0].toInt()
                            val corner = corners[i]
                            Log.d(TAG, "Found marker $id")
                            Log.d(TAG, "Marker $id corners: ${corner.dump()}")

                            getOrCreateMarker(
                                id, picture, corner, tvecs[i, 0], rvecs[i, 0],
                                timestampedImageHandle.time).andThenApply { marker ->
                                if (!markerSet.contains(marker))
                                    markerSet.add(marker)
                            }
                        }
                        Future.waitAll(*futureList.toTypedArray()).andThenConsume { promise.setValue(markerSet) }
                    }
                }
            }
        }
        return promise.future
    }

    fun lookAroundForMarker(lookAroundConfig: LookAroundConfig = LookAroundConfig(),
                            detectArucoConfig: DetectArucoConfig = DetectArucoConfig()): Future<Set<ArucoMarker>> {
        val promise = Promise<Set<ArucoMarker>>()
        qiContext.actuation.async().robotFrame().andThenConsume { robotFrame ->
            val firstLookAtTransform = lookAroundConfig.lookAtTransform.getOrNull(0)
            if (firstLookAtTransform == null) {
                promise.setError("lookAtTransform is Empty")
            } else {
                var markerSet = setOf<ArucoMarker>()
                var shouldStop = false
                // Make lookAt
                val refGazeFrame = LookAtUtils.alignedGazeFrame(qiContext, robotFrame)
                val targetFrame = qiContext.mapping.makeFreeFrame()
                targetFrame.update(refGazeFrame, firstLookAtTransform, 0)
                val lookAt = LookAtBuilder.with(qiContext).withFrame(targetFrame.frame()).build()
                lookAt.policy = lookAroundConfig.lookAtPolicy
                val lookAtFuture = lookAt.async().run()
                // Make detecting
                var detecting = detectWhenLookingAtFrame(qiContext, targetFrame.frame()).thenCompose {
                    detectMarkerWithTopCamera(detectArucoConfig)
                }
                var isFirst = true
                lookAroundConfig.lookAtTransform.forEach { lookAtTransform ->
                    if (!isFirst) { // We skip the first point, we already handled it.
                        detecting = detecting.thenCompose { detectedSoFarFuture ->
                            if (shouldStop)
                                detectedSoFarFuture
                            else {
                                if (detectedSoFarFuture.isSuccess && detectedSoFarFuture.value.isNotEmpty()) {
                                    markerSet = markerSet union detectedSoFarFuture.value
                                }
                                targetFrame.update(refGazeFrame, lookAtTransform, 0)
                                detectWhenLookingAtFrame(qiContext, targetFrame.frame()).thenCompose {
                                    detectMarkerWithTopCamera(detectArucoConfig)
                                }
                            }
                        }
                    }
                    isFirst = false
                }
                detecting.thenConsume { detectedFuture ->
                    lookAtFuture.cancel(true)
                    if (detectedFuture.hasError())
                        promise.setError(detectedFuture.errorMessage)
                    else {
                        if (detectedFuture.isSuccess && detectedFuture.value.isNotEmpty()) {
                            markerSet = markerSet union detectedFuture.value
                        }
                        promise.setValue(markerSet)
                    }
                }
                promise.setOnCancel {
                    shouldStop = true
                    lookAtFuture.requestCancellation()
                    detecting.requestCancellation()
                }
            }
        }

        return promise.future
    }

    /************************************************
     *
     *         Mapping & automatic drift correction
     *
     ************************************************/

    private val mapOrigin: Future<FreeFrame> by lazy {
        qiContext.actuation.async().robotFrame().andThenCompose { robotFrame ->
            qiContext.mapping.async().makeFreeFrame().andThenCompose { freeFrame ->
                freeFrame.async().update(robotFrame, TransformBuilder.create().fromXTranslation(0.0), 0).andThenApply { freeFrame }
            }
        }
    }

    private fun computeMarkerToMapOrigin(gazeToMarker: Transform, timestamp: Long): Future<Transform> {
        return qiContext.actuation.async().gazeFrame().andThenCompose { gazeFrame ->
            mapOrigin.andThenCompose { mapOriginFreeFrame ->
                mapOriginFreeFrame.async().frame().andThenCompose { mapOriginFrame ->
                    qiContext.mapping.async().makeFreeFrame()
                        .andThenCompose { gazeFreeFrameAtTimestamp ->
                            gazeFreeFrameAtTimestamp.async().update(
                                gazeFrame,
                                TransformBuilder.create().fromXTranslation(0.0),
                                timestamp
                            ).andThenCompose {
                                gazeFreeFrameAtTimestamp.async().frame().andThenCompose { gazeFrameAtTimestamp ->
                                    gazeFrameAtTimestamp.async().computeTransform(mapOriginFrame).andThenApply { mapToGaze ->
                                        (mapToGaze.transform * gazeToMarker).inverse()
                                    }
                                }
                            }
                        }
                }
            }
        }
    }

    private fun updateDriftFromMarker(markerToMapOrigin: Transform, gazeToMarker: Transform, timestamp: Long): Future<Void> {
        return qiContext.actuation.async().gazeFrame().andThenCompose { gazeFrame ->
            mapOrigin.andThenCompose { mapOrigin ->
                val gazeToMap = gazeToMarker * markerToMapOrigin
                mapOrigin.async().update(gazeFrame, gazeToMap, timestamp)
            }
        }
    }

    private fun createFrameAttachedToMap(transform: Transform): Future<AttachedFrame> {
        return mapOrigin.andThenCompose { mapOrigin ->
            mapOrigin.async().frame().andThenCompose { mapOriginFrame ->
                mapOriginFrame.async().makeAttachedFrame(transform)
            }
        }
    }

    companion object {
        private lateinit var INSTANCE: ArucoDetection
        private val initialized = AtomicBoolean()
        fun instance(qiContext: QiContext): ArucoDetection {
            if(!initialized.getAndSet(true)) {
                INSTANCE = ArucoDetection(qiContext)
            }
            else if (qiContext != INSTANCE.qiContext) {
                INSTANCE = ArucoDetection(qiContext)
            }
            return INSTANCE
        }
    }
}

val QiContext.arucoDetection: ArucoDetection
    get() {
        return ArucoDetection.instance(this)
    }

/**
 * Create a detached frame (free frame), from a base frame and an optional transform.
 * @param base The base frame from which to create the new frame
 * @param transform (Optional) Transform to apply to the base frame to create the new detached frame.
 * @return A new Frame, corresponding to the transform applied to the base frame.
 */
fun QiContext.makeDetachedFrame(base: Frame, transform: Transform = TransformBuilder.create().fromXTranslation(0.0)): Future<Frame> {
    return mapping.async().makeFreeFrame().andThenCompose { freeFrame ->
        freeFrame.async().update(base, transform, 0).andThenCompose {
            freeFrame.async().frame()
        }
    }
}