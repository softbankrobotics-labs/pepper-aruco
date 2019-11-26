package com.softbankrobotics.pepperaruco.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.Promise
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.`object`.actuation.LookAt
import com.aldebaran.qi.sdk.`object`.actuation.LookAtMovementPolicy
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.`object`.geometry.Vector3
import com.aldebaran.qi.sdk.builder.LookAtBuilder
import com.aldebaran.qi.sdk.builder.TransformBuilder
import kotlin.math.pow

object LookAtUtils {

    class RobotFrameStore(val size:Int = 4) {
        private var lastRobotFrames = Array<Frame?>(size) {null}
        private var currentFrame = 0
        var robotFrame: Frame?
            get() {
                return lastRobotFrames[currentFrame]
            }
            set(frame) {
                lastRobotFrames[currentFrame] = frame
                currentFrame = (currentFrame + 1) % size
            }
    }

    fun isRobotLookingAtFrame(qiContext: QiContext,
                              targetFrame: Frame,
                              waitForBaseAligned: Boolean = false,
                              store: RobotFrameStore = RobotFrameStore(),
                              alignedDelta: Double = 0.1): Future<Boolean> {
        return qiContext.actuation.async().gazeFrame().andThenCompose { gazeFrame ->
            val t = targetFrame.computeTransform(gazeFrame).transform.translation
            Log.d(TAG, "isRobotLookingAtFrame: Gaze: ${t.x} ${t.y} ${t.z}")
            val gazeAndTargetAligned = t.x > 0 && Math.abs(t.y) < alignedDelta && Math.abs(t.z) < alignedDelta
            if (gazeAndTargetAligned && waitForBaseAligned)
                qiContext.actuation.async().robotFrame().andThenCompose { robotFrame ->
                    val tb = targetFrame.computeTransform(robotFrame).transform.translation
                    Log.d(TAG, "isRobotLookingAtFrame: Body: ${tb.x} ${tb.y} ${tb.z}")
                    val bodyAndTargetAligned = tb.x > 0 && Math.abs(tb.y) < alignedDelta
                    Log.d(TAG, "isRobotLookingAtFrame return ${bodyAndTargetAligned}")
                    if (!bodyAndTargetAligned && store.robotFrame != null) {
                        robotFrame.async().computeTransform(store.robotFrame).andThenApply { bodyMovement ->
                            store.robotFrame = robotFrame
                            val bodyTrans = bodyMovement.transform.translation
                            val bodyAngle = bodyMovement.transform.rotation.toApacheRotation().angle
                            bodyTrans.x < alignedDelta && bodyTrans.y < alignedDelta && bodyAngle < 0.01
                        }
                    }
                    else {
                        store.robotFrame = robotFrame
                        Future.of(bodyAndTargetAligned)
                    }
                }
            else {
                Log.d(TAG, "isRobotLookingAtFrame return ${gazeAndTargetAligned}")
                Future.of(gazeAndTargetAligned)
            }
        }
    }

    fun detectWhenLookingAtFrame(qiContext: QiContext,
                                 targetFrame: Frame,
                                 waitForBaseAligned: Boolean = false,
                                 timeoutMs: Int = 5000): Future<Unit> {
        Log.i(TAG, "detectWhenLookingAtFrame")
        val promise = Promise<Unit>()
        val period: Long = 200 // Millisecond
        var count = period
        var running = true
        val store = RobotFrameStore()

        val handler = Handler(Looper.getMainLooper())
        var callback: () -> Unit = {}
        callback = {
            if (running)
                if (count > timeoutMs) {
                    promise.setCancelled()
                } else {
                    count += period
                    isRobotLookingAtFrame(
                        qiContext,
                        targetFrame,
                        waitForBaseAligned,
                        store
                    ).andThenConsume { isLooking ->
                        if (isLooking) {
                            Log.i(TAG, "Robot is looking !")
                            promise.setValue(null)
                        }
                        else {
                            handler.postDelayed(callback, period)
                        }
                    }
                }
        }
        handler.postDelayed(callback, period)

        promise.setOnCancel {
            Log.i(TAG, "LookAtFrame promise canceled")
            running = false
            promise.setCancelled()
        }
        return promise.future
    }

    fun stopWhenLookingAtFrame(qiContext: QiContext, lookAtFuture: Future<Void>, targetFrame: Frame, waitForBaseAligned: Boolean = false, timeoutMs: Int = 5000): Future<Unit> {
        if (!lookAtFuture.isDone) {
            val detectingFuture = detectWhenLookingAtFrame(
                qiContext,
                targetFrame,
                waitForBaseAligned,
                timeoutMs
            )
            detectingFuture.thenConsume { detectedFuture ->
                if (!lookAtFuture.isDone)
                    lookAtFuture.cancel(true)
            }
            return detectingFuture
        }
        return Future.of(null)
    }

    fun lookAtFrame(qiContext: QiContext, frame: Frame, policy: LookAtMovementPolicy = LookAtMovementPolicy.HEAD_AND_BASE): Future<Void> {
        return LookAtBuilder.with(qiContext).withFrame(frame).buildAsync().andThenCompose { lookAt ->
            lateinit var future: Future<Void>
            var detectFuture: Future<Unit>? = null
            lookAt.policy = policy
            lookAt.addOnStartedListener(object : LookAt.OnStartedListener {
                override fun onStarted() {
                    detectFuture = stopWhenLookingAtFrame(
                        qiContext, future, frame,
                        policy == LookAtMovementPolicy.HEAD_AND_BASE
                    )
                }
            })
            future = lookAt.async().run()
            future.andThenConsume {
                Log.i(TAG, "LookAtFrame done, request cancelation")
                detectFuture?.requestCancellation()
            }
            future
        }
    }

    fun lookAtTransformFromSphericalCoord(coord: SphericalCoord): Transform {
        val sinTheta = Math.sin(coord.theta)
        val x = coord.rho * sinTheta * Math.cos(coord.phi)
        val y = coord.rho * sinTheta * Math.sin(coord.phi)
        val z = coord.rho * Math.cos(coord.theta)
        return TransformBuilder.create().fromTranslation(Vector3(x, y, z))
    }

    fun lookAtTransformToSphericalCoord(transform: Transform): SphericalCoord {
        val rho = Math.sqrt(transform.translation.x.pow(2) + transform.translation.y.pow(2) + transform.translation.z.pow(2))
        val phi = Math.atan(transform.translation.y / transform.translation.x)
        val theta = Math.acos(transform.translation.z / rho)
        return SphericalCoord(rho, theta, phi)
    }

    val gazeFrameZ = 1.1165693310431193

    // Create a reference frame, aligned with the body of the robot, and at same height as gazeFrame
    fun alignedGazeFrame(qiContext: QiContext, robotFrame: Frame): Frame {
        val transform = TransformBuilder.create().fromTranslation(Vector3(0.0, 0.0,
            gazeFrameZ
        ))
        val refFrame = qiContext.mapping.makeFreeFrame()
        refFrame.update(robotFrame, transform, 0)
        return refFrame.frame()
    }

}
