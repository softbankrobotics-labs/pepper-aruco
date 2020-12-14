package com.softbankrobotics.peppermapping.actuation.internal

import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.`object`.actuation.LookAtMovementPolicy
import com.aldebaran.qi.sdk.`object`.actuation.OrientationPolicy
import com.aldebaran.qi.sdk.`object`.geometry.Quaternion
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.`object`.geometry.Vector3
import com.aldebaran.qi.sdk.builder.TransformBuilder
import com.softbankrobotics.dx.pepperextras.actuation.ExtraLookAt
import com.softbankrobotics.dx.pepperextras.actuation.ExtraLookAtBuilder
import com.softbankrobotics.dx.pepperextras.actuation.StubbornGoToBuilder
import com.softbankrobotics.dx.pepperextras.actuation.distance
import com.softbankrobotics.dx.pepperextras.actuation.internal.RunnableAction
import com.softbankrobotics.dx.pepperextras.actuation.makeDetachedFrame
import com.softbankrobotics.dx.pepperextras.geometry.div
import com.softbankrobotics.dx.pepperextras.geometry.times
import com.softbankrobotics.dx.pepperextras.geometry.toQiQuaternion
import com.softbankrobotics.dx.pepperextras.util.asyncFuture
import com.softbankrobotics.dx.pepperextras.util.await
import com.softbankrobotics.pepperaruco.actuation.ArucoMarker
import com.softbankrobotics.pepperaruco.actuation.ArucoMarkerFrameLocalizationPolicy
import com.softbankrobotics.pepperaruco.actuation.LookAroundAndDetectArucoMarker
import com.softbankrobotics.pepperaruco.actuation.LookAroundAndDetectArucoMarkerBuilder
import com.softbankrobotics.pepperaruco.actuation.PepperRobotData
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import org.apache.commons.math3.geometry.euclidean.threed.Rotation
import org.apache.commons.math3.geometry.euclidean.threed.RotationConvention
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D

/**
 *  Go to a marker, regularly making stops to look at the marker.
 *  @return A future to a boolean, which will be equal to true if marker was seen at least once.
 */
