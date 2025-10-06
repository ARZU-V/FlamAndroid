import { useState, useEffect, useRef } from 'react';
import './App.css';

// Android device's local IP address
const ANDROID_DEVICE_IP = "10.0.10.34"; 
const WEBSOCKET_URL = `ws://${ANDROID_DEVICE_IP}:8080/video`;

function App() {
  const [status, setStatus] = useState('Connecting...');
  const [fps, setFps] = useState(0);
  const [resolution, setResolution] = useState('0x0');
  const [frameUrl, setFrameUrl] = useState<string | null>(null);

  // Refs to hold values that shouldn't trigger re-renders on their own
  const frameCount = useRef(0);
  const lastFpsTime = useRef(Date.now());

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
        if (event.data instanceof Blob) {
          const newUrl = URL.createObjectURL(event.data);
          // Set the new frame URL, and the browser will automatically handle
          // revoking the old one when the <img> src changes.
          setFrameUrl(newUrl);

          // Calculate FPS
          frameCount.current++;
          const now = Date.now();
          const delta = (now - lastFpsTime.current) / 1000;
          if (delta >= 1) {
            setFps(Math.round(frameCount.current / delta));
            frameCount.current = 0;
            lastFpsTime.current = now;
          }
        }
      };

      ws.onclose = () => {
        console.log('WebSocket disconnected. Reconnecting...');
        setStatus('Disconnected. Retrying...');
        // Clean up the old URL if the connection drops
        if (frameUrl) URL.revokeObjectURL(frameUrl);
        setFrameUrl(null);
        // Try to reconnect after 2 seconds
        reconnectTimeout = setTimeout(connect, 2000);
      };

      ws.onerror = (error) => {
        console.error('WebSocket error:', error);
        ws.close(); // This will trigger the onclose event for reconnection
      };
    };

    connect(); // Initial connection attempt

    // This is the cleanup function. It runs when the component is unmounted.
    return () => {
      clearTimeout(reconnectTimeout); // Clear any pending reconnection
      if (ws) {
        ws.close(); // Gracefully close the connection
      }
      if (frameUrl) {
        URL.revokeObjectURL(frameUrl); // Final cleanup of the blob URL
      }
    };
  }, []); // The empty array [] means this effect runs only once when the component mounts

  // We can derive the image resolution directly when the image loads
  const handleImageLoad = (e: React.SyntheticEvent<HTMLImageElement>) => {
    const { naturalWidth, naturalHeight } = e.currentTarget;
    setResolution(`${naturalWidth}x${naturalHeight}`);
  };

  return (
    <div className="app">
      <h1>Android OpenCV Stream</h1>
      <div className="video-container">
        {frameUrl ? (
          <img 
            id="video-feed" 
            src={frameUrl} 
            alt="Live feed from device"
            onLoad={handleImageLoad}
          />
        ) : (
          <div className="placeholder">Waiting for stream...</div>
        )}
        {status !== 'Connected' && (
          <div className="status-overlay">{status}</div>
        )}
      </div>
      <div className="stats">
        FPS: {fps} | Resolution: {resolution}
      </div>
    </div>
  );
}

export default App;