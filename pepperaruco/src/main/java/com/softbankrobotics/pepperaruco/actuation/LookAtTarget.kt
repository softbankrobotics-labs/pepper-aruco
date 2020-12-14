package com.softbankrobotics.pepperaruco.actuation

import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.`object`.geometry.Transform

public data class LookAtTarget(
        val baseFrame: Frame,
        val transform: Transform,
        val time: Long
)
