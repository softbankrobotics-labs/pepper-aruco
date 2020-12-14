package com.softbankrobotics.pepperaruco.actuation

import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.util.FutureUtils
import com.beust.klaxon.Klaxon


class ArucoMarkerBuilder private constructor(val qiContext: QiContext) {

    private lateinit var markerString: String

    fun withMarkerString(str: String): ArucoMarkerBuilder {
        this.markerString = str
        return this
    }

    companion object {
        fun with(qiContext: QiContext): ArucoMarkerBuilder {
            return ArucoMarkerBuilder(qiContext)
        }
    }

    fun build(): ArucoMarker {
        return FutureUtils.get(buildAsync())
    }

    fun buildAsync(): Future<ArucoMarker> {
        check(::markerString.isInitialized) { "markerString is required." }
        val serializer = Klaxon().converter(ArucoMarker.KlaxonConverter(qiContext))
        return Future.of(serializer.parse<ArucoMarker>(markerString))
    }
}
