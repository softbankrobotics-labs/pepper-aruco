package com.softbankrobotics.pepperaruco.actuation

import com.aldebaran.qi.sdk.`object`.geometry.Quaternion
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.`object`.geometry.Vector3

object PepperRobotData {

    val HEAD_CAMERA_MATRIX = hashMapOf<Pair<Int, Int>, DoubleArray>(
            Pair(1280, 960) to doubleArrayOf(
                    1213.91824, 0.0           , 657.406192,
                    0.0,        1213.91824    , 488.498999,
                    0.0,        0.0           , 1.0
            ),
            Pair(640, 480) to doubleArrayOf(
                    606.95912,  0.0,          328.703096,
                    0.0,        606.95912,    244.2494995,
                    0.0,        0.0,          1.0
            ),
            Pair(320, 240) to doubleArrayOf(
                    303.47956,  0.0,          164.351548,
                    0.0,        303.47956,    122.12474975,
                    0.0,        0.0,          1.0
            )
    )

    val HEAD_CAMERA_DISTORTION_COEFS = doubleArrayOf(
            -8.55109544e+00,
            -1.33329352e+02,
            -1.85792215e-03,
            4.06427067e-03,
            1.19636779e+03,
            -8.73892483e+00,
            -1.29521262e+02,
            1.17698249e+03
    )

    // There is no way to obtain position of the camera programmatically in Android.
    // This value was computed from python using:
    //   import qi
    //   sess = qi.Session()
    //   sess.connect("127.0.0.1")
    //   motion = sess.service("ALMotion")
    //   gazeFrame = motion._getSensorFrame("Gaze")
    //   cameraFrame = motion._getSensorFrame("CameraTop")
    //   transform = cameraFrame.computeTransform(gazeFrame)
    //   transform
    //   {'transform': {'rotation': {'y': 0.0, 'x': 0.0, 'z': 0.0, 'w': 0.9999999789130501}, 'translation': {'y': 0.0, 'x': 0.020309998728599843, 'z': 0.04393999093233303}}, 'time': 0L}
    val GAZE_TO_HEAD_CAMERA_TRANSFORM = Transform(
            Quaternion(0.0, 0.0, 0.0, 1.0),
            Vector3(0.020309998728599843, 0.0, 0.04393999093233303)
    )

    val GAZE_FRAME_Z = 1.1165693310431193
}