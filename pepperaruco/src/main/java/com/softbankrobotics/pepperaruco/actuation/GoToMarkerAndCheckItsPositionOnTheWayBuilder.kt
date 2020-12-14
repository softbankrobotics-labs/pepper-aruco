package com.softbankrobotics.pepperaruco.actuation

import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.util.FutureUtils
import com.softbankrobotics.peppermapping.actuation.internal.GoToMarkerAndCheckItsPositionOnTheWay

class GoToMarkerAndCheckItsPositionOnTheWayBuilder private constructor(val qiContext: QiContext) {

    private val config = GoToMarkerAndCheckItsPositionOnTheWay.Config()
    private lateinit var marker: ArucoMarker

    fun withMarker(marker: ArucoMarker): GoToMarkerAndCheckItsPositionOnTheWayBuilder {
        this.marker = marker
        return this
    }

    fun withMarkerAlignmentEnabled(enabled: Boolean, markerZAxisRotation: Double = 0.0): GoToMarkerAndCheckItsPositionOnTheWayBuilder {
        this.config.alignWithMarker = enabled
        this.config.markerZAxisRotation = markerZAxisRotation
        return this
    }

    fun withWalkingAnimationEnabled(enabled: Boolean): GoToMarkerAndCheckItsPositionOnTheWayBuilder {
        this.config.walkingAnimationEnabled = enabled
        return this
    }

    fun withMaxSpeed(maxSpeed: Float): GoToMarkerAndCheckItsPositionOnTheWayBuilder {
        this.config.maxSpeed = maxSpeed
        return this
    }

    fun withDistanceBetweenStops(distanceBetweenStops: Double): GoToMarkerAndCheckItsPositionOnTheWayBuilder {
        this.config.distanceBetweenStops = distanceBetweenStops
        return this
    }

    companion object {
        fun with(qiContext: QiContext): GoToMarkerAndCheckItsPositionOnTheWayBuilder {
            return GoToMarkerAndCheckItsPositionOnTheWayBuilder(qiContext)
        }
    }

    fun build(): GoToMarkerAndCheckItsPositionOnTheWay {
        return FutureUtils.get(buildAsync())
    }

    fun buildAsync(): Future<GoToMarkerAndCheckItsPositionOnTheWay> {
        check(::marker.isInitialized) { "Marker required." }
        return Future.of(GoToMarkerAndCheckItsPositionOnTheWay(qiContext, marker, config))
    }
}