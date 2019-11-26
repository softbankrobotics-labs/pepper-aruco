package com.softbankrobotics.app.activities

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.`object`.actuation.LookAtMovementPolicy
import com.aldebaran.qi.sdk.`object`.holder.AutonomousAbilitiesType
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.aldebaran.qi.sdk.builder.TransformBuilder
import com.softbankrobotics.app.R
import com.softbankrobotics.app.ui.ConversationBinder
import com.softbankrobotics.app.ui.ConversationItemType
import com.softbankrobotics.app.ui.LoadingDialog
import com.softbankrobotics.app.ui.MarkersViewAdapter
import com.softbankrobotics.pepperaruco.*
import com.softbankrobotics.pepperaruco.util.GotoUtils
import com.softbankrobotics.pepperaruco.util.LookAtUtils
import com.softbankrobotics.pepperaruco.util.LookAtUtils.lookAtFrame
import com.softbankrobotics.pepperaruco.util.OpenCVUtils
import com.softbankrobotics.pepperaruco.util.SphericalCoord
import kotlinx.android.synthetic.main.activity_look_around_for_aruco.*

class LookAroundForArucoActivity : BaseActivity() {

    companion object {
        private val ARUCO_MARKER_SIZE = 15 // centimeters
    }

    override val layoutId: Int = R.layout.activity_look_around_for_aruco
    private var conversationBinder: ConversationBinder? = null
    private val markerAdapter by lazy { MarkersViewAdapter(::lookAtArucoMarker, ::goToArucoMarker) }
    private val loadingDialog by lazy { LoadingDialog(this) }
    private var qiContext: QiContext? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        QiSDK.register(this, this)
        OpenCVUtils.loadOpenCV(this)
        markersView.layoutManager = LinearLayoutManager(this) as RecyclerView.LayoutManager
        markersView.adapter = markerAdapter
    }

    override fun onDestroy() {
        // Unregister the RobotLifecycleCallbacks for this Activity.
        QiSDK.unregister(this, this)
        super.onDestroy()
    }

    var lookAroundFuture: Future<Set<ArucoMarker>>? = null

    override fun onRobotFocusGained(qiContext: QiContext) {
        this.qiContext = qiContext

        val conversationStatus = qiContext.conversation.status(qiContext.robotContext)
        conversationBinder = conversationView.bindConversationTo(conversationStatus)

        val say = SayBuilder.with(qiContext)
            .withText("I can look for Aruco markers around me. Let me search for 4x4 Aruco Markers.")
            .build()
        say.run()
        holdAutonomousAbilities(qiContext, AutonomousAbilitiesType.BASIC_AWARENESS, AutonomousAbilitiesType.BACKGROUND_MOVEMENT)

        displayLine("Look around started", ConversationItemType.INFO_LOG)

        // Configure the detection with the markers parameters
        val detectConfig = DetectArucoConfig()
        detectConfig.markerLength = LookAroundForArucoActivity.ARUCO_MARKER_SIZE / 100.0
        detectConfig.dictionary = ArucoDictionary.DICT_4X4_50
        val lookAroundConfig = LookAroundConfig()
        // Configure how pepper will look around
        lookAroundConfig.lookAtPolicy = LookAtMovementPolicy.HEAD_AND_BASE
        // Configure Pepper head position during look around: here pepper will go through the following
        //  positions (in spherical coordinates in degree):
        //    (-90, 90), (-60, 90), (-30, 90), (0, 90), (30, 90), (60, 90), (90, 90),
        val phi = 90.0
        val thetas = doubleArrayOf(-90.0, -60.0, -30.0, 0.0, 30.0, 60.0, 90.0)
        lookAroundConfig.lookAtTransform = thetas.map{
                LookAtUtils.lookAtTransformFromSphericalCoord(
                    SphericalCoord(
                        1.0,
                        Math.toRadians(phi),
                        Math.toRadians(it)
                    )
                )
            }

        qiContext.arucoDetection.addOnArucoMarkerDetectedListener(object : OnArucoMarkerDetectedListerner {
            override fun onArucoMarkerDetected(arucoMarker: ArucoMarker, detectionData: ArucoMarkerDetectionData) {
                runOnUiThread {
                    markerAdapter.addMarker(arucoMarker, detectionData)
                }
            }
        })

        lookAroundFuture = qiContext.arucoDetection.lookAroundForMarker(lookAroundConfig, detectConfig)
        lookAroundFuture?.thenConsume {f ->
            if (!f.isCancelled and !f.hasError()) {
                displayLine("Look around stopped", ConversationItemType.INFO_LOG)

                val arucos = f.value
                if (arucos.isNotEmpty()) {
                    val plural = if (arucos.size > 1) "s" else ""
                    val msg = "I found ${arucos.size} marker${plural}. Use the Look At and Go To buttons to make me look and go to marker${plural}."
                    SayBuilder.with(qiContext)
                        .withText(msg)
                        .build()
                        .async().run()
                }
                else {
                    SayBuilder.with(qiContext)
                        .withText("I'm sorry, I did not find any Aruco markers around me.")
                        .build()
                        .async().run()
                }
            }
        }
    }

    override fun onRobotFocusLost() {
        conversationBinder?.unbind()
        lookAroundFuture?.cancel(true)
        qiContext?.arucoDetection?.removeAllOnArucoMarkerDetectedListeners()
        this.qiContext = null
    }

    override fun onRobotFocusRefused(reason: String?) {
        displayLine("Robot focus refused", ConversationItemType.ERROR_LOG)
    }

    private fun displayLine(text: String, type: ConversationItemType) {
        runOnUiThread { conversationView.addLine(text, type) }
    }

    fun goToArucoMarker(marker: ArucoMarker): Future<Void> {
        // We go one meter in front of marker
        val oneMeterXTransform = TransformBuilder.create().from2DTranslation(1.0, 0.0)
        loadingDialog.show("Going to marker ${marker.id}, please wait...")
        return marker.frame.async().makeAttachedFrame(oneMeterXTransform).andThenCompose { frame ->
            GotoUtils.goToFrame(qiContext!!, frame.frame()).thenCompose {
                lookAtFrame(qiContext!!, marker.frame).thenConsume {
                    loadingDialog.dismiss()
                }
            }
        }
    }

    fun lookAtArucoMarker(marker: ArucoMarker): Future<Void> {
        loadingDialog.show("Looking at marker ${marker.id}, please wait...")
        return lookAtFrame(qiContext!!, marker.frame).thenConsume {
            loadingDialog.dismiss()
        }
    }

}
