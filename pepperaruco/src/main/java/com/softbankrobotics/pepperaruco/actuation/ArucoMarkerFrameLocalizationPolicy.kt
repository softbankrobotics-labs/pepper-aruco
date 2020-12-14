package com.softbankrobotics.pepperaruco.actuation

enum class ArucoMarkerFrameLocalizationPolicy {
    // This is the Default. Aruco frames won't be attached to any frame. With time their positions
    // will be less & less acurate because of robot drift.
    // Only use this when robot DOES NOT move. If robot moves, then use Localize action and choose
    // ATTACHED_TO_MAPFRAME
    DETACHED,

    // Choose ATTACHED_TO_MAPFRAME when robot Localize or LocalizeAndMap is running. Aruco
    // Frames will be attached to map frame, and their positions will be automatically corrected
    // and stay acurate.
    ATTACHED_TO_MAPFRAME
}
