const http = require('http');
const os = require('os');
const rn_bridge = require('rn-bridge');

// Get local IP address (non-loopback)
function getLocalIp() {
  const ifaces = os.networkInterfaces();
  for (const iface of Object.values(ifaces)) {
    for (const addr of iface) {
      if (addr.family === 'IPv4' && !addr.internal) {
        return addr.address;
      }
    }
  }
  return '127.0.0.1';
}

const ip = getLocalIp();
const port = 3000;

const server = http.createServer((req, res) => {
  console.log('[node] Received request:', req.url);
  res.writeHead(200, { 'Content-Type': 'text/html' });
  res.end(`<h1>Hello from Node.js on ${ip}:${port}!</h1>`);
});

server.listen(port, ip, () => {
  console.log(`[node] Server running at http://${ip}:${port}/`);
  rn_bridge.channel.send({ type: 'started', ip, port });
});
