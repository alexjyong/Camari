var streamingModule = (function () {
  var startStreaming = function () {
    setInterval(function () {
      cameraModule.captureFrame(function (frame) {
        const base64 = frame.split(',')[1];
        webserver.pushFrame(base64, null, console.error); // Same as start(), just new method
      });
    }, 100);
  };

  return { startStreaming };
})();
