package com.softbankrobotics.app.activities

import android.os.Bundle
import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.Promise
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.`object`.actuation.*
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.`object`.geometry.Vector3
import com.aldebaran.qi.sdk.`object`.holder.AutonomousAbilitiesType
import com.aldebaran.qi.sdk.`object`.human.Human
import com.aldebaran.qi.sdk.builder.LookAtBuilder
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.aldebaran.qi.sdk.builder.TransformBuilder
import com.softbankrobotics.app.R
import com.softbankrobotics.app.ui.ConversationBinder
import com.softbankrobotics.app.ui.ConversationItemType
import com.softbankrobotics.pepperaruco.util.GotoUtils.goToFrame
import kotlinx.android.synthetic.main.activity_engage_and_return_to_marker.*
import com.aldebaran.qi.sdk.builder.LocalizeAndMapBuilder
import com.aldebaran.qi.sdk.builder.LocalizeBuilder
import com.softbankrobotics.pepperaruco.*
import com.softbankrobotics.pepperaruco.util.*
import com.softbankrobotics.pepperaruco.util.LookAtUtils.detectWhenLookingAtFrame
import org.apache.commons.math3.geometry.euclidean.threed.RotationConvention
import org.apache.commons.math3.geometry.euclidean.threed.RotationOrder

class EngageAndReturnToMarkerActivity : BaseActivity() {

    /** The Localize action. We use a Localize action only to improve the human detection.
     * To localize we use Aruco markers. */
    private var localizing: Future<Void>? = null

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
    private val detectArucoConfig = DetectArucoConfig(ARUCO_MARKER_SIZE, ARUCO_DICT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        QiSDK.register(this, this)
        OpenCVUtils.loadOpenCV(this)
    }

    override fun onDestroy() {
        QiSDK.unregister(this, this)
        super.onDestroy()
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        val conversationStatus = qiContext.conversation.status(qiContext.robotContext)
        conversationBinder = conversationView.bindConversationTo(conversationStatus)

        lateinit var arucoMarker: ArucoMarker
        lateinit var holder: Future<Void>
        say(qiContext, "Please place a 4x4 Aruco Marker on the floor in front of me.").andThenCompose {

            Log.d(TAG, "STATE: hold basic awareness")
            holder = holdAutonomousAbilities(qiContext, AutonomousAbilitiesType.BASIC_AWARENESS)
            holder

        }.andThenCompose {

            Log.d(TAG, "STATE: detect marker")
            displayLine("Detection started", ConversationItemType.INFO_LOG)
            waitToDetectAMarkerInFront(qiContext)

        }.andThenCompose { marker ->

            arucoMarker = marker
            displayLine("Detection stopped", ConversationItemType.INFO_LOG)
            say(qiContext,"I see marker ${marker.id}, this marker will represent my home position.")

        }.andThenCompose {

            Log.d(TAG, "STATE: Go to marker")
            displayLine("Going to marker", ConversationItemType.INFO_LOG)
            goToFrame(qiContext, arucoMarker.frame).thenConsume(::displayGoToError)

        }.andThenCompose {

            Log.d(TAG, "STATE: align with marker")
            alignWithFloorMarker(qiContext, arucoMarker.frame)

        }.andThenCompose {

            val msg = "When I detect someone, I will go salute him, and come back to this position."
            say(qiContext, msg)

        }.andThenCompose {

            val msg = "Let me start the mapping so that I will detect people around me more efficiently"
            say(qiContext, msg)

        }.andThenCompose {

            Log.d(TAG, "STATE: map environment")
            mapEnvironment(qiContext)

        }.andThenConsume { explorationMap ->

            val callbackOnLocalized: () -> Unit = {
                Log.d(TAG, "STATE: align with marker")
                alignWithFloorMarker(qiContext, arucoMarker.frame).andThenConsume {
                    holder.cancel(true)
                    displayLine("Detecting humans...", ConversationItemType.INFO_LOG)
                    detectAndGoToHumanThenGoBackMarkerLoop(qiContext, arucoMarker)
                }
            }

            explorationMap?.let {
                Log.d(TAG, "STATE: localize in environment")
                this.localizing = startLocalizing(qiContext, explorationMap, callbackOnLocalized)
            }
        }.thenConsume { f ->
            if (f.hasError())
                Log.e(TAG, f.errorMessage)
        }
    }

