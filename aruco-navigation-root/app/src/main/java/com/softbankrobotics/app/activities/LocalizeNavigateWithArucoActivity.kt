package com.softbankrobotics.app.activities

import android.os.Bundle
import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.`object`.actuation.LookAtMovementPolicy
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.`object`.holder.AutonomousAbilitiesType
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.aldebaran.qi.sdk.builder.TransformBuilder
import com.softbankrobotics.app.R
import com.softbankrobotics.app.ui.ConversationBinder
import com.softbankrobotics.app.ui.ConversationItemType
import com.softbankrobotics.pepperaruco.*
import com.softbankrobotics.pepperaruco.util.GotoUtils
import com.softbankrobotics.pepperaruco.util.LookAtUtils
import com.softbankrobotics.pepperaruco.util.OpenCVUtils
import com.softbankrobotics.pepperaruco.util.SphericalCoord
import com.softbankrobotics.pepperaruco.util.TAG
import kotlinx.android.synthetic.main.activity_localize_navigate_with_aruco.*

class LocalizeNavigateWithArucoActivity : BaseActivity() {

    companion object {
        private val ARUCO_MARKER_SIZE = 15 // centimeters
        private val ONE_METER_X_TRANSFORM = TransformBuilder.create().from2DTranslation(1.0, 0.0)
    }

