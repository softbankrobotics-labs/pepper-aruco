package com.softbankrobotics.app.activities

import android.os.Bundle
import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.`object`.holder.AutonomousAbilitiesType
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.softbankrobotics.app.R
import com.softbankrobotics.app.ui.ConversationBinder
import com.softbankrobotics.app.ui.ConversationItemType
import com.softbankrobotics.pepperaruco.*
import com.softbankrobotics.pepperaruco.util.OpenCVUtils.loadOpenCV
import com.softbankrobotics.pepperaruco.util.TAG
import kotlinx.android.synthetic.main.activity_detect_aruco.*

class DetectArucoActivity : BaseActivity() {

    companion object {
        private val ARUCO_MARKER_SIZE = 15 // centimeters
    }

    override val layoutId: Int = R.layout.activity_detect_aruco
    private var conversationBinder: ConversationBinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        QiSDK.register(this, this)
        loadOpenCV(this)
    }

    override fun onDestroy() {
        // Unregister the RobotLifecycleCallbacks for this Activity.
        QiSDK.unregister(this, this)
        super.onDestroy()
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        val conversationStatus = qiContext.conversation.status(qiContext.robotContext)
        conversationBinder = conversationView.bindConversationTo(conversationStatus)

        val say = SayBuilder.with(qiContext)
            .withText("I can detect Aruco markers. Please show me a 4x4 Aruco Marker of width ${ARUCO_MARKER_SIZE} centimeters.")
            .build()
        say.run()
        holdAutonomousAbilities(qiContext, AutonomousAbilitiesType.BASIC_AWARENESS)

        displayLine("Detection started", ConversationItemType.INFO_LOG)
        detectMarkersLoop(qiContext)
    }

    override fun onRobotFocusLost() {
        conversationBinder?.unbind()
    }

    override fun onRobotFocusRefused(reason: String?) {
        displayLine("Robot focus refused", ConversationItemType.ERROR_LOG)
    }

    fun detectMarkersLoop(qiContext: QiContext) {

        val config = DetectArucoConfig()
        config.markerLength = ARUCO_MARKER_SIZE / 100.0
        config.dictionary = ArucoDictionary.DICT_4X4_50
        val f = qiContext.arucoDetection.detectMarkerWithTopCamera(config)

        f.thenConsume { markersFuture ->
            if (markersFuture.hasError())
                displayLine(markersFuture.errorMessage, ConversationItemType.ERROR_LOG)
            else if (markersFuture.isCancelled)
                displayLine("Detection cancelled", ConversationItemType.INFO_LOG)
            else {
                val markers = markersFuture.value
                Log.i(TAG, "Found ${markers.size} markers")
                sayMarkersDetected(qiContext, markers).andThenConsume { detectMarkersLoop(qiContext) }
            }
        }
    }

    fun sayMarkersDetected(qiContext: QiContext, markers: Set<ArucoMarker>): Future<Void> {
        if (markers.isEmpty())
            return Future.of(null)

        val plural = if (markers.size > 1) "s" else ""
        val m = markers.map { it.id.toString() }.joinToString(", ")
        val message = "I detect the marker${plural} with id${plural}: ${m}"
        return SayBuilder.with(qiContext).withText(message).buildAsync().andThenCompose { say ->
            say.async().run()
        }
    }

    private fun displayLine(text: String, type: ConversationItemType) {
        runOnUiThread { conversationView.addLine(text, type) }
    }
}
