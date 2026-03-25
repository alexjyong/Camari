import './HelpScreen.css';

interface HelpScreenProps {
  onClose: () => void;
}

export function HelpScreen({ onClose }: HelpScreenProps) {
  return (
    <div className="help-overlay" onClick={onClose}>
      <div className="help-sheet" onClick={e => e.stopPropagation()}>
        <button className="help-close" onClick={onClose} aria-label="Close help">✕</button>

        <h2>Setup</h2>

        <section className="help-section">
          <h3>OBS Browser Source</h3>
          <ol>
            <li>Download and install <a href="https://obsproject.com/" target="_blank" rel="noreferrer">OBS Studio</a> on your computer</li>
            <li>Tap <strong>Start Streaming</strong> in Camari</li>
            <li>In OBS, add a <strong>Browser Source</strong></li>
            <li>Paste the URL from Camari into the URL field</li>
            <li>Set width <strong>1280</strong>, height <strong>720</strong></li>
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
            <li>Stream frozen in OBS? Right-click the source → <strong>Refresh</strong></li>
            <li>Can't connect at all? Check that your phone's firewall isn't blocking port 8080</li>
          </ul>
        </section>
      </div>
    </div>
  );
}
