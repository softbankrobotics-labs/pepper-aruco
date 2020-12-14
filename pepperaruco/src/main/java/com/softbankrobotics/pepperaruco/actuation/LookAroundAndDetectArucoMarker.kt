package com.softbankrobotics.pepperaruco.actuation

import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.Promise
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.FreeFrame
import com.aldebaran.qi.sdk.`object`.actuation.LookAtMovementPolicy
import com.aldebaran.qi.sdk.util.FutureUtils
import com.softbankrobotics.dx.pepperextras.actuation.ExtraLookAt
import com.softbankrobotics.dx.pepperextras.actuation.ExtraLookAtBuilder
import com.softbankrobotics.dx.pepperextras.actuation.internal.RunnableAction
import com.softbankrobotics.dx.pepperextras.util.SingleThread
import com.softbankrobotics.dx.pepperextras.util.asyncFuture
import com.softbankrobotics.dx.pepperextras.util.await
import com.softbankrobotics.dx.pepperextras.util.awaitOrNull
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import java.lang.Exception
import java.util.concurrent.CancellationException

// Spherical coordinates to have Pepper look on the floor for markers.
// Pepper will do a full turn.
val FLOOR_LOOK_AROUND_FRONT_ONLY_SPHERICAL_COORDINATES = arrayOf(
        // Look Front, insisting...
        120.0 to 0.0,
        120.0 to 0.0,
        120.0 to 0.0,
        // Look Left
        120.0 to 15.0,
        120.0 to 15.0,
        120.0 to 30.0,
        // Look Right
        120.0 to 345.0,
        120.0 to 345.0,
        120.0 to 330.0
)

// Spherical coordinates to have Pepper look on the floor for markers.
// Pepper will do a full 360 turn.
val FLOOR_LOOK_AROUND_FULL_360_SPHERICAL_COORDINATES = arrayOf(
        // Look Left, and a bit Downward
        120.0 to 0.0,
        120.0 to 15.0,
        120.0 to 30.0,
        120.0 to 45.0,
        120.0 to 60.0,
        120.0 to 75.0,
        120.0 to 90.0,
        // Look Right, and a bit Downward
        120.0 to 345.0,
        120.0 to 330.0,
        120.0 to 315.0,
        120.0 to 300.0,
        120.0 to 285.0,
        120.0 to 270.0,
        // Look Back and a bit Downward
        120.0 to 240.0,
        120.0 to 225.0,
        120.0 to 210.0,
        120.0 to 195.0,
        120.0 to 180.0,
        120.0 to 165.0,
        120.0 to 150.0,
        120.0 to 135.0,
        120.0 to 120.0
)


