var cameraModule = (function () {
  var currentFacing = "rear";

  var startCamera = function () {
    CameraPreview.startCamera({
      x: 0, y: 0,
      width: 1,
      height: 1,
      camera: currentFacing,
      toBack: false
    });
  };

  var toggleCamera = function () {
    currentFacing = (currentFacing === "rear") ? "front" : "rear";
    CameraPreview.switchCamera();
  };

  var captureFrame = function (callback) {
    CameraPreview.takePicture({ width: 640, height: 480, quality: 80 }, function (imgData) {
      callback("data:image/jpeg;base64," + imgData);
    });
  };

  return {
    startCamera,
    toggleCamera,
    captureFrame
  };
})();
