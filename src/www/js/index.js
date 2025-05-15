var app = {
  initialize: function () {
    document.addEventListener('deviceready', this.onDeviceReady.bind(this), false);
  },

  onDeviceReady: function () {
    console.log('[cordova] Device is ready');
    startNodeProject();
  }
};

app.initialize();

function startupCallback(err) {
  if (err) {
    console.error('[cordova] Node.js failed to start:', err);
  } else {
    console.log('[cordova] Node.js started');
  }
}

function startNodeProject() {
  nodejs.channel.setListener(function (msg) {
    console.log('[cordova] Message from node:', msg);
  });

  nodejs.start('main.js', startupCallback);
}