    private fun detectAndGoToHumanThenGoBackMarkerLoop(qiContext: QiContext, arucoMarker: ArucoMarker): Future<Void> {
        var marker = arucoMarker
        lateinit var human: Human

        Log.d(TAG, "STATE: wait to detect human")

        // Wait to detect a human
        return waitToDetectHuman(qiContext).andThenCompose { detectedhuman ->

            human = detectedhuman
            say(qiContext, "I see someone")

            // Go toward him
            displayLine("GoTo Human started", ConversationItemType.INFO_LOG)
            Log.d(TAG, "STATE: go to human")
            goToHuman(qiContext, detectedhuman).thenConsume(::displayGoToError)

        }.andThenCompose {

            Log.d(TAG, "STATE: look at human")

            // Look at him and salute him
            LookAtBuilder.with(qiContext).withFrame(human.headFrame).buildAsync()
                .andThenCompose { lookAt ->
                    val lookAtFuture = lookAt.async().run()
                    say(qiContext, "Hello").thenConsume {
                        lookAtFuture.cancel(true)
                    }
                }

        }.thenCompose {

            // Go back close to marker if too far away
            computeTransformToMarker(qiContext, arucoMarker.frame).andThenCompose { transform ->
                val distance = transform.translation.toApacheVector().norm
                if (distance > 1.5) {
                    // Go at 1.5m from marker
                    val translationOneMeterFromMarker = transform.translation - (transform.translation / (distance / 1.5))
                    val transformOneMeterFromMarker = TransformBuilder.create().from2DTranslation(
                        translationOneMeterFromMarker.x,
                        translationOneMeterFromMarker.y
                    )
                    Log.d(TAG, "STATE: go close marker")

                    Log.d(TAG,"TRANSLATION AHAH ${translationOneMeterFromMarker.x} ${translationOneMeterFromMarker.y}")
                    qiContext.actuation.async().robotFrame().andThenCompose { robotFrame ->
                        qiContext.makeDetachedFrame(robotFrame, transformOneMeterFromMarker).andThenCompose { frame ->
                            displayLine("Go close to Marker started", ConversationItemType.INFO_LOG)
                            goToFrame(qiContext, frame).thenConsume(::displayGoToError)
                        }
                    }
                }
                else
                    Future.of<Void>(null)
            }

        }.thenCompose {

            Log.d(TAG, "STATE: look at marker")
            // Turn back to marker and try to detect it.
            // If successfull, replace the current marker frame with the detected marker frame.
            LookAtBuilder.with(qiContext).withFrame(marker.frame).buildAsync().andThenCompose { lookAt ->
                lookAt.policy = LookAtMovementPolicy.HEAD_AND_BASE
                val lookAtFuture = lookAt.async().run()

                detectWhenLookingAtFrame(qiContext, marker.frame, true).thenCompose {

                    Log.d(TAG, "STATE: detect marker")
                    displayLine("Detecting marker...", ConversationItemType.INFO_LOG)
                    qiContext.arucoDetection.detectMarkerWithTopCamera(detectArucoConfig).thenCompose { markersFuture ->
                        if (!markersFuture.hasError()) {
                            markersFuture.value.forEach { m ->
                                if (m.id == marker.id) {
                                    displayLine("Marker detected", ConversationItemType.INFO_LOG)
                                    marker = m
                                }
                            }
                        }
                        lookAtFuture?.cancel(true)
                        lookAtFuture
                    }
                }
            }

        }.thenCompose {

            Log.d(TAG, "STATE: go to marker")
            // Go back to marker
            displayLine("GoTo Marker started", ConversationItemType.INFO_LOG)
            goToFrame(qiContext, marker.frame).thenConsume(::displayGoToError)

        }.thenCompose {

            Log.d(TAG, "STATE: align with marker")
            // Align with marker
            alignWithFloorMarker(qiContext, marker.frame)

        }.thenCompose {

            // And start again ...
            detectAndGoToHumanThenGoBackMarkerLoop(qiContext, marker)

        }.thenConsume { f ->
            if (f.hasError())
                Log.e(TAG, f.errorMessage)
        }
    }