    override val layoutId: Int = R.layout.activity_localize_navigate_with_aruco
    private var conversationBinder: ConversationBinder? = null
    var trackRobotPositionFuture: Future<Unit>? = null
    private var qiContext: QiContext? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        QiSDK.register(this, this)
        OpenCVUtils.loadOpenCV(this)
    }

    override fun onDestroy() {
        // Unregister the RobotLifecycleCallbacks for this Activity.
        QiSDK.unregister(this, this)
        super.onDestroy()
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        val conversationStatus = qiContext.conversation.status(qiContext.robotContext)
        conversationBinder = conversationView.bindConversationTo(conversationStatus)
        this.qiContext = qiContext

        val say = SayBuilder.with(qiContext)
            .withText("I can use Aruco markers to localize and know where I am precisely. First I'll search for 4x4 Aruco Markers around me.")
            .build()
        say.run()

        // Makes sure Pepper stays still
        holdAutonomousAbilities(
            qiContext,
            AutonomousAbilitiesType.BASIC_AWARENESS,
            AutonomousAbilitiesType.BACKGROUND_MOVEMENT
        )

        // Init the origin of the map
        qiContext.actuation.async().robotFrame().andThenCompose { robotFrame ->
            qiContext.makeDetachedFrame(robotFrame).andThenConsume { robotDetachedFrame ->
                mapView.setMapOrigin(robotDetachedFrame)

                trackRobotPositionFuture = mapView.trackRobotPosition(qiContext)

                qiContext.arucoDetection.addOnArucoMarkerDetectedListener(object : OnArucoMarkerDetectedListerner {
                    override fun onArucoMarkerDetected(arucoMarker: ArucoMarker, detectionData: ArucoMarkerDetectionData) {
                        // Whenever we detect a Marker, redisplay all markers so that we see all positions are updated
                        qiContext.arucoDetection.markers.values.forEach { marker ->
                            mapView.addMarker(marker)
                        }
                    }
                })
            }
        }.thenCompose {
            displayLine("Look around started", ConversationItemType.INFO_LOG)

            // Find all markers
            findAllMarkers(qiContext)

        }.thenCompose { f ->
            if (!f.isCancelled and !f.hasError()) {
                displayLine("Look around stopped", ConversationItemType.INFO_LOG)

                val markers = f.value
                val msg = if (markers.size <= 1)
                    "I'm sorry, I did not find enough Aruco markers around me. I need to see at least 2 markers to" +
                            " show you how I navigate and localize."
                else
                    "I found ${markers.size} markers. You can see their position on the map. I will now navigate between them.\n" +
                            "The RED triangle represent my position as returned by the odometry.\n" +
                            "The PINK square represent the frames I go to, or look at.\n" +
                            "The BLACK square represent the markers frames.\n" +
                            "To stop me, click on the cross or the back arrow."

                SayBuilder.with(qiContext).withText(msg).build().async().run().thenApply { markers.size > 1 }
            }
            else {
                Log.e(TAG, f.errorMessage)
                Future.of(false)
            }
        }.andThenConsume { enoughMarkersToNavigate ->

            if (enoughMarkersToNavigate) {
                // And finally navigate between them.
                var future = Future.of<Void>(null)

                // 4) Repeat 1000 times...
                for (i in 1..1000) {
                    // ... for each marker
                    qiContext.arucoDetection.markers.values.forEach { marker ->
                        future = future.andThenCompose {
                            Log.i(TAG, "GO TO MARKER ${marker.id}")
                            // Go to that marker
                            goToMarker(qiContext, marker.frame).thenCompose {
                                qiContext.arucoDetection.detectMarkerWithTopCamera().andThenConsume{}
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onRobotFocusLost() {
        trackRobotPositionFuture?.cancel(true)
        qiContext?.arucoDetection?.removeAllOnArucoMarkerDetectedListeners()
        this.qiContext = null
    }

    override fun onRobotFocusRefused(reason: String?) {
        displayLine("Robot focus refused", ConversationItemType.ERROR_LOG)
    }

    private fun displayLine(text: String, type: ConversationItemType) {
        runOnUiThread { conversationView.addLine(text, type) }
    }

    private fun createLookAroundHeadPositions(phis: DoubleArray, thetas: DoubleArray): List<Transform> {
        var res = listOf<Transform>()
        for (theta in thetas) {
            res += phis.map { phi ->
                val c = SphericalCoord(
                    1.0,
                    Math.toRadians(theta),
                    Math.toRadians(phi)
                )
                LookAtUtils.lookAtTransformFromSphericalCoord(c)
            }
        }
        return res
    }

    private fun findAllMarkers(qiContext: QiContext): Future<Set<ArucoMarker>> {
        // Configure the detection with the markers parameters
        val detectConfig = DetectArucoConfig()
        detectConfig.markerLength = ARUCO_MARKER_SIZE / 100.0
        detectConfig.dictionary = ArucoDictionary.DICT_4X4_50
        val lookAroundConfig = LookAroundConfig()
        // Configure how pepper will look around
        lookAroundConfig.lookAtPolicy = LookAtMovementPolicy.HEAD_AND_BASE
        // Configure Pepper head position during look around.
        lookAroundConfig.lookAtTransform = createLookAroundHeadPositions(
            doubleArrayOf(0.0, 40.0, 80.0, 120.0, 160.0, 200.0, 240.0, 280.0, 320.0, 360.0),
            doubleArrayOf(90.0)
        )
        return qiContext.arucoDetection.lookAroundForMarker(lookAroundConfig, detectConfig)
    }

    var firstTimeLookAt = true

    private fun goToMarker(qiContext: QiContext, markerFrame: Frame): Future<Void> {
        val inFrontOfMarkerFrame = qiContext.mapping.makeFreeFrame()
        inFrontOfMarkerFrame.update(markerFrame, ONE_METER_X_TRANSFORM, 0)
        mapView.setTarget(inFrontOfMarkerFrame.frame())

        return GotoUtils.goToFrame(qiContext, inFrontOfMarkerFrame.frame()).thenCompose { f ->
            mapView.setTarget(markerFrame)

            if (firstTimeLookAt) {
                firstTimeLookAt = false
                val msg = "Once I arrive in front of a marker, I look at it. This allows me to correct the position of the markers with respect to my odometry frame that drifts. Old markers positions will be displayed in GRAY."
                SayBuilder.with(qiContext).withText(msg).build().async().run()
                    .thenCompose { LookAtUtils.lookAtFrame(qiContext, markerFrame) }
            }
            else
                LookAtUtils.lookAtFrame(qiContext, markerFrame)
        }
    }
}