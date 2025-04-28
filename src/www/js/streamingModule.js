var streamingModule = (function () {
    var clients = [];
  
    var startStreaming = function () {
      setInterval(function () {
        cameraModule.captureFrame(function (frame) {
          clients.forEach(function (res) {
            try {
              res.write("--frame\r\n");
              res.write("Content-Type: image/jpeg\r\n\r\n");
              res.write(Buffer.from(frame.split(",")[1], 'base64'));
              res.write("\r\n");
            } catch (e) {
              console.log("Client disconnected");
            }
          });
        });
      }, 150);
    };
  
    var addClient = function (res) {
      res.writeHead(200, {
        'Content-Type': 'multipart/x-mixed-replace; boundary=frame',
        'Connection': 'keep-alive',
        'Cache-Control': 'no-cache'
      });
      clients.push(res);
      res.on('close', () => {
        clients = clients.filter(c => c !== res);
      });
    };
  
    return { startStreaming, addClient };
  })();
  