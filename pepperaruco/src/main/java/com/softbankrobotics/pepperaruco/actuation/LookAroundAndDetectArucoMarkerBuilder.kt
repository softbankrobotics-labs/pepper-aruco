package com.softbankrobotics.pepperaruco.actuation

import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.`object`.actuation.LookAtMovementPolicy
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.`object`.geometry.Vector3
import com.aldebaran.qi.sdk.builder.TransformBuilder
import com.aldebaran.qi.sdk.util.FutureUtils
import com.softbankrobotics.dx.pepperextras.actuation.makeDetachedFrame
import com.softbankrobotics.dx.pepperextras.geometry.IDENTITY_TRANSFORM
import com.softbankrobotics.dx.pepperextras.util.SingleThread
import com.softbankrobotics.dx.pepperextras.util.asyncFuture
import com.softbankrobotics.dx.pepperextras.util.await
import com.softbankrobotics.pepperaruco.actuation.PepperRobotData.GAZE_FRAME_Z
import kotlinx.coroutines.CoroutineName


class LookAroundAndDetectArucoMarkerBuilder private constructor(val qiContext: QiContext) {

    private val config = LookAroundAndDetectArucoMarker.Config()
    private val detectArucoMarkerConfig = DetectArucoMarker.Config()
    private var sphericalCoordinates: List<UnitSphericalCoord>? = null

    fun withMarkerLength(markerLength: Double): LookAroundAndDetectArucoMarkerBuilder {
        this.detectArucoMarkerConfig.markerLength = markerLength
        return this
    }

    fun withDictionary(dictionary: ArucoDictionary): LookAroundAndDetectArucoMarkerBuilder {
        this.detectArucoMarkerConfig.dictionary = dictionary
        return this
    }

    fun withCameraMatrix(cameraMatrix: HashMap<Pair<Int, Int>, DoubleArray>): LookAroundAndDetectArucoMarkerBuilder {
        this.detectArucoMarkerConfig.cameraMatrix = cameraMatrix
        return this
    }

    fun withDistortionCoefs(distortionCoefs: DoubleArray): LookAroundAndDetectArucoMarkerBuilder {
        this.detectArucoMarkerConfig.distortionCoefs = distortionCoefs
        return this
    }

    fun withArucoMarkerFrameLocalizationPolicy(policy: ArucoMarkerFrameLocalizationPolicy): LookAroundAndDetectArucoMarkerBuilder {
        this.detectArucoMarkerConfig.localizationPolicy = policy
        return this
    }

    fun withLookAtMovementPolicy(policy: LookAtMovementPolicy): LookAroundAndDetectArucoMarkerBuilder {
        this.config.lookAtMovementPolicy = policy
        return this
    }

    fun withLookAtTargetSequence(sequence: Sequence<LookAtTarget>): LookAroundAndDetectArucoMarkerBuilder {
        this.config.lookAtTargetSequence = sequence
        return this
    }

    fun withLookAtTargetFrame(vararg frame: Frame): LookAroundAndDetectArucoMarkerBuilder {
        config.lookAtTargetSequence = Sequence {
            object: Iterator<LookAtTarget> {
                val frames = frame.toMutableList()
                override fun hasNext(): Boolean {
                    return frames.isNotEmpty()
                }
                override fun next(): LookAtTarget {
                    return LookAtTarget(frames.removeAt(0), IDENTITY_TRANSFORM, 0)
                }
            }
        }
        return this
    }

    // Warning: make sure the reference frame does not move.
    // if it does and you don't want (like if you use gazeFrame), then use
    // qiContext.mapping.makeDetachedFrame to create a detached frame
    fun withLookAtReferenceFrameAndTransforms(lookAtRefFrame: Frame, lookAtTransforms: List<Transform>): LookAroundAndDetectArucoMarkerBuilder {
        val transforms = lookAtTransforms.toList()
        config.lookAtTargetSequence = object: Sequence<LookAtTarget> {
            override fun iterator(): Iterator<LookAtTarget> {
                return object: Iterator<LookAtTarget> {
                    val t = transforms.toMutableList()
                    override fun hasNext(): Boolean {
                        return t.isNotEmpty()
                    }
                    override fun next(): LookAtTarget {
                        return LookAtTarget(lookAtRefFrame, t.removeAt(0), 0)
                    }
                }
            }
        }
        return this
    }

    fun withTerminationCallback(terminationCallback: LookAroundAndDetectArucoMarker.TerminationCallback)
            : LookAroundAndDetectArucoMarkerBuilder {
        config.terminationCallback = terminationCallback
        return this
    }

    fun withArucoMarkerValidationCallback(callback: LookAroundAndDetectArucoMarker.ArucoMarkerValidationCallback)
            : LookAroundAndDetectArucoMarkerBuilder {
        config.markerValidationCallback = callback
        return this
    }

    private data class UnitSphericalCoord(
            val theta: Double, // polar (co-latitude) angle
            val phi: Double // azimuthal angle in x-y plane
    ) {
        val cartesian: Vector3

        init {
            val rphi = Math.toRadians(phi)
            val rtheta = Math.toRadians(theta)
            val sinTheta = Math.sin(rtheta)
            val x = sinTheta * Math.cos(rphi)
            val y = sinTheta * Math.sin(rphi)
            val z = Math.cos(rtheta)
            cartesian = Vector3(x, y, z)
        }
    }

    fun withLookAtSphericalCoordinates(vararg sphericalCoordinates: Pair<Double, Double>): LookAroundAndDetectArucoMarkerBuilder {
        this.sphericalCoordinates = sphericalCoordinates.map {
            UnitSphericalCoord(it.first, it.second)
        }
        return this
    }

    private fun makeReferenceFrameAndTransformFromSphericalCoordinates(): Future<Unit>
        = SingleThread.GlobalScope.asyncFuture(
            CoroutineName("LookAroundAndDetectArucoMarkerBuilder|MakeReferenceFrameFromSphericalCoordinates")) {
        if (sphericalCoordinates != null) {
            val refFrame = qiContext.mapping.async().makeDetachedFrame(
                    qiContext.actuation.async().robotFrame().await(),
                    TransformBuilder.create().fromTranslation(Vector3(0.0, 0.0, GAZE_FRAME_Z)))
                    .await()
            val transforms = sphericalCoordinates!!.map {
                TransformBuilder.create().fromTranslation(it.cartesian)
            }
            config.lookAtTargetSequence = Sequence {
                object: Iterator<LookAtTarget> {
                    val t = transforms.toMutableList()
                    override fun hasNext(): Boolean {
                        return t.isNotEmpty()
                    }
                    override fun next(): LookAtTarget {
                        return LookAtTarget(refFrame, t.removeAt(0), 0)
                    }
                }
            }
        }
    }

    companion object {
        fun with(qiContext: QiContext): LookAroundAndDetectArucoMarkerBuilder {
            return LookAroundAndDetectArucoMarkerBuilder(qiContext)
        }
    }

    fun build(): LookAroundAndDetectArucoMarker {
        return FutureUtils.get(buildAsync())
    }

    fun buildAsync(): Future<LookAroundAndDetectArucoMarker> {
        return makeReferenceFrameAndTransformFromSphericalCoordinates().andThenApply {
            LookAroundAndDetectArucoMarker(qiContext, config, detectArucoMarkerConfig)
        }
    }
}