    override fun onRobotFocusLost() {
        conversationBinder?.unbind()
        localizing?.cancel(true)
    }

    override fun onRobotFocusRefused(reason: String?) {
        displayLine("Robot focus refused", ConversationItemType.ERROR_LOG)
    }

    /**
     * Compute the transform between the robotFrame and the marker Frame.
     * Usefull to compute:
     * - the distance between the robot and the marker
     * - the path to the marker
     */
    fun computeTransformToMarker(qiContext: QiContext, markerFrame: Frame): Future<Transform> {
        return qiContext.actuation.async().robotFrame().andThenCompose { robotFrame ->
            markerFrame.async().computeTransform(robotFrame).andThenApply {
                it.transform
            }
        }
    }

    /**
     * Utility function used to display the result of the GoTo action in the Conversation View.
     * @param The GoTo action future.
     */
    fun displayGoToError(f: Future<Void>) {
        if (f.hasError())
            displayLine("GoTo finished with error. ${f.getError()}",
                ConversationItemType.ERROR_LOG)
        else
            displayLine("GoTo finished", ConversationItemType.INFO_LOG)
    }

    fun say(qiContext: QiContext, message: String): Future<Void> {
        return SayBuilder.with(qiContext).withText(message).build().async().run()
    }

    /**
     *  Wait for basic awareness to trigger human around signal
     */
    fun waitToDetectHuman(qiContext: QiContext): Future<Human> {
        val humans = qiContext.humanAwareness.humansAround
        if (humans.size > 0)
            return Future.of(humans[0])
        val promise = Promise<Human>()
        qiContext.humanAwareness.addOnHumansAroundChangedListener { humans ->
            if (humans.size > 0) {
                qiContext.humanAwareness.removeAllOnHumansAroundChangedListeners()
                promise.setValue(humans[0])
            }
        }
        return promise.future
    }

    /**
     * Go to human frame.
     */
    fun goToHuman(qiContext: QiContext, human: Human): Future<Void> {
        val transform50cm = TransformBuilder.create().from2DTranslation(0.5, 0.0)
        return human.headFrame.async().makeAttachedFrame(transform50cm).andThenCompose { inFrontOfHuman ->
            goToFrame(qiContext, inFrontOfHuman.frame())
        }
    }

    /**
     * Call [detectMarkerWithTopCamera] in loop until an aruco marker is detected.
     * @return A future to an Aruco marker.
     */
    fun detectMarkerLoop(qiContext: QiContext): Future<ArucoMarker> {
        return qiContext.arucoDetection.detectMarkerWithTopCamera(detectArucoConfig).andThenCompose { markers ->
            if (markers.size > 0)
                Future.of(markers.first()) // Return the first one seen
            else
                detectMarkerLoop(qiContext)
        }
    }

    /**
     * Look at the floor, 1 meter in front of Pepper, and wait to detect an aruco marker with the
     * top camera.
     */
    fun waitToDetectAMarkerInFront(qiContext: QiContext): Future<ArucoMarker> {
        lateinit var lookAtFuture: Future<Void>
        return qiContext.actuation.async().robotFrame().andThenCompose { robotFrame ->
            robotFrame.async().makeAttachedFrame(ONE_METER_X_TRANSFORM).andThenCompose { it.async().frame() }
        }.andThenCompose { oneMeterInFrontFrame ->
            LookAtBuilder.with(qiContext).withFrame(oneMeterInFrontFrame).buildAsync()
        }.andThenConsume { lookAt ->
            lookAtFuture = lookAt.async().run()
        }.thenCompose { lookAtActionFuture ->
            val detectFuture = detectMarkerLoop(qiContext)
            detectFuture.thenConsume { lookAtFuture.cancel(true) }
            lookAtFuture.thenCompose { detectFuture }
        }
    }