class LookAroundAndDetectArucoMarker internal constructor(
        private val qiContext: QiContext,
        private val config: Config,
        private val detectArucoMarkerConfig: DetectArucoMarker.Config)
    : RunnableAction<Set<ArucoMarker>, LookAroundAndDetectArucoMarker.Async>() {

    interface ArucoMarkerValidationCallback {
        fun isMarkerValid(marker: ArucoMarker): Future<Boolean>
    }

    class AllMarkerAreValid(): ArucoMarkerValidationCallback {
        override fun isMarkerValid(marker: ArucoMarker): Future<Boolean> {
            return Future.of(true)
        }
    }

    interface TerminationCallback {
        fun shouldTerminate(currentlyDetectedMarkers: Set<ArucoMarker>): Future<Boolean>
    }

    class TerminateOnlyWhenLookAroundHasFinished(): TerminationCallback {
        override fun shouldTerminate(currentlyDetectedMarkers: Set<ArucoMarker>): Future<Boolean> {
            return Future.of(false)
        }
    }

    class TerminateWhenSomeMarkersDetected(): TerminationCallback {
        override fun shouldTerminate(currentlyDetectedMarkers: Set<ArucoMarker>): Future<Boolean> {
            return Future.of(currentlyDetectedMarkers.isNotEmpty())
        }
    }

    class TerminateWhenSpecificMarkerDetected(val markerId: Int): TerminationCallback {
        override fun shouldTerminate(currentlyDetectedMarkers: Set<ArucoMarker>): Future<Boolean> {
            return Future.of((currentlyDetectedMarkers.map { it.id }.contains(markerId)))
        }
    }

    data class Config(
            var lookAtTargetSequence: Sequence<LookAtTarget> = Sequence { arrayOf<LookAtTarget>().iterator() },
            var lookAtMovementPolicy: LookAtMovementPolicy = LookAtMovementPolicy.HEAD_ONLY,
            var markerValidationCallback: ArucoMarkerValidationCallback = AllMarkerAreValid(),
            var terminationCallback: TerminationCallback = TerminateOnlyWhenLookAroundHasFinished()
    )

    interface OnArucoMarkerDetectedListener {
        fun onArucoMarkerDetected(markers: Set<ArucoMarker>)
    }

    fun addOnArucoMarkerDetectedListener(listener: OnArucoMarkerDetectedListener) {
        FutureUtils.get(async().addOnArucoMarkerDetectedListener(listener))
    }

    fun removeOnArucoMarkerDetectedListener(listener: OnArucoMarkerDetectedListener) {
        FutureUtils.get(async().removeOnArucoMarkerDetectedListener(listener))
    }

    fun removeAllOnArucoMarkerDetectedListeners() {
        FutureUtils.get(async().removeAllOnArucoMarkerDetectedListeners())
    }

    override val _asyncInstance = Async()
    inner class Async internal constructor()
        : RunnableAction<Set<ArucoMarker>, LookAroundAndDetectArucoMarker.Async>.Async() {

        private val listeners = mutableListOf<OnArucoMarkerDetectedListener>()
        private var extraLookAt: ExtraLookAt? = null
        private var endPromise: Promise<Unit>? = null
        private var extraLookAtFuture: Future<Void>? = null

        override fun _run(scope: CoroutineScope): Future<Set<ArucoMarker>> = scope.asyncFuture(CoroutineName("$ACTION_NAME|_run")) {
            Log.d(TAG, "Starting.")
            var listener: OnLookAtStatusChangedListener? = null
            try {
                val targetIterator = config.lookAtTargetSequence.iterator()
                if (!targetIterator.hasNext()) {
                    Log.d(TAG, "No target defined. Returning.")
                    return@asyncFuture setOf<ArucoMarker>()
                }

                // Make LookAt target frame
                val targetFrame = qiContext.mapping.async().makeFreeFrame().await().apply {
                    targetIterator.next().let { next ->
                        async().update(next.baseFrame, next.transform, next.time).await()
                    }
                }

                val markerSet = mutableSetOf<ArucoMarker>()
                endPromise = Promise()
                listener = OnLookAtStatusChangedListener(markerSet, targetFrame, targetIterator, scope)

                extraLookAt = ExtraLookAtBuilder.with(qiContext)
                        .withFrame(targetFrame.async().frame().await())
                        .buildAsync().await()
                        .apply {
                            policy = config.lookAtMovementPolicy
                            async().addOnStatusChangedListener(listener).await()
                        }
                Log.i(TAG, "Running dxLookAt")
                extraLookAtFuture = extraLookAt?.async()?.run(this)
                Log.i(TAG, "Await end promise")
                endPromise?.future?.await()
                Log.d(TAG, "Found ${markerSet.size} markers.")
                markerSet
            } catch (e: CancellationException) {
                // Code will arrive here if future returned by lookAroundForMarker gets cancelled.
                // We need to make sure that detection stops.
                listener?.future?.requestCancellation()
                listener?.future?.await()
                Log.d(TAG, "Got CancellationException.")
                throw e
            }
        }

        inner class OnLookAtStatusChangedListener(
                val markerSet: MutableSet<ArucoMarker>,
                val targetFrame: FreeFrame,
                val targetIterator: Iterator<LookAtTarget>,
                val scope: CoroutineScope): ExtraLookAt.OnStatusChangedListener {

            var future: Future<Unit>? = null
            var preventMultipleDetectionFlag = false

            override fun onStatusChanged(status: ExtraLookAt.LookAtStatus) {
                Log.i(TAG, "onStatusChanged listener called with status $status")
                future = scope.asyncFuture(CoroutineName("$ACTION_NAME|onStatusChanged")) listener@{
                    try {
                        val detectedMarkers: Set<ArucoMarker>
                        when (status) {
                            ExtraLookAt.LookAtStatus.LOOKING_AT,
                            ExtraLookAt.LookAtStatus.NOT_LOOKING_AT_AND_NOT_MOVING_ANYMORE -> {
                                if (preventMultipleDetectionFlag)
                                    return@listener
                                preventMultipleDetectionFlag = true
                                try {
                                    Log.d(TAG, "Detecting aruco marker")
                                    detectedMarkers = DetectArucoMarkerBuilder.with(qiContext)
                                            .withMarkerLength(detectArucoMarkerConfig.markerLength)
                                            .withDictionary(detectArucoMarkerConfig.dictionary)
                                            .withCameraMatrix(detectArucoMarkerConfig.cameraMatrix)
                                            .withDistortionCoefs(detectArucoMarkerConfig.distortionCoefs)
                                            .withArucoMarkerFrameLocalizationPolicy(detectArucoMarkerConfig.localizationPolicy)
                                            .buildAsync().await()
                                            .async().run().await().filter {
                                                config.markerValidationCallback.isMarkerValid(it).await()
                                            }.toSet()

                                    Log.d(TAG, "${detectedMarkers.size} markers detected.")
                                    listeners.forEach { listener ->
                                        listener.onArucoMarkerDetected(detectedMarkers)
                                    }
                                } catch (e: CancellationException) {
                                    return@listener
                                } catch (e: Exception) {
                                    Log.d(TAG, "Got Exception: $e.")
                                    extraLookAtFuture?.requestCancellation()
                                    extraLookAtFuture?.awaitOrNull()
                                    endPromise?.setError(e.message)
                                    return@listener
                                }
                            }
                            else -> {
                                return@listener
                            }
                        }
                        markerSet.addAll(detectedMarkers)

                        if (config.terminationCallback.shouldTerminate(markerSet).await()) {
                            Log.d(TAG, "Should terminate. Cancelling lookat.")
                            extraLookAtFuture?.requestCancellation()
                            Log.d(TAG, "Awaiting dxlookat")
                            extraLookAtFuture?.awaitOrNull()
                            Log.d(TAG, "Set value endpromise")
                            endPromise?.setValue(Unit)
                        } else if (targetIterator.hasNext()) {
                            Log.d(TAG, "Updating target to next target")
                            targetIterator.next().let { next ->
                                targetFrame.async().update(next.baseFrame, next.transform, next.time).await()
                                preventMultipleDetectionFlag = false
                                extraLookAt?.async()?.resetStatus()
                            }
                        } else {
                            Log.d(TAG, "No more target. Cancelling lookat.")
                            extraLookAtFuture?.requestCancellation()
                            extraLookAtFuture?.awaitOrNull()
                            endPromise?.setValue(Unit)
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Caught an error $e")
                        throw e
                    }
                }
            }
        }

        fun addOnArucoMarkerDetectedListener(listener: OnArucoMarkerDetectedListener): Future<Unit>
                = SingleThread.GlobalScope.asyncFuture(CoroutineName("$ACTION_NAME|addOnstatusChangedListener")) {
            listeners.add(listener)
            Unit
        }

        fun removeOnArucoMarkerDetectedListener(listener: OnArucoMarkerDetectedListener): Future<Unit>
                = SingleThread.GlobalScope.asyncFuture(CoroutineName("$ACTION_NAME|removeOnstatusChangedListener"))  {
            listeners.remove(listener)
            Unit
        }

        fun removeAllOnArucoMarkerDetectedListeners(): Future<Unit>
                = SingleThread.GlobalScope.asyncFuture(CoroutineName("$ACTION_NAME|removeAllOnstatusChangedListener"))  {
            listeners.clear()
        }
    }
}