package com.softbankrobotics.pepperaruco.actuation

data class RobotRelativeFrameOrientation(
        val xAxisDirection: RobotRelativeDirection,
        val yAxisDirection: RobotRelativeDirection,
        val zAxisDirection: RobotRelativeDirection
)
