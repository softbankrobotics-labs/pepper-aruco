package com.softbankrobotics.pepperaruco.actuation

import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.`object`.actuation.Actuation
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.util.FutureUtils
import com.softbankrobotics.dx.pepperextras.geometry.getRotatedXAxis
import com.softbankrobotics.dx.pepperextras.geometry.getRotatedYAxis
import com.softbankrobotics.dx.pepperextras.geometry.getRotatedZAxis
import com.softbankrobotics.dx.pepperextras.geometry.toApacheVector3D
import com.softbankrobotics.dx.pepperextras.util.TAG
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D
import kotlin.math.acos

fun Actuation.computeRobotRelativeFrameOrientation(frame: Frame): RobotRelativeFrameOrientation {
    return FutureUtils.get(async().computeRobotRelativeFrameOrientation(frame))
}

fun Actuation.Async.computeRobotRelativeFrameOrientation(frame: Frame): Future<RobotRelativeFrameOrientation> {
    return robotFrame().andThenCompose { robotFrame ->
        frame.async().computeTransform(robotFrame).andThenApply { transformTime ->
            transformTime.transform.computeRobotRelativeFrameOrientation()
        }
    }
}

fun Actuation.computeFrameOrientation(frame: Frame): FrameOrientation {
    return FutureUtils.get(async().computeFrameOrientation(frame))
}

fun Actuation.Async.computeFrameOrientation(frame: Frame): Future<FrameOrientation> {
    return computeRobotRelativeFrameOrientation(frame).andThenApply { orientation ->
        if (orientation.zAxisDirection == RobotRelativeDirection.UP
                || orientation.zAxisDirection == RobotRelativeDirection.DOWN
                || orientation.yAxisDirection == RobotRelativeDirection.UP
                || orientation.yAxisDirection == RobotRelativeDirection.DOWN)
            FrameOrientation.VERTICAL
        else
            FrameOrientation.HORIZONTAL
    }
}

private fun getAngleToHorizontalPlane(vector: Vector3D): Double {
    val projectedVector = Vector3D(vector.x, vector.y, 0.0).normalize()
    return Math.toDegrees(acos(vector.dotProduct(projectedVector)))
}

private fun getAngleToVerticalVector(vector: Vector3D): Double {
    val projectedVector = Vector3D(0.0, 0.0, vector.z).normalize()
    return Math.toDegrees(acos(vector.dotProduct(projectedVector)))
}

fun Actuation.validateArucoMarkerFrameOrientation(markerFrame: Frame,
                                                  position: FrameOrientation,
                                                  threshold: Int = 4): Boolean {
    return FutureUtils.get(async().validateArucoMarkerFrameOrientation(
            markerFrame, position, threshold))
}

fun Actuation.Async.validateArucoMarkerFrameOrientation(markerFrame: Frame,
                                                        position: FrameOrientation,
                                                        threshold: Int = 4): Future<Boolean> {
    return robotFrame().andThenCompose { robotFrame ->
        markerFrame.async().computeTransform(robotFrame).andThenApply { markerTransform ->
            val rot = markerTransform.transform.rotation
            when (position) {
                FrameOrientation.HORIZONTAL -> {
                    val angleOfXToVerticalVector = getAngleToVerticalVector(
                            rot.getRotatedXAxis().toApacheVector3D())
                    val angleOfYToHorizontalPlane = getAngleToHorizontalPlane(
                            rot.getRotatedYAxis().toApacheVector3D())
                    val angleOfZToHorizontalPlane = getAngleToHorizontalPlane(
                            rot.getRotatedZAxis().toApacheVector3D())
                    val isValid = (angleOfXToVerticalVector <= threshold
                            && angleOfYToHorizontalPlane <= threshold
                            && angleOfZToHorizontalPlane <= threshold)
                    Log.i(TAG, "validateFrameOrientation: valid: $isValid " +
                            "($angleOfXToVerticalVector, $angleOfYToHorizontalPlane," +
                            " $angleOfZToHorizontalPlane)")
                    isValid
                }
                FrameOrientation.VERTICAL -> {
                    val angleOfXToHorizontalPlane = getAngleToHorizontalPlane(
                            rot.getRotatedXAxis().toApacheVector3D())
                    val angleOfYToHorizontalPlane = getAngleToHorizontalPlane(
                            rot.getRotatedYAxis().toApacheVector3D())
                    val angleOfZToVerticalVector = getAngleToVerticalVector(
                            rot.getRotatedZAxis().toApacheVector3D())
                    val isValid = (angleOfXToHorizontalPlane <= threshold
                            && angleOfYToHorizontalPlane <= threshold
                            && angleOfZToVerticalVector <= threshold)
                    isValid
                }
            }
        }
    }
}

fun Actuation.isMarkerOnTheFloor(markerFrame: Frame): Boolean {
    return FutureUtils.get(async().isMarkerOnTheFloor(markerFrame))
}

/**
 *  Check if a marker is lying on the floor, so marker is horizontal and is at floor level
 *  @return A future to a boolean, which will be equal to true if marker is indeed on the floor.
 */
fun Actuation.Async.isMarkerOnTheFloor(markerFrame: Frame): Future<Boolean> {
    val angleThreshold = 15 // Aruco must at most make an angle of 15° with horizon
    val levelThreshold = 0.2 // Aruco must be at most 10cm above/below floor level
    return robotFrame().andThenCompose { robotFrame ->
        validateArucoMarkerFrameOrientation(
                markerFrame, FrameOrientation.HORIZONTAL, angleThreshold).andThenCompose { isHorizontal ->
            markerFrame.async().computeTransform(robotFrame).andThenApply { floorLevelTransform ->
                val floorLevel = floorLevelTransform.transform.translation.z
                Log.i(TAG, "isMarkerOnTheFloor: floorLevel: $floorLevel")
                val isAtFloorLevel = Math.abs(floorLevel) < levelThreshold
                isHorizontal && isAtFloorLevel
            }
        }
    }
}
