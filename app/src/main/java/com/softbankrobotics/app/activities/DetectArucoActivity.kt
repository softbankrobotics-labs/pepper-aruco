package com.softbankrobotics.app.activities

import android.util.Log
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.holder.AutonomousAbilitiesType
import com.aldebaran.qi.sdk.`object`.holder.Holder
import com.aldebaran.qi.sdk.builder.HolderBuilder
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.softbankrobotics.app.R
import com.softbankrobotics.app.ui.ConversationBinder
import com.softbankrobotics.app.ui.ConversationItemType
import com.softbankrobotics.dx.pepperextras.geometry.toApacheVector3D
import com.softbankrobotics.dx.pepperextras.util.TAG
import com.softbankrobotics.dx.pepperextras.util.await
import com.softbankrobotics.dx.pepperextras.util.awaitOrNull
import com.softbankrobotics.pepperaruco.actuation.ArucoDictionary
import com.softbankrobotics.pepperaruco.actuation.DetectArucoMarkerBuilder
import com.softbankrobotics.pepperaruco.actuation.FrameOrientation
import com.softbankrobotics.pepperaruco.actuation.computeFrameOrientation
import com.softbankrobotics.pepperaruco.actuation.validateArucoMarkerFrameOrientation
import kotlinx.android.synthetic.main.activity_detect_aruco.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import java.util.concurrent.CancellationException

class DetectArucoActivity : BaseActivity() {

    companion object {
        private val ARUCO_MARKER_SIZE = 15 // centimeters
    }

    override val layoutId: Int = R.layout.activity_detect_aruco
    private var conversationBinder: ConversationBinder? = null

    override suspend fun onRobotFocus(qiContext: QiContext) {
        val holder: Holder = HolderBuilder.with(qiContext)
            .withAutonomousAbilities(AutonomousAbilitiesType.BASIC_AWARENESS)
            .build()
        try {
            val conversationStatus = qiContext.conversation.status(qiContext.robotContext)
            conversationBinder = conversationView.bindConversationTo(conversationStatus)

            SayBuilder.with(qiContext)
                .withText("I can detect Aruco markers. Please show me a 4x4 Aruco Marker of width ${ARUCO_MARKER_SIZE} centimeters.")
                .buildAsync().await()
                .async().run().await()

            holder.async().hold().await()

            displayLine("Detection started", ConversationItemType.INFO_LOG)
            detectMarkersLoop(qiContext)
        } catch (e: Throwable) {
            displayLine("Got an error: ${e}", ConversationItemType.ERROR_LOG)
        } finally {
            holder.async().release().awaitOrNull()
            conversationBinder?.unbind()
        }
    }

    override fun onRobotFocusRefused(reason: String?) {
        displayLine("Robot focus refused ${reason}", ConversationItemType.ERROR_LOG)
    }

    suspend fun detectMarkersLoop(qiContext: QiContext) = coroutineScope {
        while (isActive) {
            try {
                val actuation = qiContext.actuationAsync.await()
                val robotFrame = actuation.async().robotFrame().await()

                val markers = DetectArucoMarkerBuilder.with(qiContext)
                    .withMarkerLength(ARUCO_MARKER_SIZE / 100.0)
                    .withDictionary(ArucoDictionary.DICT_4X4_50)
                    .buildAsync().await()
                    .async().run().await()
                Log.i(TAG, "Found ${markers.size} markers")
                if (markers.isNotEmpty()) {
                    val marker = markers.first()
                    val robotToMarkerTransform =
                        marker.frame.async().computeTransform(robotFrame).await().transform
                    val dist =
                        Math.round(robotToMarkerTransform.translation.toApacheVector3D().norm * 100) / 100.0
                    frameViewSide.setMarker(robotToMarkerTransform)
                    frameViewTop.setMarker(robotToMarkerTransform)
                    frameViewBack.setMarker(robotToMarkerTransform)

                    val orientation =
                        actuation.async().computeFrameOrientation(marker.frame).await()
                    val isValid = actuation.async()
                        .validateArucoMarkerFrameOrientation(marker.frame, orientation).await()

                    val validTxt = if (orientation == FrameOrientation.HORIZONTAL)
                        "parallel to floor & perpendicular to walls"
                    else
                        "parallel to walls & perpendicular to floor"

                    runOnUiThread {
                        marker.detectionData?.let { cameraImage.setImageBitmap(it.image) }
                        markerInfo.setText(
                            "Infos:\n"
                                    + " • id: ${marker.id}\n"
                                    + " • distance: ${dist}m\n"
                                    + " • orientation: ${orientation}\n"
                                    + " • frame seems valid (${validTxt})?: ${isValid}"
                        )
                    }

                    val message = "I detect the marker with id: ${marker.id}"
                    SayBuilder.with(qiContext).withText(message)
                        .buildAsync().await()
                        .async().run().await()
                }

            } catch (e: CancellationException) {
                displayLine(e.message ?: "", ConversationItemType.INFO_LOG)
            } catch (e: Throwable) {
                displayLine(e.message ?: "", ConversationItemType.ERROR_LOG)
            }
        }
    }

    private fun displayLine(text: String, type: ConversationItemType) {
        runOnUiThread { conversationView.addLine(text, type) }
    }
}
