package com.softbankrobotics.app.activities

import android.os.Bundle
import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aldebaran.qi.Future
import com.aldebaran.qi.Promise
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.LookAtMovementPolicy
import com.aldebaran.qi.sdk.`object`.holder.AutonomousAbilitiesType
import com.aldebaran.qi.sdk.builder.HolderBuilder
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.aldebaran.qi.sdk.builder.TransformBuilder
import com.softbankrobotics.app.R
import com.softbankrobotics.app.ui.ConversationBinder
import com.softbankrobotics.app.ui.ConversationItemType
import com.softbankrobotics.app.ui.LoadingDialog
import com.softbankrobotics.app.ui.MarkersViewAdapter
import com.softbankrobotics.dx.pepperextras.actuation.ExtraLookAt
import com.softbankrobotics.dx.pepperextras.actuation.ExtraLookAtBuilder
import com.softbankrobotics.dx.pepperextras.actuation.StubbornGoToBuilder
import com.softbankrobotics.dx.pepperextras.util.asyncFuture
import com.softbankrobotics.dx.pepperextras.util.await
import com.softbankrobotics.dx.pepperextras.util.awaitOrNull
import com.softbankrobotics.dx.pepperextras.util.TAG
import com.softbankrobotics.pepperaruco.actuation.*
import kotlinx.android.synthetic.main.activity_look_around_for_aruco.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
        markersView.layoutManager = LinearLayoutManager(this)
        markersView.adapter = markerAdapter
    }

    override suspend fun onRobotFocus(qiContext: QiContext) = coroutineScope {
        this@LookAroundForArucoActivity.qiContext = qiContext
        val holder = HolderBuilder.with(qiContext)
            .withAutonomousAbilities(AutonomousAbilitiesType.BASIC_AWARENESS,
                AutonomousAbilitiesType.BACKGROUND_MOVEMENT)
            .build()
        try {
            val conversationStatus = qiContext.conversation.status(qiContext.robotContext)
            conversationBinder = conversationView.bindConversationTo(conversationStatus)

            SayBuilder.with(qiContext)
                .withText("I can look for Aruco markers around me. Let me search for 4x4 Aruco Markers.")
                .buildAsync().await()
                .async().run().await()

            displayLine("Look around started", ConversationItemType.INFO_LOG)

            holder.async().hold().await()
            val markers = LookAroundAndDetectArucoMarkerBuilder.with(qiContext)
                .withMarkerLength(ARUCO_MARKER_SIZE / 100.0)
                .withDictionary(ArucoDictionary.DICT_4X4_50)
                .withArucoMarkerFrameLocalizationPolicy(ArucoMarkerFrameLocalizationPolicy.DETACHED)
                .withLookAtMovementPolicy(LookAtMovementPolicy.HEAD_AND_BASE)
                .withLookAtSphericalCoordinates(
                    90.0 to -90.0,
                    90.0 to -60.0,
                    90.0 to -30.0,
                    90.0 to 0.0,
                    90.0 to 30.0,
                    90.0 to 60.0,
                    90.0 to 90.0
                )
                .buildAsync().await()
                .apply {
                    async().addOnArucoMarkerDetectedListener(object :
                        LookAroundAndDetectArucoMarker.OnArucoMarkerDetectedListener {
                        override fun onArucoMarkerDetected(markers: Set<ArucoMarker>) {
                            runOnUiThread {
                                markerAdapter.addMarkers(markers)
                            }
                        }
                    }).await()
                }
                .async().run().await()
            displayLine("Look around stopped", ConversationItemType.INFO_LOG)


            if (markers.isNotEmpty()) {
                val plural = if (markers.size > 1) "s" else ""
                val msg =
                    "I found ${markers.size} marker${plural}. Use the Look At and Go To buttons to make me look and go to marker${plural}."
                SayBuilder.with(qiContext).withText(msg)
                    .buildAsync().await()
                    .async().run().await()
            } else {
                SayBuilder.with(qiContext)
                    .withText("I'm sorry, I did not find any Aruco markers around me.")
                    .buildAsync().await()
                    .async().run().await()
            }
            // Wait infinitely
            while (isActive) delay(1000)
        } catch (e: Throwable) {
            displayLine("Got an error: ${e}", ConversationItemType.ERROR_LOG)
        } finally {
            Log.i(TAG, "Done !!!")
            holder.async().release().awaitOrNull()
            conversationBinder?.unbind()
            this@LookAroundForArucoActivity.qiContext = null
        }
    }

    override fun onRobotFocusRefused(reason: String?) {
        displayLine("Robot focus refused ${reason}", ConversationItemType.ERROR_LOG)
    }

    private fun displayLine(text: String, type: ConversationItemType) {
        runOnUiThread { conversationView.addLine(text, type) }
    }

    fun goToArucoMarker(marker: ArucoMarker): Future<Unit> = appScope.asyncFuture {
        // We go one meter in front of marker
        val oneMeterXTransform = TransformBuilder.create().from2DTranslation(1.0, 0.0)
        runOnUiThread {
            loadingDialog.show("Going to marker ${marker.id}, please wait...")
        }
        val frame = marker.frame.async().makeAttachedFrame(oneMeterXTransform).await()
            .async().frame().await()
        StubbornGoToBuilder.with(qiContext!!)
            .withFrame(frame).withWalkingAnimationEnabled(true)
            .buildAsync().await()
            .async().run(this).await()
        ExtraLookAtBuilder.with(qiContext!!)
            .withFrame(frame)
            .withTerminationPolicy(ExtraLookAt.TerminationPolicy.TERMINATE_WHEN_LOOKING_AT_OR_NOT_MOVING_ANYMORE)
            .buildAsync().await()
            .async().run(this).await()
        runOnUiThread {
            loadingDialog.dismiss()
        }
    }

    fun lookAtArucoMarker(marker: ArucoMarker): Future<Unit> = appScope.asyncFuture {
        runOnUiThread {
            loadingDialog.show("Looking at marker ${marker.id}, please wait...")
        }
        ExtraLookAtBuilder.with(qiContext!!)
            .withFrame(marker.frame)
            .withTerminationPolicy(ExtraLookAt.TerminationPolicy.TERMINATE_WHEN_LOOKING_AT_OR_NOT_MOVING_ANYMORE)
            .buildAsync().await()
            .async().run(this).await()
        runOnUiThread {
            loadingDialog.dismiss()
        }
    }
}
