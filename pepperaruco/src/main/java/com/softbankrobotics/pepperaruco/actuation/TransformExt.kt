package com.softbankrobotics.pepperaruco.actuation

import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.`object`.geometry.Vector3
import com.softbankrobotics.dx.pepperextras.geometry.times
import com.softbankrobotics.dx.pepperextras.geometry.toApacheVector3D
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D

fun Transform.computeRobotRelativeFrameOrientation(): RobotRelativeFrameOrientation {
    val r = rotation
    val vX = r * Vector3(1.0, 0.0, 0.0)
    val vY = r * Vector3(0.0, 1.0, 0.0)
    val vZ = r * Vector3(0.0, 0.0, 1.0)

    val directions = listOf(
            RobotRelativeDirection.AWAY_FROM_ROBOT to Vector3D(1.0, 0.0, 0.0),
            RobotRelativeDirection.TOWARD_ROBOT to Vector3D(-1.0, 0.0, 0.0),
            RobotRelativeDirection.UP to Vector3D(0.0, 0.0, 1.0),
            RobotRelativeDirection.DOWN to Vector3D(0.0, 0.0, -1.0),
            RobotRelativeDirection.LEFT_OF_ROBOT to Vector3D(0.0, 1.0, 0.0),
            RobotRelativeDirection.RIGHT_OF_ROBOT to Vector3D(0.0, -1.0, 0.0)
    )
    val directionsXDot = directions.map { it.first to it.second.dotProduct(vX.toApacheVector3D())}
    val directionsYDot = directions.map { it.first to it.second.dotProduct(vY.toApacheVector3D())}
    val directionsZDot = directions.map { it.first to it.second.dotProduct(vZ.toApacheVector3D())}
    return RobotRelativeFrameOrientation(
            directionsXDot.maxBy { it.second }!!.first,
            directionsYDot.maxBy { it.second }!!.first,
            directionsZDot.maxBy { it.second }!!.first
    )
}