package com.softbankrobotics.app.activities

import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.Promise
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.ExplorationMap
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.`object`.actuation.LocalizationStatus
import com.aldebaran.qi.sdk.`object`.actuation.OrientationPolicy
import com.aldebaran.qi.sdk.`object`.geometry.Quaternion
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.`object`.holder.AutonomousAbilitiesType
import com.aldebaran.qi.sdk.`object`.human.Human
import com.aldebaran.qi.sdk.builder.HolderBuilder
import com.aldebaran.qi.sdk.builder.LocalizeAndMapBuilder
import com.aldebaran.qi.sdk.builder.LocalizeBuilder
import com.aldebaran.qi.sdk.builder.LookAtBuilder
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.aldebaran.qi.sdk.builder.TransformBuilder
import com.softbankrobotics.app.R
import com.softbankrobotics.app.ui.ConversationBinder
import com.softbankrobotics.app.ui.ConversationItemType
import com.softbankrobotics.dx.pepperextras.actuation.StubbornGoToBuilder
import com.softbankrobotics.dx.pepperextras.actuation.makeDetachedFrame
import com.softbankrobotics.dx.pepperextras.geometry.div
import com.softbankrobotics.dx.pepperextras.geometry.minus
import com.softbankrobotics.dx.pepperextras.geometry.toApacheVector3D
import com.softbankrobotics.dx.pepperextras.util.TAG
import com.softbankrobotics.dx.pepperextras.util.await
import com.softbankrobotics.dx.pepperextras.util.awaitOrNull
import com.softbankrobotics.pepperaruco.actuation.ArucoDictionary
import com.softbankrobotics.pepperaruco.actuation.ArucoMarker
import com.softbankrobotics.pepperaruco.actuation.DetectArucoMarkerBuilder
import com.softbankrobotics.pepperaruco.actuation.GoToMarkerAndCheckItsPositionOnTheWayBuilder
import kotlinx.android.synthetic.main.activity_engage_and_return_to_marker.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


class EngageAndReturnToMarkerActivity : BaseActivity() {

    companion object {
        /** Size of the Aruco markers pepper will detect, in meter. */
        private val ARUCO_MARKER_SIZE = 0.15

        /** Dictionary of Aruco markers to use for the detection. */
        private val ARUCO_DICT = ArucoDictionary.DICT_4X4_50

        /** A Transform that move a Frame by 1 meter on the X axis */
        private val ONE_METER_X_TRANSFORM = TransformBuilder.create().from2DTranslation(1.0, 0.0)
    }

    override val layoutId: Int = R.layout.activity_engage_and_return_to_marker
    private var conversationBinder: ConversationBinder? = null

    override suspend fun onRobotFocus(qiContext: QiContext) {
        val holder = HolderBuilder.with(qiContext)
            .withAutonomousAbilities(AutonomousAbilitiesType.BASIC_AWARENESS)
            .build()
        /** The Localize action. We use a Localize action only to improve the human detection.
         * To localize we use Aruco markers. */
        var localizing: Future<Void>? = null
        try {
            val conversationStatus = qiContext.conversation.status(qiContext.robotContext)
            conversationBinder = conversationView.bindConversationTo(conversationStatus)

            say(qiContext, "Please place a 4x4 Aruco Marker on the floor in front of me.")

            Log.d(TAG, "STATE: hold basic awareness")

            holder.async().hold().await()

            Log.d(TAG, "STATE: detect marker")
            displayLine("Detection started", ConversationItemType.INFO_LOG)
            val marker = waitToDetectAMarkerInFront(qiContext)
            displayLine("Detection stopped", ConversationItemType.INFO_LOG)
            say(
                qiContext,
                "I see marker ${marker.id}, this marker will represent my home position."
            )

            Log.d(TAG, "STATE: Go to marker")
            displayLine("Going to marker", ConversationItemType.INFO_LOG)

            // Rotate marker so that X comes to Z axis, so that robot align correctly
            val transformRotationY =
                TransformBuilder.create().fromRotation(Quaternion(0.0, -0.707, 0.0, 0.707))
            val freeFrame = qiContext.mapping.async().makeFreeFrame().await()
            freeFrame.async().update(marker.frame, transformRotationY, 0).await()
            val rotatedMarkerFrame = freeFrame.async().frame().await()
            goToFrame(qiContext, rotatedMarkerFrame)

            say(
                qiContext,
                "When I detect someone, I will go salute him, and come back to this position."
            )
            say(
                qiContext,
                "Let me start the mapping so that I will detect people around me more efficiently"
            )

            Log.d(TAG, "STATE: map environment")
            displayLine("Mapping...", ConversationItemType.INFO_LOG)
            val explorationMap = try {
                mapEnvironment(qiContext)
            } catch (e: Throwable) {
                displayLine("Mapping finished with error. $e", ConversationItemType.ERROR_LOG)
                return
            }
            displayLine("Robot has mapped his environment", ConversationItemType.INFO_LOG)

            Log.d(TAG, "STATE: localize in environment")
            displayLine("Localizing...", ConversationItemType.INFO_LOG)
            try {
                localizing = startLocalizing(qiContext, explorationMap)
            } catch (e: Throwable) {
                displayLine("Localize finished with error. $e", ConversationItemType.ERROR_LOG)
                return
            }
            displayLine("Robot is localized", ConversationItemType.INFO_LOG)

            Log.d(TAG, "STATE: align with marker")
            goToFrame(qiContext, rotatedMarkerFrame)
            holder?.async()?.release()?.awaitOrNull()

            displayLine("Detecting humans...", ConversationItemType.INFO_LOG)
            detectAndGoToHumanThenGoBackMarkerLoop(qiContext, marker)

        } catch (e: Throwable) {
            displayLine("Got an error: ${e}", ConversationItemType.ERROR_LOG)
        } finally {
            localizing?.apply { requestCancellation() }?.awaitOrNull()
            holder.async().release().awaitOrNull()
            conversationBinder?.unbind()
        }
    }

