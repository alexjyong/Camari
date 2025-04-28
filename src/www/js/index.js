document.addEventListener("deviceready", function () {
    document.getElementById('status').textContent = 'Starting...';
    cameraModule.startCamera();
    serverModule.startServer();
    streamingModule.startStreaming();
    document.getElementById('status').textContent = 'Streaming!';
  });
  