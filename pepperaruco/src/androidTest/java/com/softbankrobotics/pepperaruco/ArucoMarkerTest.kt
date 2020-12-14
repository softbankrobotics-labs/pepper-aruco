package com.softbankrobotics.pepperaruco

import android.graphics.Bitmap
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.LocalizationStatus
import com.aldebaran.qi.sdk.`object`.geometry.Quaternion
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.`object`.geometry.Vector3
import com.aldebaran.qi.sdk.builder.LocalizeAndMapBuilder
import com.softbankrobotics.dx.util.QiSDKTestActivity
import com.softbankrobotics.dx.util.SingleThread
import com.softbankrobotics.dx.util.TAG
import com.softbankrobotics.dx.util.await
import com.softbankrobotics.dx.util.awaitOrNull
import com.softbankrobotics.dx.util.withRobotFocus
import com.softbankrobotics.pepperaruco.actuation.ArucoDictionary
import com.softbankrobotics.pepperaruco.actuation.ArucoMarker
import com.softbankrobotics.pepperaruco.actuation.ArucoMarkerBuilder
import com.softbankrobotics.pepperaruco.actuation.ArucoMarkerFrameLocalizationPolicy
import com.softbankrobotics.pepperaruco.actuation.PepperRobotData
import com.softbankrobotics.pepperaruco.actuation.RobotRelativeDirection
import com.softbankrobotics.pepperaruco.actuation.computeRobotRelativeFrameOrientation
import io.mockk.mockk
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.core.Mat


@RunWith(AndroidJUnit4::class)
class ArucoMarkerTest {

    @get:Rule
    val activityRule = ActivityTestRule(QiSDKTestActivity::class.java)

    val dummyBitmap: Bitmap = mockk {}
    val dummyCorners: Mat = mockk {}

    suspend fun createMarker(qiContext: QiContext, tvec: DoubleArray, rvec: DoubleArray): ArucoMarker {
        return ArucoMarker.create(qiContext, 42, ArucoMarkerFrameLocalizationPolicy.DETACHED,
                dummyBitmap, dummyCorners, tvec, rvec, 0, 0.15,
                ArucoDictionary.DICT_4X4_100, PepperRobotData.HEAD_CAMERA_MATRIX,
                PepperRobotData.HEAD_CAMERA_DISTORTION_COEFS)
    }

    suspend fun localizeAndRunTest(qiContext: QiContext, block: suspend () -> Unit) {
        var f: Future<Void> = Future.cancelled()
        f = LocalizeAndMapBuilder.with(qiContext).buildAsync().await()
                .apply {
                    addOnStatusChangedListener { status ->
                        SingleThread.GlobalScope.launch {
                            Log.i(TAG, "Status changed $status")
                            if (status ==  LocalizationStatus.LOCALIZED) {
                                Log.i(TAG, "Robot is localized")
                                block()
                                f.requestCancellation()
                            }
                        }
                    }
                }
                .async().run()
        f.awaitOrNull()
    }

