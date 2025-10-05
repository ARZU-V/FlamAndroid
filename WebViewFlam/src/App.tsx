import { useState, useEffect } from 'react';
import './App.css';

// Android device's local IP address
const ANDROID_DEVICE_IP = "10.0.10.34"; 
const WEBSOCKET_URL = `ws://${ANDROID_DEVICE_IP}:8080/video`;

function App() {
  const [status, setStatus] = useState('Connecting...');
  const [frameUrl, setFrameUrl] = useState<string | null>(null);

  useEffect(() => {
    let ws: WebSocket;
    let reconnectTimeout: number;

    const connect = () => {
      ws = new WebSocket(WEBSOCKET_URL);

      ws.onopen = () => {
        console.log('WebSocket connected');
        setStatus('Connected');
      };

      ws.onmessage = (event: MessageEvent) => {
        // Get the image data and create a URL for the <img> tag
        if (event.data instanceof Blob) {
          const newUrl = URL.createObjectURL(event.data);
          setFrameUrl(newUrl);
        }
      };

      ws.onclose = () => {
        console.log('WebSocket disconnected. Reconnecting...');
        setStatus('Disconnected. Retrying...');
        if (frameUrl) URL.revokeObjectURL(frameUrl);
        setFrameUrl(null);
        // Basic reconnection attempt
        reconnectTimeout = setTimeout(connect, 2000);
      };

      ws.onerror = (error) => {
        console.error('WebSocket error:', error);
        ws.close(); // Triggers the 'onclose' event for reconnection
      };
    };

    connect(); // Start the connection

    // Cleanup function when the component is removed
    return () => {
      clearTimeout(reconnectTimeout);
      if (ws) ws.close();
      if (frameUrl) URL.revokeObjectURL(frameUrl);
    };
  }, []); // The empty array [] means this effect runs only once

  return (
    <div className="app">
      <h1>Android OpenCV Stream</h1>
      <div className="video-container">
        {frameUrl ? (
          <img 
            id="video-feed" 
            src={frameUrl} 
            alt="Live feed from device"
          />
        ) : (
          <div className="placeholder">Waiting for stream...</div>
        )}
        {status !== 'Connected' && (
          <div className="status-overlay">{status}</div>
        )}
      </div>
      {/* The stats bar for FPS and resolution has been removed for simplicity */}
    </div>
  );
}

export default App;
