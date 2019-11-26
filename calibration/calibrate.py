import cv2
from cv2 import aruco
import numpy as np
import matplotlib.pyplot as plt

images=[
	"board/0.jpg",
	"board/1.jpg",
	"board/2.jpg",
	"board/3.jpg",
	"board/4.jpg",
	"board/5.jpg",
	"board/6.jpg",
	"board/7.jpg",
	"board/8.jpg",
	"board/9.jpg",
	"board/10.jpg",
	"board/11.jpg",
	"board/12.jpg",
	"board/13.jpg",
	"board/14.jpg",
	"board/15.jpg",
	"board/16.jpg",
	"board/17.jpg",
	"board/18.jpg",
	"board/19.jpg",
	"board/20.jpg",
	"board/21.jpg",
	"board/22.jpg",
	"board/23.jpg",
]
close_images=[
	"board/0.jpg",
	"board/1.jpg",
	"board/6.jpg",
	"board/8.jpg",
	"board/9.jpg",
	"board/10.jpg",
	"board/11.jpg",
	"board/12.jpg",
	"board/13.jpg",
	"board/14.jpg",
	"board/15.jpg",
	"board/16.jpg",
	"board/17.jpg",
	"board/18.jpg",
	"board/19.jpg",
	"board/20.jpg",
	"board/21.jpg",
	"board/22.jpg",
	"board/23.jpg",
]
images=close_images
images=[
	"board2/1.jpg",
	"board2/2.jpg",
	"board2/3.jpg",
	"board2/4.jpg",
	"board2/5.jpg",
	"board2/6.jpg",
	"board2/7.jpg",
]
aruco_dict = aruco.Dictionary_get(aruco.DICT_4X4_250)
board = aruco.CharucoBoard_create(8, 5, 0.36, .27, aruco_dict)
#imboard = board.draw((2000, 2000))
#cv2.imwrite("chessboard.tiff", imboard)


def read_chessboards(images):
    """
    Charuco base pose estimation.
    """
    print("POSE ESTIMATION STARTS:")
    allCorners = []
    allIds = []
    decimator = 0
    # SUB PIXEL CORNER DETECTION CRITERION
    criteria = (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 100, 0.00001)

    for im in images:
        print("=> Processing image {0}".format(im))
        frame = cv2.imread(im)
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        corners, ids, rejectedImgPoints = cv2.aruco.detectMarkers(gray, aruco_dict)

        if len(corners)>0:
            # SUB PIXEL DETECTION
            for corner in corners:
                cv2.cornerSubPix(gray, corner,
                                 winSize = (3,3),
                                 zeroZone = (-1,-1),
                                 criteria = criteria)
            res2 = cv2.aruco.interpolateCornersCharuco(corners,ids,gray,board)
            if res2[1] is not None and res2[2] is not None and len(res2[1])>3 and decimator%1==0:
                allCorners.append(res2[1])
                allIds.append(res2[2])

        decimator+=1

    imsize = gray.shape
    return allCorners,allIds,imsize


allCorners,allIds,imsize=read_chessboards(images)


def calibrate_camera(allCorners,allIds,imsize):
    """
    Calibrates the camera using the dected corners.
    """
    print("CAMERA CALIBRATION")

    cameraMatrixInit = np.array([[ 1000.,    0., imsize[0]/2.],
                                 [    0., 1000., imsize[1]/2.],
                                 [    0.,    0.,           1.]])

    distCoeffsInit = np.zeros((5,1))
    flags = (cv2.CALIB_USE_INTRINSIC_GUESS + cv2.CALIB_RATIONAL_MODEL + cv2.CALIB_FIX_ASPECT_RATIO)
    (ret, camera_matrix, distortion_coefficients0,
     rotation_vectors, translation_vectors,
     stdDeviationsIntrinsics, stdDeviationsExtrinsics,
     perViewErrors) = cv2.aruco.calibrateCameraCharucoExtended(
                      charucoCorners=allCorners,
                      charucoIds=allIds,
                      board=board,
                      imageSize=imsize,
                      cameraMatrix=cameraMatrixInit,
                      distCoeffs=distCoeffsInit,
                      flags=flags,
                      criteria=(cv2.TERM_CRITERIA_EPS & cv2.TERM_CRITERIA_COUNT, 10000, 1e-9))

    return ret, camera_matrix, distortion_coefficients0, rotation_vectors, translation_vectors

ret, mtx, dist, rvecs, tvecs = calibrate_camera(allCorners,allIds,imsize)

print ret
print " ----------"
print mtx
print " ----------"
print dist



i=2# select image id
plt.figure()
frame = cv2.imread(images[i])
img_undist = cv2.undistort(frame,mtx,dist,None)
plt.subplot(1,2,1)
plt.imshow(frame)
plt.title("Raw image")
plt.axis("off")
plt.subplot(1,2,2)
plt.imshow(img_undist)
plt.title("Corrected image")
plt.axis("off")
plt.show()


###################################################################

#Piere version:

K16VGA_RESOLUTION = {"x": 2560, "y": 1920}

CAMERA_RESOLUTIONS = [1,2,3,4,5,6,7]

CAMERA_DISTORTION_COEFF = np.array(
        [[0.13086823, -0.44239733, 0.0004841, -0.00322714, 0.16996254]])

CAMERA_MATRIX_RESOLUTION_2560_1920 = np.array([
        [2.41523736e+03, 0.00000000e+00, 1.25128063e+03],
        [0.00000000e+00, 2.41690366e+03, 9.94791007e+02]])

CAMERA_MATRIX_RESOLUTION_INDEPENDANT = np.array([
        [0.00000000e+00, 0.00000000e+00, 1.00000000e+00]
    ])

CAMERA_DATAS_AT_RESOLUTION = {
    camera_resolution: {
        "matrix": np.append(
            CAMERA_MATRIX_RESOLUTION_2560_1920 / (2.**i),
            CAMERA_MATRIX_RESOLUTION_INDEPENDANT, axis=0
            ),
            "image_size": (
                K16VGA_RESOLUTION["x"] / (2**i),
                K16VGA_RESOLUTION["y"] / (2**i)
                ),
                "fps": 5,
        }
    for i, camera_resolution in enumerate(CAMERA_RESOLUTIONS)
}

print CAMERA_DATAS_AT_RESOLUTION



mtx = np.array([[1.20761868e+03, 0.00000000e+00, 6.25640315e+02],
                [0.00000000e+00, 1.20845183e+03, 4.97395504e+02],
                [0.00000000e+00, 0.00000000e+00, 1.00000000e+00]])
