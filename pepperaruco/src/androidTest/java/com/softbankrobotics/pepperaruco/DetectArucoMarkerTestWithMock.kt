package com.softbankrobotics.pepperaruco

import android.content.Context
import android.graphics.*
import android.util.Log
import android.util.Size
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.camera.Camera
import com.aldebaran.qi.sdk.`object`.camera.TakePicture
import com.aldebaran.qi.sdk.`object`.image.EncodedImageHandle
import com.aldebaran.qi.sdk.`object`.image.TimestampedImageHandle
import com.softbankrobotics.dx.geometry.toApacheVector3D
import com.softbankrobotics.dx.util.QiSDKTestActivity
import com.softbankrobotics.dx.util.TAG
import com.softbankrobotics.dx.util.await
import com.softbankrobotics.dx.util.withRobotFocus
import com.softbankrobotics.pepperaruco.actuation.DetectArucoMarkerBuilder
import com.softbankrobotics.pepperaruco.actuation.RobotRelativeDirection
import com.softbankrobotics.pepperaruco.util.OpenCVUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer


fun encodeBitmap(bitmap: Bitmap): ByteBuffer {
    val byteArrayBitmapStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayBitmapStream)
    return ByteBuffer.wrap(byteArrayBitmapStream.toByteArray())
}

fun makeArucoImage(context: Context, resource: Int, size: Size): Bitmap {
    val arucoBitmap = BitmapFactory.decodeResource(context.resources, resource)
    return Bitmap.createScaledBitmap(arucoBitmap, size.width, size.height, true)
}

fun makeCameraImage(cameraImageSize: Size, config: Bitmap.Config): Bitmap {
    val cameraImage = Bitmap.createBitmap(cameraImageSize.width, cameraImageSize.height, config)
    val canvas = Canvas(cameraImage)
    canvas.drawColor(Color.WHITE)
    return cameraImage
}

fun drawToCameraImage(cameraImage: Bitmap,
                      arucoBitmap: Bitmap,
                      position: Point,
                      rotationAngle: Float,
                      direction: RobotRelativeDirection) {

    val canvas = Canvas(cameraImage)
    val matrix = Matrix()

    val src = floatArrayOf(
            0f, 0f, // top left point
            arucoBitmap.width.toFloat(), 0f, // top right point
            arucoBitmap.width.toFloat(), arucoBitmap.height.toFloat(), // bottom right point
            0f, arucoBitmap.height.toFloat()// bottom left point
    )
    // Bascule vers le haut
    val xmargin = arucoBitmap.width.toFloat() * 0.125f
    val ymargin = arucoBitmap.height.toFloat() * 0.62f
    val dst = when (direction) {
        RobotRelativeDirection.UP -> floatArrayOf(
                xmargin, ymargin, // top left point
                arucoBitmap.width.toFloat()-xmargin, ymargin, // top right point
                arucoBitmap.width.toFloat(), arucoBitmap.height.toFloat(), // bottom right point
                0f, arucoBitmap.height.toFloat() // bottom left point
        )
        RobotRelativeDirection.DOWN -> floatArrayOf(
                0f, 0f, // top left point
                arucoBitmap.width.toFloat(), 0f, // top right point
                arucoBitmap.width.toFloat()-xmargin, arucoBitmap.height.toFloat()-ymargin, // bottom right point
                xmargin, arucoBitmap.height.toFloat()-ymargin // bottom left point
        )
        RobotRelativeDirection.TOWARD_ROBOT -> src
        RobotRelativeDirection.RIGHT_OF_ROBOT -> floatArrayOf(
                0f, 0f, // top left point
                arucoBitmap.width.toFloat()-ymargin, xmargin, // top right point
                arucoBitmap.width.toFloat()-ymargin, arucoBitmap.height.toFloat()-xmargin, // bottom right point
                0f, arucoBitmap.height.toFloat() // bottom left point
        )
        RobotRelativeDirection.LEFT_OF_ROBOT -> floatArrayOf(
                ymargin, xmargin, // top left point
                arucoBitmap.width.toFloat(), 0f, // top right point
                arucoBitmap.width.toFloat(), arucoBitmap.height.toFloat(), // bottom right point
                ymargin, arucoBitmap.height.toFloat() -xmargin // bottom left point
        )
        RobotRelativeDirection.AWAY_FROM_ROBOT -> throw Exception("AWAY_FROM_ROBOT makes no sense")
    }

    matrix.setPolyToPoly(src, 0, dst, 0, 4)

    // Tourne dans le sens des aiguille d'une montre
    matrix.preRotate(rotationAngle, arucoBitmap.width / 2f, arucoBitmap.height / 2f) // Sens aiguille montre

    matrix.postTranslate(position.x.toFloat(), position.y.toFloat())
    canvas.drawBitmap(arucoBitmap, matrix, null)
}


