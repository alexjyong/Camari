const http = require('http');
const cordova = require('cordova-bridge');

const PORT = 8080;

const server = http.createServer((req, res) => {
  cordova.channel.send(`[node] Got request for ${req.url}`);

  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.end('Hello from Node.js inside Cordova!\n');
});

server.listen(PORT, () => {
  cordova.channel.send(`[node] Server is running at http://localhost:${PORT}`);
});
