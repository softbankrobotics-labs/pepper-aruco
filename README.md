# Aruco Navigation Library

This Android library will help you:

* Search and detect Aruco markers with Pepper
* Make Pepper localize and navigate using the markers as landmarks

## 1. Video demonstration

This video was filmed at SoftBank Robotics Europe, and shows Pepper detecting and navigating toward aruco marker placed in the SBRE Showroom.

[Watch video on YouTube](https://www.youtube.com/watch?v=YwWUTFxySWU)

## 2. Getting started

### 2.1. Running the sample app

The project comes complete with a sample project. You can clone the repository, open it in Android Studio, and run this directly onto a Robot.

The sample application contains a tutorial that will demonstrate what you can do with the library.
You will need to have Aruco Markers in order to play with the tutorial. See [Print Aruco markers](#22-print-aruco-markers)

### 2.2. Print Aruco markers

You can generate Aruco Markers, for instance with [this website](http://chev.me/arucogen/).
Choose 4x4 aruco markers, of size 150mm, and pick an ID inferior at 50.

If you want you can also directly print the following markers (click on each image to open them):

[ <img src="sample_markers/4x4_1000-13" alt="Marker 13" width="200" border="10"/> ](sample_markers/4x4_1000-13.svg)
[<img src="sample_markers/4x4_1000-36" alt="Marker 36" width="200" border="10"/>](sample_markers/4x4_1000-36.svg)
[<img src="sample_markers/4x4_1000-41" alt="Marker 41" width="200" border="10"/>](sample_markers/4x4_1000-41.svg)

## 3. Using the library in your project

### 3.1. Add the library as a dependancy

You can use Jitpack (https://jitpack.io/) to add the library as a graddle dependancy.

**Step 1)** add JitPack repository to your build file:

Add it in your root build.gradle at the end of repositories:

```
allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```

**Step 2)** Add the dependency on the Aruco navigation lib to your app build.gradle in the dependencies section:

	dependencies {
		implementation 'com.github.softbankrobotics-labs:pepper-aruco:master-SNAPSHOT'
	}

### 3.2. Add OpenCV libraries

You have two options to add the OpenCV libraries to your project:

#### 3.2.1. Add OpenCV native libraries

With this method you directly add the libraries to your apk. The disadvantage of this method is that your apk will become very big.

Copy the .so files you will find in the folder [aruco-navigation-root/app/src/main/jniLibs](aruco-navigation-root/app/src/main/jniLibs) into your own src/main folder.
Android studio will automatically find and include these libraries into your apk.


#### 3.2.2. Use OpenCV external APK

With this method, the opencv libraries are not installed with your apk, you need to install them separately on the robot.

To install OpenCV manager APK, connect to your robot ip (for instance 10.0.204.180) with adb and install the package you will find in the folder [opencv-apk](opencv-apk/):

```
$ adb connect 10.0.204.180:5555
$ adb install opencv-apk/OpenCV_3.4.7-dev_Manager_3.47_armeabi-v7a.apk
```

## 4. Usage

### 4.1. Prerequisite: load OpenCV in our Activity

In your Activity class, you need to load opencv in the `onCreate` method in order to be able to use the library. To do so, call `OpenCVUtils.loadOpenCV(this)`:

```kotlin

class MyActivity : AppCompatActivity(), RobotLifecycleCallbacks() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        OpenCVUtils.loadOpenCV(this)
        //...
    }
```

### 4.2. Detect a marker with the top camera

To detect a marker using Pepper Top camera, use the function `qiContext.arucoDetection.detectMarkerWithTopCamera`. This function capture the Top Camera image, analyse it and returns a `Set` of `ArucoMarker`s found in it.

```kotlin
val detectConfig = DetectArucoConfig()
detectConfig.markerLength = 0.15
detectConfig.dictionary = ArucoDictionary.DICT_4X4_50

val res: Future<Set<ArucoMarker>> =
  qiContext.arucoDetection.detectMarkerWithTopCamera(detectConfig)
```

Using the `DetectArucoConfig` object parameter, you can specify the size of the Aruco Marker to find, as well as the Aruco Dictionnary to use.


### 4.3. Look around and detect markers

If you want Pepper to search around for Aruco Markers, you can use the function `qiContext.arucoDetection.lookAroundForMarker`. This function makes Pepper look at different positions, take pictures with its top camera, analyse them and return `Set` of `ArucoMarker`s found in it.

```kotlin
val detectConfig = DetectArucoConfig()
detectConfig.markerLength = 0.15
detectConfig.dictionary = ArucoDictionary.DICT_4X4_50

val lookAroundConfig = LookAroundConfig()
lookAroundConfig.lookAtPolicy = LookAtMovementPolicy.HEAD_AND_BASE
lookAroundConfig.lookAtTransform = listOf(
            LookAtUtils.lookAtTransformFromSphericalCoord(
                SphericalCoord(1.0, Math.toRadians(90.0), Math.toRadians(-30.0))), // Look left 30°
            LookAtUtils.lookAtTransformFromSphericalCoord(
                SphericalCoord(1.0, Math.toRadians(90.0), Math.toRadians(0.0))), // Look front
            LookAtUtils.lookAtTransformFromSphericalCoord(
                SphericalCoord(1.0, Math.toRadians(90.0), Math.toRadians(30.0))), // Look right 30°
)

val res: Future<Set<ArucoMarker>> =
    qiContext.arucoDetection.lookAroundForMarker(lookAroundConfig, detectConfig)
```

Using the `DetectArucoConfig` object parameter, you can specify the size of the Aruco Marker to find, as well as the Aruco Dictionnary to use.

Using the `LookAroundConfig` object parameter, you can specify if Pepper should look at only with the head or with its full body (head & base), and where pepper should look at using the `lookAtTransform` parameter.
This parameters takes a list of `Transform` expressing positions where Pepper should look at, with respect to the `gazeFrame` (`qiContext.actuation.gazeFrame()`) Frame.
You can use the helper function `LookAtUtils.lookAtTransformFromSphericalCoord` to create a `Transform` from a Spherical coordinate position.


### 4.4. Add and remove listener to be called whenever an aruco marker is detected

If you want to have a callback called, whenever an aruco marker is (re-)detected, you can add Listeners. To add a listener

```kotlin
qiContext.arucoDetection.addOnArucoMarkerDetectedListener(object : OnArucoMarkerDetectedListerner {
            override fun onArucoMarkerDetected(arucoMarker: ArucoMarker, detectionData: ArucoMarkerDetectionData) {
                // Do something
            }
        })
```

To remove one listener:

```kotlin
qiContext.arucoDetection.removeOnArucoMarkerDetectedListerner(listenerObject)
```

To remove all listeners:

```kotlin
qiContext.arucoDetection.removeAllOnArucoMarkerDetectedListeners()
```

### 4.5. Retrieve all Aruco markers detected up to now

To retrieve all aruco markers that where detected up to now, you can use `qiContext.actuation.markers`,
this is a `Map<Int, ArucoMarker>` containing all markers, indexed by their ids.

To get it as a list, use `qiContext.actuation.markers.values`


### 4.6. Aruco markers object:

The markers are unique object. If they get re-dected, the api will return the same object.
Every marker has the following members:

  - an Id (a positive number) that uniquely identify it.
  - a `Frame`, corresponding to the position of the marker in space. This `Frame` can be used with the `LookAt` or `GoTo` actions.


```kotlin
class ArucoMarker(
    val id: Int,/** The number Id of the Aruco marker. */
    val frame: Frame,/** The frame of the Aruco marker */
    val detectionData: MutableList<ArucoMarkerDetectionData> = mutableListOf(),
)
```

Every time a marker is re-detected by any function in the library, one `ArucoMarkerDetectionData` will be appended to the `detectionData` list. The `ArucoMarkerDetectionData` objects contains:

  - The image of the camera in which the aruco marker was detected
  - The [timestamp (QiSDK Timestamp)](https://qisdk.softbankrobotics.com/sdk/doc/pepper-sdk/ch4_api/perception/reference/timestamps.html#timestamps) when the marker was detected.
  - The elements returned by the OpenCV detection functions (`corners`, `tvec`, `rvec`)

```kotlin
data class ArucoMarkerDetectionData(
    val image: Bitmap,/** The camera image of the Aruco marker */
    val corners: Mat,/** The corners of the marker in the image, as returned by OpenCV */
    val tvec: DoubleArray,
    val rvec: DoubleArray,
    val timestamp: Long /** The time when the Aruco was detected  */
)
```

### 4.7. Aruco markers Frames automatic repositionning

The robot odometry position drift with time: whenever the robot moves, it tries to keep track of its position, but there is always a little bit of error that accumulate, and in the end, becomes a lot of error.

The Aruco library contains a mechanism to account for this drift, and automatically adjust the detected markers `Frame`s, so that they are always accurate.

Indeed, whenever a known marker is re-detected by the library, it automatically update the `Frame`s of all the ArucoMarkers detected until now, so that they are always correctly positionned, even if the robot odometry drifted.

For this system to work however, you *MUST* regularly re-detect markers if the robot moves. For instance, re-detect at least one marker every time the robot has traveled at most 10 meters.


## 5. Advanced usage

### 5.1. Use a different version of OpenCV

It is possible to replace the version of opencv contained in this project. Though the complete method on how to do it exactly is left out of this README. One thing you need to make sure is you use a version of OpenCV compiled with Aruco Detection code in it (which is not the default). This version is called opencv contrib.
You can find compiled versions here:

[https://pullrequest.opencv.org/buildbot/builders/3_4-contrib_pack-contrib-android]

Click on a build number, and then click on the upload release to find the archive called OpenCV4Android.zip
You will find the files you need in this archive.


### 5.2. Extending the lib

#### 5.3.1. Full OpenCV Aruco documentation by developper

The full opencv aruco documentation can be found here:

[https://docs.google.com/document/d/1QU9KoBtjSM2kF6ITOjQ76xqL7H0TEtXriJX5kwi9Kgc/edit#]

## 6. License

This project is licensed under the BSD 3-Clause "New" or "Revised" License- see the [COPYING](COPYING.md) file for details