class GoToMarkerAndCheckItsPositionOnTheWay internal constructor(
    private val qiContext: QiContext,
    val targetMarker: ArucoMarker,
    val config: Config)
    : RunnableAction<Pair<Boolean, ArucoMarker?>, GoToMarkerAndCheckItsPositionOnTheWay.Async>() {

    data class Config(
        var markerZAxisRotation: Double = 0.0,
        var alignWithMarker: Boolean = true,
        var maxSpeed: Float = 1.0f,
        var walkingAnimationEnabled: Boolean = true,
        var distanceBetweenStops: Double = 3.0
    )

    val MIN_MARKER_VIEWING_DISTANCE = 1.2 // Robot cannot look at marker closest to that distance

    override val _asyncInstance = Async()

    inner class Async internal constructor()
        : RunnableAction<Pair<Boolean, ArucoMarker?>, GoToMarkerAndCheckItsPositionOnTheWay.Async>.Async() {

        override fun _run(scope: CoroutineScope): Future<Pair<Boolean, ArucoMarker?>> = scope.asyncFuture(CoroutineName("$ACTION_NAME|_run")) {

            var detectedMarker: ArucoMarker = targetMarker
            var markerFound = false

            while (true) {
                val distanceToMarker = detectedMarker.frame.async()
                        .distance(qiContext.actuation.async().robotFrame().await()).await()
                Log.d(TAG, "Marker is at ${distanceToMarker} meters")

                // Try to detect marker if not too close
                if (distanceToMarker > MIN_MARKER_VIEWING_DISTANCE) {
                    Log.d(TAG, "Searching for marker ${targetMarker.id}.")
                    val markers = lookForMarker()
                    if (markers.isNotEmpty()) {
                        Log.d(TAG, "Marker was found")
                        markerFound = true
                        detectedMarker = markers.first()
                    } else
                        Log.d(TAG, "Marker was not found")
                } else
                    Log.d(TAG, "Marker too close to look at it.")

                // If marker is far, advance toward marker and loop
                if (distanceToMarker > config.distanceBetweenStops) {
                    val intermediateFrame = getFrameBetweenRobotAndMarker(detectedMarker, distanceToMarker)
                    Log.d(TAG, "Going to intermediate position in front of marker.")
                    val success = goToMarkerFrame(intermediateFrame, 10)
                    Log.d(TAG, "Position reached: $success")
                } else
                    break
            }
            // Once close enough, go to marker
            Log.d(TAG, "Trying to go to marker.")
            val success = goToMarkerFrame(
                    applyRotationToZAxis(detectedMarker.frame, config.markerZAxisRotation),
                    10
            )
            Log.d(TAG, "Position reached: $success")
            Pair(success, if (markerFound) detectedMarker else null)
        }

        private suspend fun goToMarkerFrame(markerFrame: Frame, maxRetry: Int): Boolean = coroutineScope {
            alignPepperHead(qiContext)
            // Rotate marker so that X comes to Z axis, so that robot align correctly
            val transformRotationY = TransformBuilder.create().fromRotation(Quaternion(0.0, -0.707, 0.0, 0.707))
            val freeFrame = qiContext.mapping.async().makeFreeFrame().await()
            freeFrame.async().update(markerFrame, transformRotationY, 0).await()
            val frame = freeFrame.async().frame().await()
            StubbornGoToBuilder.with(qiContext)
                    .apply {
                        if (config.alignWithMarker) withFinalOrientationPolicy(OrientationPolicy.ALIGN_X)
                    }.withFrame(frame)
                    .withWalkingAnimationEnabled(config.walkingAnimationEnabled)
                    .withMaxRetry(maxRetry)
                    .withMaxSpeed(config.maxSpeed)
                    .buildAsync().await()
                    .async().run(this).await()
        }

        private suspend fun lookForMarker(): Set<ArucoMarker> = coroutineScope {
            LookAroundAndDetectArucoMarkerBuilder.with(qiContext)
                    .withMarkerLength(targetMarker.detectionConfig.markerLength)
                    .withArucoMarkerFrameLocalizationPolicy(ArucoMarkerFrameLocalizationPolicy.ATTACHED_TO_MAPFRAME)
                    .withLookAtMovementPolicy(LookAtMovementPolicy.HEAD_AND_BASE)
                    .withLookAtTargetFrame(*createLookAtTargetsAroundMarker(targetMarker.frame))
                    .withArucoMarkerValidationCallback(object : LookAroundAndDetectArucoMarker.ArucoMarkerValidationCallback {
                        override fun isMarkerValid(marker: ArucoMarker): Future<Boolean> {
                            return Future.of(marker.id == targetMarker.id)
                        }
                    })
                    .withTerminationCallback(LookAroundAndDetectArucoMarker
                            .TerminateWhenSomeMarkersDetected())
                    .buildAsync().await()
                    .async().run(this).await()
        }

        private suspend fun createLookAtTargetsAroundMarker(markerFrame: Frame): Array<Frame> {
            val robotFrame = qiContext.actuation.async().robotFrame().await()
            val robotToMarker = markerFrame.async().computeTransform(robotFrame).await().transform

            val identityVector = Vector3(0.0, 0.0, 0.0)
            return arrayOf(
                    markerFrame,
                    markerFrame,
                    Transform(Quaternion(0.0, 0.0, 0.131, 0.991), identityVector).let {
                        qiContext.mapping.async().makeDetachedFrame(robotFrame, it * robotToMarker).await()
                    },
                    Transform(Quaternion(0.0, 0.0, 0.259, 0.966), identityVector).let {
                        qiContext.mapping.async().makeDetachedFrame(robotFrame, it * robotToMarker).await()
                    },
                    Transform(Quaternion(0.0, 0.0, -0.131, 0.991), identityVector).let {
                        qiContext.mapping.async().makeDetachedFrame(robotFrame, it * robotToMarker).await()
                    },
                    Transform(Quaternion(0.0, 0.0, -0.259, 0.966), identityVector).let {
                        qiContext.mapping.async().makeDetachedFrame(robotFrame, it * robotToMarker).await()
                    })
        }

        private suspend fun getFrameBetweenRobotAndMarker(marker: ArucoMarker, currentDistanceToMarker: Double): Frame {
            val margin = 0.3
            val distanceToWalk = Math.min(
                currentDistanceToMarker - MIN_MARKER_VIEWING_DISTANCE - margin,
                config.distanceBetweenStops)
            return qiContext.actuation.async().robotFrame().await().let { robotFrame ->
                marker.frame.async().computeTransform(robotFrame).await()
                        .run { transform.translation / (currentDistanceToMarker / distanceToWalk) }
                        .let { TransformBuilder.create().from2DTranslation(it.x, it.y) }
                        .let { qiContext.mapping.async().makeDetachedFrame(robotFrame, it).await() }
            }
        }

        private suspend fun alignPepperHead(qiContext: QiContext) {
            val transform = TransformBuilder.create().fromTranslation(
                Vector3(1.0, 0.0, PepperRobotData.GAZE_FRAME_Z))
            val robotFrame = qiContext.actuation.async().robotFrame().await()
            val lookAtFrame = qiContext.mapping.async().makeDetachedFrame(robotFrame, transform).await()
            ExtraLookAtBuilder.with(qiContext).withFrame(lookAtFrame)
                .withTerminationPolicy(ExtraLookAt.TerminationPolicy.TERMINATE_WHEN_LOOKING_AT_OR_NOT_MOVING_ANYMORE)
                .buildAsync().await()
                .apply {
                    policy = LookAtMovementPolicy.HEAD_ONLY
                }.async().run().await()
        }

        private suspend fun applyRotationToZAxis(markerFrame: Frame, rotation: Double): Frame {
            val r = Rotation(Vector3D(1.0, 0.0, 0.0), rotation, RotationConvention.FRAME_TRANSFORM)
            val t = Transform(r.toQiQuaternion(), Vector3(0.0, 0.0, 0.0))
            return markerFrame.makeAttachedFrame(t).async().frame().await()
        }
    }
}