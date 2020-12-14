package com.softbankrobotics.pepperaruco.actuation

import android.graphics.Bitmap
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.`object`.geometry.Quaternion
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.`object`.geometry.Vector3
import com.beust.klaxon.Converter
import com.beust.klaxon.JsonObject
import com.beust.klaxon.JsonValue
import com.beust.klaxon.Klaxon
import com.softbankrobotics.dx.pepperextras.actuation.getFrameAtTimestamp
import com.softbankrobotics.dx.pepperextras.actuation.makeDetachedFrame
import com.softbankrobotics.dx.pepperextras.geometry.times
import com.softbankrobotics.dx.pepperextras.geometry.toQiQuaternion
import com.softbankrobotics.dx.pepperextras.util.await
import org.apache.commons.math3.geometry.euclidean.threed.Rotation
import org.apache.commons.math3.geometry.euclidean.threed.RotationConvention
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D
import org.opencv.core.Mat


data class ArucoMarker internal constructor(
        val id: Int,/** The number Id of the Aruco marker. */
        val frame: Frame,/** The frame of the Aruco marker */
        val detectionConfig: DetectionConfig,
        var detectionData: DetectionData? = null
) {
    data class DetectionConfig(
            val markerLength: Double, // Aruco markers real size in meter
            val dictionary: ArucoDictionary,
            val cameraMatrix: HashMap<Pair<Int, Int>, DoubleArray>,
            val distortionCoefs: DoubleArray,
            val localizationPolicy: ArucoMarkerFrameLocalizationPolicy)

    data class DetectionData(
            val image: Bitmap,/** The camera image of the Aruco marker */
            val corners: Mat,/** The corners of the marker in the image, as returned by OpenCV */
            val tvec: DoubleArray,
            val rvec: DoubleArray,
            val timestamp: Long /** The time when the Aruco was detected  */
    )

    public fun serialize(qiContext: QiContext): String {
        if (detectionConfig.localizationPolicy
                != ArucoMarkerFrameLocalizationPolicy.ATTACHED_TO_MAPFRAME)
            throw RuntimeException("Marker serialization with localizationPolicy " +
                    "${detectionConfig.localizationPolicy} is not supported.")
        val serializer = Klaxon().converter(KlaxonConverter(qiContext))
        return serializer.toJsonString(this)
    }

    companion object {

        internal suspend fun create(qiContext: QiContext, id: Int,
                            localizationPolicy: ArucoMarkerFrameLocalizationPolicy,
                            image: Bitmap, corners: Mat, tvec: DoubleArray, rvec: DoubleArray,
                            timestamp: Long, markerLength: Double, dictionary: ArucoDictionary,
                            cameraMatrix: HashMap<Pair<Int, Int>, DoubleArray>,
                            distortionCoefs: DoubleArray): ArucoMarker {
            val detectionData = DetectionData(image, corners, tvec, rvec, timestamp)
            val detectionConfig = DetectionConfig(markerLength, dictionary, cameraMatrix,
                    distortionCoefs, localizationPolicy)
            val gazeToMarker = computeGazeToMarkerTransform(tvec, rvec)
            val gazeFrame = qiContext.actuation.async().gazeFrame().await()
            val markerFrame = when (localizationPolicy) {
                ArucoMarkerFrameLocalizationPolicy.DETACHED -> {
                    qiContext.mapping.async()
                            .makeDetachedFrame(gazeFrame, gazeToMarker, timestamp).await()
                }
                ArucoMarkerFrameLocalizationPolicy.ATTACHED_TO_MAPFRAME -> {
                    val mapToMarker = computeMapFrameToMarker(qiContext, gazeToMarker, timestamp)
                    createFrameAttachedToMap(qiContext, mapToMarker)
                }
            }
            return ArucoMarker(id, markerFrame, detectionConfig, detectionData)
        }

        private fun computeGazeToMarkerTransform(tvec: DoubleArray, rvec: DoubleArray): Transform {
            val cameraToArucoTranslation = Vector3(tvec[2], -tvec[0], -tvec[1])
            val wx = rvec[2]
            val wy = -rvec[0]
            val wz = -rvec[1]
            // Usefull converter:
            // - https://www.andre-gaschler.com/rotationconverter/
            // Theta: angle of rotation camera to target
            val theta = Math.sqrt(wx * wx + wy * wy + wz * wz)
            // Vector of rotation camera to target: (wx / theta, wy / theta, wz / theta)
            val cameraToArucoRotation = Rotation(Vector3D(wx / theta, wy / theta, wz / theta), theta, RotationConvention.FRAME_TRANSFORM)
            val rotation180AroundX = Quaternion(1.0, 0.0, 0.0, 0.0)
            val correctedRotation = (cameraToArucoRotation.toQiQuaternion() * rotation180AroundX)
            val cameraToArucoTransform = Transform(correctedRotation, cameraToArucoTranslation)
            val gazeToArucoTransform = PepperRobotData.GAZE_TO_HEAD_CAMERA_TRANSFORM * cameraToArucoTransform
            return gazeToArucoTransform
        }

        private suspend fun computeMapFrameToMarker(qiContext: QiContext, gazeToMarker: Transform,
                                                    timestamp: Long): Transform {
            val gazeFrame = qiContext.actuation.async().gazeFrame().await()
            val gazeFrameAtTimestamp = qiContext.mapping.async()
                    .getFrameAtTimestamp(gazeFrame, timestamp).await()
            val mapFrame = qiContext.mapping.async().mapFrame().await()
            val mapToGaze = gazeFrameAtTimestamp.async().computeTransform(mapFrame).await()
            return mapToGaze.transform * gazeToMarker
        }

        private suspend fun createFrameAttachedToMap(qiContext: QiContext,
                                                     transform: Transform): Frame {
            return qiContext.mapping.async().mapFrame().await()
                    .async().makeAttachedFrame(transform).await()
                    .async().frame().await()
        }
    }

    internal class KlaxonConverter(val qiContext: QiContext): Converter {

        override fun canConvert(cls: Class<*>): Boolean {
            return cls == ArucoMarker::class.java
        }

        override fun toJson(value: Any): String {
            return when (value) {
                is ArucoMarker -> {
                    "{\"detectionConfig\" : {" +
                            "\"cameraMatrix\" : ${cameraMatrixToJson(value.detectionConfig.cameraMatrix)}, " +
                            "\"dictionary\" : \"${value.detectionConfig.dictionary}\", " +
                            "\"distortionCoefs\" : [${value.detectionConfig.distortionCoefs.joinToString()}], " +
                            "\"localizationPolicy\" : \"${value.detectionConfig.localizationPolicy}\", " +
                            "\"markerLength\" : ${value.detectionConfig.markerLength} " +
                            "}, " +
                            "\"frame\" : ${frameToJson(value.frame)}, " +
                            "\"id\" : ${value.id} " +
                            "}"
                }
                else -> ""
            }
        }

        override fun fromJson(jv: JsonValue): Any {
            return when (jv.propertyClass) {
                ArucoMarker::class.java -> {
                    val config = (jv.obj?.get("detectionConfig") ?: error("detectionConfig")) as JsonObject
                    val id = jv.objInt("id")
                    val frame = frameFromJson(jv.obj!!)
                    val markerLength = config.double("markerLength") ?: error("markerLength")
                    val dictionary = ArucoDictionary.valueOf(
                            config.string("dictionary") ?: error("dictionary"))
                    val cameraMatrix = cameraMatrixFromJson(config)
                    val distortionCoefs = config.array<Double>("distortionCoefs")
                            ?.toDoubleArray() ?: error("distortionCoefs")
                    val localizationPolicy = ArucoMarkerFrameLocalizationPolicy.valueOf(
                            config.string("localizationPolicy") ?: error("localizationPolicy"))
                    ArucoMarker(id, frame, ArucoMarker.DetectionConfig(markerLength,
                            dictionary, cameraMatrix, distortionCoefs, localizationPolicy))
                }
                else -> Any()
            }
        }

        private fun frameToJson(f: Frame): String {
            val t = f.computeTransform(qiContext.mapping.mapFrame()).transform
            val translation = t.translation.run {  "{ \"x\": ${x}, \"y\": ${y}, \"z\": ${z} }" }
            val rotation = t.rotation.run {  "{ \"w\": ${w}, \"x\": ${x}, \"y\": ${y}, \"z\": ${z} }" }
            return "{ \"translation\": $translation, \"rotation\": $rotation }"
        }

        private fun frameFromJson(obj: JsonObject): Frame {
            val transform = obj.obj("frame")?.let { frame ->
                val translation = frame.obj("translation")?.let { t ->
                    Vector3(t.double("x") ?: error("x"),
                            t.double("y") ?: error("y"),
                            t.double("z") ?: error("z"))
                } ?: error("translation")
                val rotation = frame.obj("rotation")?.let { r ->
                    Quaternion(r.double("x") ?: error("x"),
                            r.double("y") ?: error("y"),
                            r.double("z") ?: error("z"),
                            r.double("w") ?: error("w"))
                } ?: error("rotation")
                Transform(rotation, translation)
            } ?: error("frame")
            return qiContext.mapping.mapFrame().makeAttachedFrame(transform).frame()
        }

        private fun cameraMatrixToJson(m: HashMap<Pair<Int, Int>, DoubleArray>): String {
            return "[" + m.map {
                "{ \"resolution\": { \"width\": ${it.key.first}, \"height\": ${it.key.second} }, " +
                        "\"matrix\": [${it.value.joinToString()}] }"
            }.joinToString() + "]"
        }

        private fun cameraMatrixFromJson(config: JsonObject): HashMap<Pair<Int, Int>, DoubleArray> {
            val res = hashMapOf<Pair<Int, Int>, DoubleArray>()
            config.array<JsonObject>("cameraMatrix")?.map {
                val resolution = (it.get("resolution")?: error("resolution")) as JsonObject
                val key = Pair(
                        resolution.int("width") ?: error("width"),
                        resolution.int("height") ?: error("height"))
                val value = it.array<Double>("matrix")?.toDoubleArray() ?: error("matrix")
                res[key] = value
            } ?: error("cameraMatrix")
            return res
        }

        private fun error(field: String): Nothing {
            throw RuntimeException("Deserialization failed, '$field' field is missing")
        }
    }
}

