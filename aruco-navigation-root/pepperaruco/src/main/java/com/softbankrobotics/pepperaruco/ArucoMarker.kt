package com.softbankrobotics.pepperaruco

import android.graphics.Bitmap
import com.aldebaran.qi.sdk.`object`.actuation.AttachedFrame
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import org.opencv.core.Mat

data class ArucoMarkerDetectionData(
    val image: Bitmap,/** The camera image of the Aruco marker */
    val corners: Mat,/** The corners of the marker in the image, as returned by OpenCV */
    val tvec: DoubleArray,
    val rvec: DoubleArray,
    val timestamp: Long /** The time when the Aruco was detected  */
)

data class ArucoMarkerMappingData(
    val markerToMapOrigin: Transform,
    val markerAttachedFrame: AttachedFrame
)

class ArucoMarker(
    val id: Int,/** The number Id of the Aruco marker. */
    val frame: Frame,/** The frame of the Aruco marker */
    val detectionData: MutableList<ArucoMarkerDetectionData> = mutableListOf(),
    internal val mappingData: ArucoMarkerMappingData
)