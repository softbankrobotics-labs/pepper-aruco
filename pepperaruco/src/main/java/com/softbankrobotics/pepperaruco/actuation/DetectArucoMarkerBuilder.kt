package com.softbankrobotics.pepperaruco.actuation

import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.util.FutureUtils


class DetectArucoMarkerBuilder private constructor(val qiContext: QiContext) {

    private val config = DetectArucoMarker.Config()

    fun withMarkerLength(markerLength: Double): DetectArucoMarkerBuilder {
        this.config.markerLength = markerLength
        return this
    }

    fun withDictionary(dictionary: ArucoDictionary): DetectArucoMarkerBuilder {
        this.config.dictionary = dictionary
        return this
    }

    fun withCameraMatrix(cameraMatrix: HashMap<Pair<Int, Int>, DoubleArray>): DetectArucoMarkerBuilder {
        this.config.cameraMatrix = cameraMatrix
        return this
    }

    fun withDistortionCoefs(distortionCoefs: DoubleArray): DetectArucoMarkerBuilder {
        this.config.distortionCoefs = distortionCoefs
        return this
    }

    fun withArucoMarkerFrameLocalizationPolicy(policy: ArucoMarkerFrameLocalizationPolicy): DetectArucoMarkerBuilder {
        this.config.localizationPolicy = policy
        return this
    }

    companion object {
        fun with(qiContext: QiContext): DetectArucoMarkerBuilder {
            return DetectArucoMarkerBuilder(qiContext)
        }
    }

    fun build(): DetectArucoMarker {
        return FutureUtils.get(buildAsync())
    }

    fun buildAsync(): Future<DetectArucoMarker> {
        return Future.of(DetectArucoMarker(qiContext, config))
    }
}