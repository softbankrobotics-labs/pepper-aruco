package com.softbankrobotics.pepperaruco

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import com.aldebaran.qi.sdk.`object`.geometry.Quaternion
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.`object`.geometry.Vector3
import com.softbankrobotics.dx.actuation.makeDetachedFrame
import com.softbankrobotics.dx.util.QiSDKTestActivity
import com.softbankrobotics.dx.util.await
import com.softbankrobotics.dx.util.withRobotFocus
import com.softbankrobotics.pepperaruco.actuation.RobotRelativeDirection
import com.softbankrobotics.pepperaruco.actuation.computeRobotRelativeFrameOrientation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class FutureExtTest {

    @get:Rule
    val activityRule = ActivityTestRule(QiSDKTestActivity::class.java)

    @Test
    fun testGetRobotRelativeFrameOrientation() = withRobotFocus(activityRule.activity) { qiContext ->
        runBlocking {
            val robotFrame = qiContext.actuation.async().robotFrame().await()
            val t = Vector3(1.0, 1.0, 1.0)
            val quatIdentity = Quaternion(0.0, 0.0, 0.0, 1.0)
            var frame = qiContext.mapping.async()
                    .makeDetachedFrame(robotFrame, Transform(quatIdentity, t)).await()
            var orientation = qiContext.actuation.async()
                    .computeRobotRelativeFrameOrientation(frame).await()
            assertEquals(RobotRelativeDirection.AWAY_FROM_ROBOT, orientation.xAxisDirection)
            assertEquals(RobotRelativeDirection.LEFT_OF_ROBOT, orientation.yAxisDirection)
            assertEquals(RobotRelativeDirection.UP, orientation.zAxisDirection)

            val quat180AroundX = Quaternion(1.0, 0.0, 0.0, 0.0)
            frame = qiContext.mapping.async()
                    .makeDetachedFrame(robotFrame, Transform(quat180AroundX, t)).await()
            orientation = qiContext.actuation.async()
                    .computeRobotRelativeFrameOrientation(frame).await()
            assertEquals(RobotRelativeDirection.AWAY_FROM_ROBOT, orientation.xAxisDirection)
            assertEquals(RobotRelativeDirection.RIGHT_OF_ROBOT, orientation.yAxisDirection)
            assertEquals(RobotRelativeDirection.DOWN, orientation.zAxisDirection)

            val quat180AroundY = Quaternion(0.0, 1.0, 0.0, 0.0)
            frame = qiContext.mapping.async()
                    .makeDetachedFrame(robotFrame, Transform(quat180AroundY, t)).await()
            orientation = qiContext.actuation.async()
                    .computeRobotRelativeFrameOrientation(frame).await()
            assertEquals(RobotRelativeDirection.TOWARD_ROBOT, orientation.xAxisDirection)
            assertEquals(RobotRelativeDirection.LEFT_OF_ROBOT, orientation.yAxisDirection)
            assertEquals(RobotRelativeDirection.DOWN, orientation.zAxisDirection)
        }
    }

}