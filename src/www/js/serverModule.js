var serverModule = (function () {
  var startServer = function () {
    webserver.start(8080, function (req) {
      if (req.path === '/') {
        req.response.send(htmlPlayer.get(), 200, { 'Content-Type': 'text/html' });
      } else {
        req.response.send("Not found", 404);
      }
    }, function (err) {
      console.error("Server failed:", err);
    });
  };

  return { startServer };
})();
