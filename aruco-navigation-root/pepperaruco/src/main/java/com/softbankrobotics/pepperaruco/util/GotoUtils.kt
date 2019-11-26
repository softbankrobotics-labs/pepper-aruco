package com.softbankrobotics.pepperaruco.util

import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.`object`.geometry.Quaternion
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.`object`.geometry.Vector3
import com.aldebaran.qi.sdk.builder.AnimateBuilder
import com.aldebaran.qi.sdk.builder.AnimationBuilder
import com.aldebaran.qi.sdk.builder.GoToBuilder
import com.aldebaran.qi.sdk.builder.TransformBuilder
import org.apache.commons.math3.geometry.euclidean.threed.RotationConvention
import org.apache.commons.math3.geometry.euclidean.threed.RotationOrder
import java.lang.Math.atan2
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate

object GotoUtils {

    private var timerGoTo: Timer? = null
    private var futureGoTo: Future<Void>? = null

    private fun launchTimerGoTo(goToTimeout: Int) {
        if (timerGoTo != null) {
            timerGoTo?.cancel()
        }
        timerGoTo = Timer()
        timerGoTo?.scheduleAtFixedRate(goToTimeout.toLong(), 1000) {
            timerGoTo?.cancel()
            if (futureGoTo?.isDone != true) {
                Log.i(TAG, "GoTo timer interrupting")
                futureGoTo?.cancel(true)
            }
            timerGoTo = null
            futureGoTo = null
        }
    }

    // goToFrame makes the robot go to targetFrame.
    // This function will retry in case the GoTo action fails.
    fun goToFrame(qiContext: QiContext, targetFrame: Frame?, maxRetry: Int = 5): Future<Void> {
        return goToFrame(
            qiContext,
            targetFrame,
            maxRetry,
            0
        )
    }

    private fun goToFrame(qiContext: QiContext, targetFrame: Frame?, maxRetry: Int, attempt: Int, goToTimeout: Int = 30000): Future<Void> {
        Log.d(TAG, "goToFrame: #${attempt}/${maxRetry}")

        // Check target frame
        if (targetFrame == null) {
            Log.e(TAG, "Attempt: #$attempt: Invalid frame")
            return Future.fromError("Invalid frame")
        }
        if (attempt > maxRetry) {
            return Future.fromError("Too many failed attempt, abort")
        }
        // Build and run
        return GoToBuilder.with(qiContext).withFrame(targetFrame).buildAsync()
            .andThenCompose { goTo ->
                Log.d(TAG, "running GoTo")
                launchTimerGoTo(goToTimeout)
                futureGoTo = goTo.async().run()
                futureGoTo
            }.thenCompose() { future ->
                Log.d(TAG, "GoTo finished")
                if (future.isSuccess) {
                    Log.d(TAG, "Target frame reached (Retries: $attempt)")
                    future
                } else if (future.hasError()) {
                    Log.e(TAG, "GoTo failed: ${future.error}")
                    // Workaround: recursive call to goTo
                    goToFrame(
                        qiContext,
                        targetFrame,
                        maxRetry,
                        attempt + 1,
                        goToTimeout
                    )
                } else if (future.isCancelled) {
                    Log.i(TAG, "GoTo has been cancelled, attempt: #$attempt")
                    future
                }
                else
                    future
            }
    }

    fun goStraightToPos(qiContext: QiContext, x: Double, y: Double, theta: Double): Future<Void> {
        val animationString = String.format("[\"Holonomic\", [\"Line\", [%f, %f]], %f, 1.0]", x, y, theta)
        val animation = AnimationBuilder.with(qiContext).withTexts(animationString).build()
        val animate = AnimateBuilder.with(qiContext).withAnimation(animation).build()
        return animate.async().run()
    }
}

