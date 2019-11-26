package com.softbankrobotics.pepperaruco.util

import android.util.Log
import com.aldebaran.qi.sdk.`object`.geometry.Quaternion
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.`object`.geometry.Vector3
import org.apache.commons.math3.geometry.euclidean.threed.Rotation as ApacheRotation
import org.apache.commons.math3.geometry.euclidean.threed.RotationConvention
import org.apache.commons.math3.geometry.euclidean.threed.RotationOrder
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.MatrixUtils
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D as ApacheVector
import org.apache.commons.math3.complex.Quaternion as ApacheQuaternion

// Rotation

class Rotation: ApacheRotation {
    // Angle axis rotation
    constructor(x: Double, y: Double, z: Double, theta: Double): super(ApacheVector(x, y, z), theta, RotationConvention.FRAME_TRANSFORM) {}
    // Euler angle rotation
    constructor(wx: Double, wy: Double, wz: Double) : super(RotationOrder.XYZ, RotationConvention.FRAME_TRANSFORM, wx, wy, wz) {}
    constructor(r: ApacheRotation): super(r.q0, r.q1, r.q2, r.q3, false) {}

    fun toQuaternion(): Quaternion { return toQiQuaternion() }
}

// Vector3

fun Vector3.toApacheVector(): ApacheVector {
    return ApacheVector(x, y, z)
}

operator fun Vector3.plus(v2: Vector3): Vector3 {
    return Vector3(x + v2.x, y + v2.y, z + v2.z)
}

operator fun Vector3.minus(v2: Vector3): Vector3 {
    return Vector3(x - v2.x, y - v2.y, z - v2.z)
}

operator fun Vector3.div(d: Double): Vector3 {
    return Vector3(x / d, y / d, z / d)
}

// Quaternion

fun Quaternion.toApacheQuaternion(): ApacheQuaternion {
    return ApacheQuaternion(w, x, y, z)
}

fun Quaternion.toApacheRotation(): ApacheRotation {
    return ApacheRotation(w, x, y, z, true)
}

operator fun Quaternion.times(q2: Quaternion): Quaternion {
    return toApacheQuaternion().multiply(q2.toApacheQuaternion()).toQiQuaternion()
}

operator fun Quaternion.times(v: Vector3): Vector3 {
    return toApacheRotation().applyInverseTo(v.toApacheVector()).toVector3()
}

// ApacheQuaternion

fun ApacheQuaternion.toQiQuaternion(): Quaternion {
    return Quaternion(q1, q2, q3, q0)
}

// ApacheRotation

fun ApacheRotation.toQiQuaternion(): Quaternion {
    return Quaternion(q1, q2, q3, q0)
}

// ApacheVector

fun ApacheVector.toVector3(): Vector3 {
    return Vector3(x, y, z)
}

// Transform

operator fun Transform.times(t2: Transform): Transform {
    return Transform(
        rotation * t2.rotation,
        translation + (rotation * t2.translation)
    )
}

fun Transform.inverse(): Transform {
    val r = rotation.toApacheRotation().matrix
    val t = translation
    val matrix = Array2DRowRealMatrix(4, 4)
    matrix.setSubMatrix(Array2DRowRealMatrix(r).transpose().data, 0, 0)
    matrix.setColumn(3, doubleArrayOf(t.x, t.y, t.z, 1.0))
    matrix.setRow(3, doubleArrayOf(0.0, 0.0, 0.0, 1.0))
    val inv = MatrixUtils.inverse(matrix)
    val tinv = inv.getColumn(3)
    val minv = inv.getSubMatrix(0, 2, 0, 2).data
    return Transform(
        ApacheRotation(Array2DRowRealMatrix(minv).transpose().data, 0.1).toQiQuaternion(),
        Vector3(tinv[0], tinv[1], tinv[2])
    )
}

fun Transform.log(TAG: String, msg: String) {
    Log.d(TAG, "${msg}: translation: {${translation.x} ${translation.y} ${translation.z}}")
    Log.d(TAG, "${msg}: rotation: {${rotation.x} ${rotation.y} ${rotation.z} ${rotation.w}}")
}

// Spherical Coordinates

data class SphericalCoord(val rho: Double, val theta: Double, val phi: Double)