    @Test
    fun testMarkerOrientation() = withRobotFocus(activityRule.activity) { qiContext ->

        runBlocking {
            var tvec = doubleArrayOf(-0.04059367148270392, -0.0042877351072176245, 0.6531470343376096)
            var rvec = doubleArrayOf(3.1581800164380525, -0.009737187076127246, -0.17236074445411662)

            var marker = createMarker(qiContext, tvec, rvec)
            var orientation = qiContext.actuation.async()
                    .computeRobotRelativeFrameOrientation(marker.frame).await()
            assertEquals(RobotRelativeDirection.TOWARD_ROBOT, orientation.xAxisDirection)
            assertEquals(RobotRelativeDirection.RIGHT_OF_ROBOT, orientation.yAxisDirection)
            assertEquals(RobotRelativeDirection.UP, orientation.zAxisDirection)

            tvec = doubleArrayOf(-0.0065799738201712315, -0.04728001631789635, 0.6154773782242214)
            rvec = doubleArrayOf(-2.9321697351475327, 1.05357687607394, 0.20378092783132243)
            marker = createMarker(qiContext, tvec, rvec)
            orientation = qiContext.actuation.async()
                    .computeRobotRelativeFrameOrientation(marker.frame).await()
            assertEquals(RobotRelativeDirection.TOWARD_ROBOT, orientation.xAxisDirection)
            assertEquals(RobotRelativeDirection.RIGHT_OF_ROBOT, orientation.yAxisDirection)
            assertEquals(RobotRelativeDirection.UP, orientation.zAxisDirection)

            tvec = doubleArrayOf(0.02633380703333648, -0.07620234461276373, 0.6047962955214515)
            rvec = doubleArrayOf(2.187503296551204, -2.211253248464454, -0.022563764848722613)
            marker = createMarker(qiContext, tvec, rvec)
            orientation = qiContext.actuation.async()
                    .computeRobotRelativeFrameOrientation(marker.frame).await()
            assertEquals(RobotRelativeDirection.TOWARD_ROBOT, orientation.xAxisDirection)
            assertEquals(RobotRelativeDirection.UP, orientation.yAxisDirection)
            assertEquals(RobotRelativeDirection.LEFT_OF_ROBOT, orientation.zAxisDirection)

            tvec = doubleArrayOf(0.024316931604140578, -0.037989984846261066, 0.7739170739242143)
            rvec = doubleArrayOf(-0.11085258206779833, -3.0729501033762214, 0.04187114589747818)
            marker = createMarker(qiContext, tvec, rvec)
            orientation = qiContext.actuation.async()
                    .computeRobotRelativeFrameOrientation(marker.frame).await()
            assertEquals(RobotRelativeDirection.TOWARD_ROBOT, orientation.xAxisDirection)
            assertEquals(RobotRelativeDirection.LEFT_OF_ROBOT, orientation.yAxisDirection)
            assertEquals(RobotRelativeDirection.DOWN, orientation.zAxisDirection)

            tvec = doubleArrayOf(-0.0404276817331526, -0.012954027985346846, 0.7375226634399822)
            rvec = doubleArrayOf(2.7959332709641056, -0.05851947192163478, 1.4143941872217434)
            marker = createMarker(qiContext, tvec, rvec)
            orientation = qiContext.actuation.async()
                    .computeRobotRelativeFrameOrientation(marker.frame).await()
            assertEquals(RobotRelativeDirection.RIGHT_OF_ROBOT, orientation.xAxisDirection)
            assertEquals(RobotRelativeDirection.AWAY_FROM_ROBOT, orientation.yAxisDirection)
            assertEquals(RobotRelativeDirection.UP, orientation.zAxisDirection)

            tvec = doubleArrayOf(-0.011586647576947998, 0.0071068020441877496, 0.4273288072821557)
            rvec = doubleArrayOf(-2.767512880657446, 0.32361905505941196, -1.2843125885116533)
            marker = createMarker(qiContext, tvec, rvec)
            orientation = qiContext.actuation.async()
                    .computeRobotRelativeFrameOrientation(marker.frame).await()
            assertEquals(RobotRelativeDirection.RIGHT_OF_ROBOT, orientation.xAxisDirection)
            assertEquals(RobotRelativeDirection.AWAY_FROM_ROBOT, orientation.yAxisDirection)
            assertEquals(RobotRelativeDirection.UP, orientation.zAxisDirection)

            tvec = doubleArrayOf(0.024432450955777278, -0.00528629075949304, 0.4433741202828122)
            rvec = doubleArrayOf(-1.558362048138247, 1.510494574427345, -0.9366239135742951)
            marker = createMarker(qiContext, tvec, rvec)
            orientation = qiContext.actuation.async()
                    .computeRobotRelativeFrameOrientation(marker.frame).await()
            assertEquals(RobotRelativeDirection.RIGHT_OF_ROBOT, orientation.xAxisDirection)
            assertEquals(RobotRelativeDirection.UP, orientation.yAxisDirection)
            assertEquals(RobotRelativeDirection.TOWARD_ROBOT, orientation.zAxisDirection)

            tvec = doubleArrayOf(-0.005560823622641131, 0.009502341394693125, 0.45606686599444823)
            rvec = doubleArrayOf(0.041183120947138746, 2.052628494492256, 0.03515298832695021)
            marker = createMarker(qiContext, tvec, rvec)
            orientation = qiContext.actuation.async()
                    .computeRobotRelativeFrameOrientation(marker.frame).await()
            assertEquals(RobotRelativeDirection.RIGHT_OF_ROBOT, orientation.xAxisDirection)
            assertEquals(RobotRelativeDirection.TOWARD_ROBOT, orientation.yAxisDirection)
            assertEquals(RobotRelativeDirection.DOWN, orientation.zAxisDirection)

            tvec = doubleArrayOf(-0.03176647510338478, 0.020478635063781298, 0.4164881044601403)
            rvec = doubleArrayOf(1.4999971274197659, -1.5934114272981683, 0.9108280374230204)
            marker = createMarker(qiContext, tvec, rvec)
            orientation = qiContext.actuation.async()
                    .computeRobotRelativeFrameOrientation(marker.frame).await()
            assertEquals(RobotRelativeDirection.UP, orientation.xAxisDirection)
            assertEquals(RobotRelativeDirection.AWAY_FROM_ROBOT, orientation.yAxisDirection)
            assertEquals(RobotRelativeDirection.LEFT_OF_ROBOT, orientation.zAxisDirection)

            tvec = doubleArrayOf(-0.04072477101727981, 0.038743244580552026, 0.4757404092791874)
            rvec = doubleArrayOf(1.5183977631651149, 1.5473412942709308, -1.0094046166048305)
            marker = createMarker(qiContext, tvec, rvec)
            orientation = qiContext.actuation.async()
                    .computeRobotRelativeFrameOrientation(marker.frame).await()
            assertEquals(RobotRelativeDirection.UP, orientation.xAxisDirection)
            assertEquals(RobotRelativeDirection.TOWARD_ROBOT, orientation.yAxisDirection)
            assertEquals(RobotRelativeDirection.RIGHT_OF_ROBOT, orientation.zAxisDirection)

            tvec = doubleArrayOf(0.04272707585794839, 0.012823012388409917, 0.4495808723633766)
            rvec = doubleArrayOf(2.70697536386678, -4.867833456807776E-4, -1.3232219618155134)
            marker = createMarker(qiContext, tvec, rvec)
            orientation = qiContext.actuation.async()
                    .computeRobotRelativeFrameOrientation(marker.frame).await()
            assertEquals(RobotRelativeDirection.LEFT_OF_ROBOT, orientation.xAxisDirection)
            assertEquals(RobotRelativeDirection.TOWARD_ROBOT, orientation.yAxisDirection)
            assertEquals(RobotRelativeDirection.UP, orientation.zAxisDirection)
        }
    }

    @Test
    fun testSerializeDeserialize() = withRobotFocus(activityRule.activity) { qiContext ->

        runBlocking {
            localizeAndRunTest(qiContext) {
                val markerFrame = qiContext.mapping.async().mapFrame().await().async().makeAttachedFrame(
                        Transform(Quaternion(1.0, 0.0, 0.0, 1.0), Vector3(42.0, 2.0, 3.0))
                ).await().async().frame().await()
                val config = ArucoMarker.DetectionConfig(
                        0.15,
                        ArucoDictionary.DICT_4X4_100,
                        PepperRobotData.HEAD_CAMERA_MATRIX,
                        PepperRobotData.HEAD_CAMERA_DISTORTION_COEFS,
                        ArucoMarkerFrameLocalizationPolicy.ATTACHED_TO_MAPFRAME)
                val marker = ArucoMarker(42, markerFrame, config)

                val asString = marker.serialize(qiContext)
                val reconstructed = ArucoMarkerBuilder.with(qiContext)
                        .withMarkerString(asString).buildAsync().await()
                assertEquals(asString.toSortedSet(), reconstructed.serialize(qiContext).toSortedSet())
            }
        }
    }
}