    private suspend fun detectAndGoToHumanThenGoBackMarkerLoop(qiContext: QiContext, arucoMarker: ArucoMarker) = coroutineScope {
        var marker = arucoMarker

        while (isActive) {
            Log.d(TAG, "STATE: wait to detect human")
            val human = waitToDetectHuman(qiContext)
            say(qiContext, "I see someone")

            // Go toward him
            displayLine("GoTo Human started", ConversationItemType.INFO_LOG)
            Log.d(TAG, "STATE: go to human")
            goToHuman(qiContext, human)

            Log.d(TAG, "STATE: look at human")

            // Look at him and salute him
            LookAtBuilder.with(qiContext).withFrame(human.headFrame).buildAsync().await()
                .async().run().also {
                    say(qiContext, "Hello")
                }.apply { requestCancellation() }.awaitOrNull()

            // Go back close to marker if too far away
            val translationToMarker =
                computeTransformToMarker(qiContext, arucoMarker.frame).translation
            val distance = translationToMarker.toApacheVector3D().norm
            if (distance > 1.5) {
                // Go at 1.5m from marker
                val translationOneMeterFromMarker =
                    translationToMarker - (translationToMarker / (distance / 1.5))
                val transformOneMeterFromMarker = TransformBuilder.create().from2DTranslation(
                    translationOneMeterFromMarker.x,
                    translationOneMeterFromMarker.y
                )
                Log.d(TAG, "STATE: go close marker")

                Log.d(
                    TAG,
                    "TRANSLATION ${translationOneMeterFromMarker.x} ${translationOneMeterFromMarker.y}"
                )
                val robotFrame = qiContext.actuationAsync.await().async().robotFrame().await()
                val frame = qiContext.mappingAsync.await().async()
                    .makeDetachedFrame(robotFrame, transformOneMeterFromMarker).await()
                displayLine("Go close to Marker started", ConversationItemType.INFO_LOG)
                goToFrame(qiContext, frame)
            }

            Log.d(TAG, "STATE: go to marker")
            displayLine("Going back to marker...", ConversationItemType.INFO_LOG)

            val (markerReached, detectedMarker) = GoToMarkerAndCheckItsPositionOnTheWayBuilder.with(
                qiContext
            )
                .withMarker(marker)
                .withWalkingAnimationEnabled(true)
                .withMarkerAlignmentEnabled(true)
                .buildAsync().await()
                .async().run().await()

            if (markerReached)
                displayLine("GoTo finished with success", ConversationItemType.INFO_LOG)
            else
                displayLine("GoTo did not finished with success", ConversationItemType.ERROR_LOG)
            if (detectedMarker != null)
                marker = detectedMarker
        }
    }

    override fun onRobotFocusRefused(reason: String?) {
        displayLine("Robot focus refused ${reason}", ConversationItemType.ERROR_LOG)
    }

    private suspend fun goToFrame(qiContext: QiContext, frame: Frame) {
        try {
            val success = StubbornGoToBuilder.with(qiContext)
                .withWalkingAnimationEnabled(true)
                .withMaxSpeed(0.6f)
                .withFinalOrientationPolicy(OrientationPolicy.ALIGN_X)
                .withFrame(frame)
                .buildAsync().await()
                .async().run().await()
            if (success)
                displayLine("GoTo finished with success", ConversationItemType.INFO_LOG)
            else
                displayLine("GoTo did not finished with success", ConversationItemType.ERROR_LOG)
        } catch (e: Throwable) {
            displayLine("GoTo finished with error. $e", ConversationItemType.ERROR_LOG)
        }
    }

    /**
     * Compute the transform between the robotFrame and the marker Frame.
     * Usefull to compute:
     * - the distance between the robot and the marker
     * - the path to the marker
     */
    private suspend fun computeTransformToMarker(qiContext: QiContext, markerFrame: Frame): Transform {
        val robotFrame = qiContext.actuationAsync.await().async().robotFrame().await()
        return markerFrame.async().computeTransform(robotFrame).await().transform
    }

