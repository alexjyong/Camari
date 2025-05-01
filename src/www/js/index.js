document.addEventListener("deviceready", function () {
  document.getElementById('status').textContent = 'Starting...';
  cameraModule.startCamera();
  serverModule.startServer();
  streamingModule.startStreaming();
  document.getElementById('status').textContent = 'Streaming!';

  getLocalIPAddress(function (ip) {
    document.getElementById('ip-address').textContent = `http://${ip}:8080/stream`;
  });
});

function getLocalIPAddress(callback) {
  var RTCPeerConnection = window.RTCPeerConnection || window.webkitRTCPeerConnection;
  var pc = new RTCPeerConnection({ iceServers: [] });

  pc.createDataChannel('');
  pc.createOffer().then(offer => pc.setLocalDescription(offer)).catch(console.error);

  pc.onicecandidate = (event) => {
    if (event && event.candidate && event.candidate.candidate) {
      var parts = event.candidate.candidate.split(' ');
      var addr = parts[4];
      if (addr.startsWith('192.') || addr.startsWith('10.') || addr.startsWith('172.')) {
        callback(addr);
        pc.onicecandidate = null;
      }
    }
  };
}
