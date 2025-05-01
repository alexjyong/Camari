var streamingModule = (function () {
  var start = function () {
    webserver.start(8080,
      function success() {
        console.log("Server started on port 8080");

        webserver.onRequest(function (req) {
          if (typeof req === 'string') {
            console.log(req); 
            return;
          }
          if (req.path === '/') {
            webserver.sendResponse(req.requestId, {
              status: 200,
              body: 'hi there I work!',  
              headers: { 'Content-Type': 'text/html' }
            });
          } else {
            webserver.sendResponse(req.requestId, {
              status: 404,
              body: 'Not found',
              headers: { 'Content-Type': 'text/plain' }
            });
          }
        });

        setInterval(function () {
          cameraModule.captureFrame(function (frame) {
            const base64 = frame.split(',')[1];
            console.log("pushing to webserver stream")
            webserver.pushFrame(base64, null, console.error);
          });
        }, 100); // about 10 fps here. should be configurable later
      },
      function error(err) {
        console.error("Server failed to start", err);
      }
    );
  };

  return { start };
})();