    suspend fun say(qiContext: QiContext, message: String) {
        SayBuilder.with(qiContext).withText(message).buildAsync().await().async().run().await()
    }

    /**
     *  Wait for basic awareness to trigger human around signal
     */
    private suspend fun waitToDetectHuman(qiContext: QiContext): Human = coroutineScope {
        val humanAwareness = qiContext.humanAwarenessAsync.await()
        val humansAround = humanAwareness.async().humansAround.await()
        if (humansAround.size > 0)
            return@coroutineScope humansAround[0]
        val promise = Promise<Human>()
        humanAwareness.async().addOnHumansAroundChangedListener { humans ->
            if (humans.size > 0) {
                launch {
                    humanAwareness.async().removeAllOnHumansAroundChangedListeners().awaitOrNull()
                    promise.setValue(humans[0])
                }
            }
        }
        promise.future.await()
    }

    /**
     * Go to human frame.
     */
    private suspend fun goToHuman(qiContext: QiContext, human: Human) {
        val transform50cm = TransformBuilder.create().from2DTranslation(0.5, 0.0)
        val inFrontOfHuman = human.async().headFrame.await()
            .async().makeAttachedFrame(transform50cm).await().async().frame().await()
        goToFrame(qiContext, inFrontOfHuman)
    }

    /**
     * Look at the floor, 1 meter in front of Pepper, and wait to detect an aruco marker with the
     * top camera.
     */
    suspend fun waitToDetectAMarkerInFront(qiContext: QiContext): ArucoMarker {
        val robotFrame = qiContext.actuation.async().robotFrame().await()
        val oneMeterInFrontFrame =
            robotFrame.async().makeAttachedFrame(ONE_METER_X_TRANSFORM).await().async().frame()
                .await()
        val lookAtFuture = LookAtBuilder.with(qiContext)
            .withFrame(oneMeterInFrontFrame).buildAsync().await()
            .async().run()
        try {
            var markers: Set<ArucoMarker>
            while (true) {
                markers = DetectArucoMarkerBuilder.with(qiContext)
                    .withDictionary(ARUCO_DICT)
                    .withMarkerLength(ARUCO_MARKER_SIZE)
                    .buildAsync().await().async().run().await()
                if (markers.size > 0) {
                    return markers.first()
                }
            }
        } finally {
            lookAtFuture.apply { requestCancellation() }.awaitOrNull()
        }
    }


    /**
     * Create a map of the environment using the LocalizeAndMap action.
     * This function returns only when the mapping is finished.
     * @return A future to an ExplorationMap, that will be usable with a Localize action.
     */
    private suspend fun mapEnvironment(qiContext: QiContext): ExplorationMap = coroutineScope {
        // Start a LocalizeAndMap action
        val onLocalizedPromised = Promise<Unit>()
        val localizeAndMap = LocalizeAndMapBuilder.with(qiContext).buildAsync().await().apply {
            addOnStatusChangedListener { status ->
                if (status == LocalizationStatus.LOCALIZED)
                    onLocalizedPromised.setValue(Unit)
            }
        }
        val localizeAndMapFuture = localizeAndMap.async().run().thenConsume {
            localizeAndMap.removeAllOnStatusChangedListeners()
            if (it.hasError() && !onLocalizedPromised.future.isDone)
                onLocalizedPromised.setError(it.errorMessage)
        }
        // Wait for robot to be localized
        onLocalizedPromised.future.await()
        val map = localizeAndMap.dumpMap()
        localizeAndMap.removeAllOnStatusChangedListeners()
        localizeAndMapFuture.apply { requestCancellation() }.awaitOrNull()
        map
    }

    /**
     * Start a Localize action and immediately return.
     * @return A future containing the localize action. Use this future to terminate the localize action.
     */
    private suspend fun startLocalizing(qiContext: QiContext, explorationMap: ExplorationMap): Future<Void> {

        // Start a Localize action
        val onLocalizedPromised = Promise<Unit>()
        val localize = LocalizeBuilder.with(qiContext).withMap(explorationMap)
            .buildAsync().await().apply {
            addOnStatusChangedListener { status ->
                if (status == LocalizationStatus.LOCALIZED)
                    onLocalizedPromised.setValue(Unit)
            }
        }
        val localizeFuture = localize.async().run().thenConsume {
            localize.removeAllOnStatusChangedListeners()
            if (it.hasError() && !onLocalizedPromised.future.isDone)
                onLocalizedPromised.setError(it.errorMessage)
        }
        // Wait for robot to be localized
        onLocalizedPromised.future.await()
        return localizeFuture
    }

    /**
     * Displays a line of text in the Conversation view. Use it to display the status of the robot.
     */
    private fun displayLine(text: String, type: ConversationItemType) {
        runOnUiThread { conversationView.addLine(text, type) }
    }
}