@RunWith(AndroidJUnit4::class)
class DetectArucoMarkerTestWithMock {

    @get:Rule
    val activityRule = ActivityTestRule(QiSDKTestActivity::class.java)

    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    val aruco13 = makeArucoImage(appContext, R.drawable.marker13, Size(50, 50))
    val aruco36 = makeArucoImage(appContext, R.drawable.marker36, Size(100, 100))
    val aruco41 = makeArucoImage(appContext, R.drawable.marker41, Size(200, 200))
    lateinit var qiContextMock: QiContext
    var timestamp: Long = 12345
    lateinit var cameraImage : Bitmap

    fun setUpTest(qiContext: QiContext) {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        OpenCVUtils.loadOpenCV(appContext)

        val timestampedImageHandle = mockk<TimestampedImageHandle> {
            every { image } returns mockk<EncodedImageHandle> {
                every { async() } returns mockk {
                    every { value } returns Future.of(mockk {
                        every { data } returns encodeBitmap(cameraImage)
                    })
                }
            }
            every { time } returns timestamp
        }
        val takePicture = mockk<TakePicture> {
            every { async() } returns mockk<TakePicture.Async> {
                every { run() } returns Future.of(timestampedImageHandle)
            }
        }
        val cameraMock = mockk<Camera> {
            every { async() } returns mockk<Camera.Async> {
                every { makeTakePicture(any()) } returns Future.of(takePicture)
            }
        }
        qiContextMock = spyk(qiContext) {
            every { camera } returns (cameraMock)
        }
    }

    @Test
    fun testDetectOneMarker() = withRobotFocus(activityRule.activity) { qiContext ->

        cameraImage = makeCameraImage(Size(1280, 960), aruco36.config)
        drawToCameraImage(cameraImage, aruco36, Point(100, 100), 0f, RobotRelativeDirection.LEFT_OF_ROBOT)
        setUpTest(qiContext)


        runBlocking {
            val markers = DetectArucoMarkerBuilder
                    .with(qiContextMock).buildAsync().await()
                    .async().run().await()
            Assert.assertEquals(1, markers.size)
            Assert.assertEquals(36, markers.first().id)
        }
    }

    @Test
    fun testDetectSeveralMarkers() = withRobotFocus(activityRule.activity) { qiContext ->

        cameraImage = makeCameraImage(Size(1280, 960), aruco13.config)
        drawToCameraImage(cameraImage, aruco13, Point(100, 100), 90f, RobotRelativeDirection.DOWN)
        drawToCameraImage(cameraImage, aruco36, Point(300, 300), 180f, RobotRelativeDirection.UP)
        drawToCameraImage(cameraImage, aruco41, Point(600, 600), 270f, RobotRelativeDirection.LEFT_OF_ROBOT)
        setUpTest(qiContext)

        runBlocking {
            val markers = DetectArucoMarkerBuilder
                    .with(qiContextMock).buildAsync().await()
                    .async().run().await()
            Assert.assertEquals(3, markers.size)
            Assert.assertThat(markers.map { it.id }, containsInAnyOrder(13, 36, 41))
        }
    }

    @Test
    fun testMarkerDistance() = withRobotFocus(activityRule.activity) { qiContext ->

        cameraImage = makeCameraImage(Size(1280, 960), aruco36.config)
        drawToCameraImage(cameraImage, aruco36, Point(100, 100), 0f, RobotRelativeDirection.RIGHT_OF_ROBOT)
        setUpTest(qiContext)

        runBlocking {
            val markers = DetectArucoMarkerBuilder
                    .with(qiContextMock).buildAsync().await()
                    .async().run().await()
            Assert.assertEquals(1, markers.size)
            Assert.assertEquals(36, markers.first().id)

            val marker = markers.first()
            val t = marker.frame.computeTransform(qiContext.actuation.robotFrame()).transform
            Log.i(TAG, "Distance: ${t.translation.toApacheVector3D().norm}")
        }
    }


}