    /**
     * Align the robot body with the frame of a marker on the floor.
     * @param qiContext
     * @param frame: the frame of the marker
     */
    fun alignWithFloorMarker(qiContext: QiContext, frame: Frame): Future<Void> {
        return qiContext.actuation.async().robotFrame().andThenCompose { robotFrame ->
            // Align the x axis of the robot frame with the marker frame on the floor
            val t = Transform(Rotation(0.0, Math.toRadians(-90.0), 0.0).toQuaternion(), Vector3(0.0, 0.0, 0.0))
            robotFrame.async().makeAttachedFrame(t).andThenCompose { f ->
                f.async().frame().andThenCompose { rotatedRobotFrame ->
                    // And compute the rotation on the X axis of the robot frame to align with marker
                    val delta = frame.computeTransform(rotatedRobotFrame).transform
                    val theta = delta.rotation.toApacheRotation().getAngles(RotationOrder.XYZ, RotationConvention.VECTOR_OPERATOR)
                    GotoUtils.goStraightToPos(qiContext, 0.0, 0.0, -theta[0])
                }
            }
        }
    }


    /**
     * Create a map of the environment using the LocalizeAndMap action.
     * This function returns only when the mapping is finished.
     * @return A future to an ExplorationMap, that will be usable with a Localize action.
     */
    private fun mapEnvironment(qiContext: QiContext): Future<ExplorationMap?> {
        var explorationMap: ExplorationMap? = null
        var localizationAndMapping: Future<Void>? = null
        return LocalizeAndMapBuilder.with(qiContext).buildAsync().andThenCompose { localizeAndMap ->
            localizeAndMap.addOnStatusChangedListener { status ->
                if (status == LocalizationStatus.LOCALIZED) {
                    explorationMap = localizeAndMap?.dumpMap()
                    localizeAndMap.removeAllOnStatusChangedListeners()
                    displayLine("Robot has mapped his environment", ConversationItemType.INFO_LOG)
                    localizationAndMapping?.cancel(true)
                }
            }
            localizationAndMapping = localizeAndMap.async().run()
            localizationAndMapping?.thenConsume { future ->
                if (future.hasError()) {
                    displayLine("Mapping finished with error. ${future.getError()}", ConversationItemType.ERROR_LOG)
                }
                else {
                    displayLine("Mapping finished", ConversationItemType.INFO_LOG)
                }
            }
            displayLine("Mapping...", ConversationItemType.INFO_LOG)
            localizationAndMapping?.thenApply {

                explorationMap
            }
        }
    }

    /**
     * Start a Localize action and immediately return.
     * @return A future containing the localize action. Use this future to terminate the localize action.
     */
    private fun startLocalizing(qiContext: QiContext, explorationMap: ExplorationMap, onLocalized: () -> Unit): Future<Void> {
        return LocalizeBuilder.with(qiContext).withMap(explorationMap).buildAsync().andThenCompose { localize ->
            localize.addOnStatusChangedListener { status ->
                if (status == LocalizationStatus.LOCALIZED) {
                    displayLine("Robot is localized", ConversationItemType.INFO_LOG)
                    onLocalized()
                }
            }
            displayLine("Localizing...", ConversationItemType.INFO_LOG)
            val localizing = localize.async().run()
            localizing.thenConsume { future ->
                localize.removeAllOnStatusChangedListeners()
                if (future.hasError())
                    displayLine("Localize finished with error. ${future.getError()}", ConversationItemType.ERROR_LOG)
            }
            localizing
        }
    }

    /**
     * Displays a line of text in the Conversation view. Use it to display the status of the robot.
     */
    private fun displayLine(text: String, type: ConversationItemType) {
        runOnUiThread { conversationView.addLine(text, type) }
    }
}
