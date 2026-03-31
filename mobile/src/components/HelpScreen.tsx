import './HelpScreen.css';

interface HelpScreenProps {
  onClose: () => void;
}

export function HelpScreen({ onClose }: HelpScreenProps) {
  return (
    <div className="help-overlay" onClick={onClose}>
      <div className="help-sheet" onClick={e => e.stopPropagation()}>
        <button className="help-close" onClick={onClose} aria-label="Close help">✕</button>

        <h2>Help & About</h2>

        <section className="help-section">
          <h3>OBS Browser Source</h3>
          <ol>
            <li>Download and install <a href="https://obsproject.com/" target="_blank" rel="noreferrer">OBS Studio</a> on your computer</li>
            <li>Tap <strong>Start Streaming</strong> in Camari</li>
            <li>In OBS, add a <strong>Browser Source</strong></li>
            <li>Paste the URL from Camari into the URL field</li>
            <li>Set the width and height to match your chosen resolution — <strong>1280 × 720</strong> for 720p (default), <strong>1920 × 1080</strong> for 1080p, <strong>854 × 480</strong> for 480p</li>
            <li>Click OK — video should appear within a few seconds</li>
          </ol>
        </section>

        <section className="help-section">
          <h3>Discord / Teams / Zoom</h3>
          <ol>
            <li>Set up the OBS Browser Source above</li>
            <li>In OBS, start <strong>Virtual Camera</strong> (Tools menu)</li>
            <li>In your video call app, select <strong>OBS Virtual Camera</strong> as your camera</li>
          </ol>
        </section>

        <section className="help-section">
          <h3>Network troubleshooting</h3>
          <ul>
            <li>Your phone and computer need to be on the <strong>same WiFi network</strong></li>
            <li>No WiFi? Enable your phone's hotspot and connect your computer to it</li>
            <li>Stream frozen in OBS? Stream not showing up? Click on Browser Source and click on the <strong>Refresh</strong> button</li>
            <li>Can't connect at all? Check that your phone's firewall isn't blocking port 8080</li>
            <li>VPNs on your phone may cause trouble with getting your computer to talk to Camari. Recommend not using a VPN while using Camari</li>
          </ul>
        </section>
        <section className="help-section">
          <h3>About</h3>
          <p>
            Camari is free and open source software, released under the{' '}
            <a href="https://www.gnu.org/licenses/gpl-3.0.html" target="_blank" rel="noreferrer">GNU GPL v3</a> license.
            Source code is on{' '}
            <a href="https://github.com/alexjyong/Camari" target="_blank" rel="noreferrer">GitHub</a>.
          </p>
        </section>

        <section className="help-section">
          <h3>Support the project</h3>
          <p>If Camari is useful to you, consider buying me a coffee. :) </p>
          <div className="help-donate-links">
            <a href="https://buymeacoffee.com/alexjyong" target="_blank" rel="noreferrer">Buy Me a Coffee</a>
            <a href="https://liberapay.com/alexjyong" target="_blank" rel="noreferrer">Liberapay</a>
            <a href="https://paypal.me/alexjyong" target="_blank" rel="noreferrer">PayPal</a>
            <a href="https://cash.app/$ajyong" target="_blank" rel="noreferrer">Cash App</a>
            <a href="https://account.venmo.com/u/ajyong" target="_blank" rel="noreferrer">Venmo</a>
          </div>
        </section>
      </div>
    </div>
  );
}
