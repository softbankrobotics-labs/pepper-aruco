# Changelog

All notable changes to this project will be documented in this file.

## [2.0.0] - 2020-12-14
### Pepper Aruco library
#### Added
- Action `GoToMakerAndCheckItsPositionOnTheWay` with a builder, that makes Pepper go to a marker position and try to redetect on the way to it

#### Changed
- Use QiSDK 7
- Use the [Pepper Extras library](https://github.com/softbankrobotics-labs/pepper-extras) as dependency
- Updated dependencies
- Rewrote the api to detect an Aruco marker as an action `DetectArucoMarker` with a builder
- Rewrote the api to detect several Aruco markers around Pepper as an action `LookAroundAndDetectArucoMarker` with a builder
- Expose several helper functions to help compute Aruco markers orientation and position

#### Removed
- The automatic repositioning mechanism to compensate for Pepper drift was not working correctly and has been removed. Use `LocalizeAndMap` and `Localize` and choose `ATTACHED_TO_MAPFRAME` policy when you detect markers.


### Sample App
#### Changed
- Rewrote the tutorials taking advantage of kotlin coroutines and Pepper Extras

#### Removed
- Tutorial to detect several Aruco markers around Pepper and navigate between them using the automatic repositioning mechanism has been removed


## [1.0.0] - 2019-11-26
### Pepper Aruco library
#### Added
- Api to detect an Aruco marker
- Api to detect several Aruco markers around Pepper
- Automatic repositioning mechanism for Aruco frame to compensate for robot drift

### Sample App
#### Addeed
- Tutorial to detect an Aruco marker
- Tutorial to detect several Aruco markers around Pepper
- Tutorial to detect several Aruco markers around Pepper and navigate between them using the automatic repositioning mechanism
- Tutorial to detect a marker on the floor. Consider it as HOME position. Then engage humans around, go to them,  then come back to HOME position.
