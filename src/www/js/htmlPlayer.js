var htmlPlayer = (function () {
    var page = `
      <!DOCTYPE html>
      <html><head>
      <style>
        body { background: #111; color: white; text-align: center; font-family: sans-serif; }
        img { width: 100%; max-width: 720px; margin-top: 20px; border: 4px solid #333; }
      </style>
      </head><body>
      <h1>📷 Live Stream</h1>
      <img src="/stream" />
      </body></html>`;
    return { get: () => page };
  })();
  