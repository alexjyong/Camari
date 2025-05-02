document.addEventListener("deviceready", function () {
  document.getElementById('status').textContent = 'Starting...';
  cordova.plugins.backgroundMode.enable();
  //cameraModule.startCamera();
  streamingModule.start();
  document.getElementById('status').textContent = 'Streaming!';

  console.log("going to get ip address")
  getLocalIPAddress(function (ip) {
    console.log("ip address is at" + ip)
    document.getElementById('ip-address').textContent = `http://${ip}:8080/stream`;
  });
});

function getLocalIPAddress(callback) {
  console.log("trying to get local IP address")
  var RTCPeerConnection = window.RTCPeerConnection || window.webkitRTCPeerConnection;
  var pc = new RTCPeerConnection({ iceServers: [] });

  pc.createDataChannel('');
  pc.createOffer().then(offer => pc.setLocalDescription(offer)).catch(console.error);

  pc.onicecandidate = (event) => {
    if (event && event.candidate && event.candidate.candidate) {
      var parts = event.candidate.candidate.split(' ');
      var addr = parts[4];
      console.log(`addr is ${addr}`)
      if (addr.startsWith('192.') || addr.startsWith('10.') || addr.startsWith('172.')) {
        callback(addr);
        pc.onicecandidate = null;
      }
    }
  };
}
