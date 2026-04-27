# TRAKN — Indoor Child Localization System

**Senior Design Project — Qatar University, April 2026**

TRAKN is a real-time indoor localization system for tracking a child's position inside a building. A wearable IoT tag collects IMU and Wi-Fi RSSI data and sends it to a cloud server every second. The server fuses the data using Pedestrian Dead Reckoning (PDR) corrected by Wi-Fi RSSI positioning, then streams the live location to a parent's Android app over WebSocket.

The system was deployed and tested in Building H07, C Corridor at Qatar University.

---

## System Architecture

```
[Wearable Tag] ──HTTPS──▶ [GCP Cloud Server] ──WebSocket──▶ [Parent Android App]
      │                           │
 [Scanner Board] ──UART──▶ [Tag]      [PostgreSQL DB]
```

The tag is built from two separate ESP32-C6 boards. One board handles Wi-Fi scanning only (never connects), and the other handles IMU sampling and HTTPS posting (never scans). This split was necessary because the ESP32 cannot scan and maintain a TLS connection at the same time — combining both on one board caused scan blackouts and TLS timeouts.

---

## Components

| Component | Technology | Location |
|---|---|---|
| Tag (main board) | Beetle ESP32-C6, FreeRTOS | `firmware/beetle_c6_main/new_trakn_tag/` |
| Tag (scanner board) | Beetle ESP32-C6 | `firmware/beetle_c6_scanner/` |
| Backend server | Python 3.11, FastAPI, PostgreSQL 16 | `backend/` |
| Web mapping tool | React 18, Konva.js, Zustand | `web_app/web-react/` |
| Android AP survey tool | Kotlin, Jetpack Compose | `tools/trakn-ap-tool/` |
| Android parent app | Kotlin, Jetpack Compose | `tools/trakn-parent-app/` |
| Server infrastructure | GCP e2-micro, Nginx, Docker Compose | `docker-compose.yml`, `nginx/` |

---

## Hardware

### Tag Architecture

**Board 1 — Main Tag (Beetle ESP32-C6):**
- Samples MPU6050 IMU at 100 Hz
- Reads BMP180 barometer at ~1 Hz for floor detection
- Posts JSON packets over HTTPS every ~1 second
- Radio used exclusively for TCP/TLS — never scans

**Board 2 — Scanner (Beetle ESP32-C6):**
- Passive Wi-Fi channel sweep every 5 seconds
- Sends AP list as a JSON line over UART to the main board
- Never connects to any network

**UART link:** Scanner GPIO16 (TX) → Tag GPIO17 (RX), 115200 baud.

### Sensors

**MPU6050 IMU** — I²C (SDA=GPIO19, SCL=GPIO20), 100 Hz sampling via FreeRTOS `vTaskDelayUntil`.

**BMP180 Barometer** — I²C address 0x77, shared bus with MPU6050. Measures altitude delta from a ground-floor baseline sampled on boot. Floor thresholds:

| Altitude Delta | Floor |
|---|---|
| < −2.0 m | Basement |
| −2.0 to +2.5 m | Ground |
| +2.5 to +5.5 m | Floor 1 |
| > +5.5 m | Floor 2 |

A 10-reading confirmation window (~10 seconds) is required before committing a floor change.

---

## Firmware

**Active firmware:** `firmware/beetle_c6_main/new_trakn_tag/new_trakn_tag.ino`

### FreeRTOS Tasks

| Task | Priority | Role |
|---|---|---|
| `imu_task` | 5 | MPU6050 at 100 Hz, fills ring buffer |
| `uart_task` | 3 | Reads JSON scan from scanner over UART |
| `wifi_task` | 3 | Maintains Wi-Fi connection, BSSID-targeted roaming |
| `post_task` | 2 | Builds and POSTs JSON packet every 1 s |
| `baro_task` | 1 | BMP180 floor detection at ~1 Hz |

### Packet Format

```json
{
  "mac":   "9C:9E:6E:77:17:50",
  "ts":    12345,
  "floor": 0,
  "imu":   [{"ts": 100, "ax": 0.01, "ay": 0.00, "az": 9.82, "gx": 0.00, "gy": 0.00, "gz": 0.01}],
  "wifi":  [{"bssid": "24:16:1B:76:28:C0", "ssid": "QU User", "rssi": -57, "ch": 11}]
}
```

Each packet carries 25 IMU samples and, when a fresh scan is available, the full AP list from the scanner.

---

## Backend Server

**Technology:** Python 3.11, FastAPI, PostgreSQL 16, SQLAlchemy 2.0 async, Docker.  
**Hosting:** GCP e2-micro, Nginx (port 443 only), self-signed TLS certificate.

### Request Pipeline

```
POST /api/v1/gateway/packet
  → Authenticate (X-API-Key)
  → Register MAC → TRAKN-XXXX tag ID
  → Run PDR on each IMU sample
  → If Wi-Fi scan present: run RSSI localizer → anchor PDR position
  → Broadcast via WebSocket
  → Return 200 OK
```

### Key Files

| File | Role |
|---|---|
| `backend/app/api/gateway.py` | Main packet handler + sensor fusion |
| `backend/app/api/websocket.py` | WebSocket position stream |
| `backend/app/fusion/pdr.py` | PDR engine |
| `backend/app/fusion/rssi_localizer.py` | RSSI Kalman + log-distance + weighted centroid |
| `backend/app/models.py` | SQLAlchemy ORM (Venue, FloorPlan, AccessPoint, GridPoint) |

---

## Positioning Algorithms

### Pedestrian Dead Reckoning (PDR)

PDR runs on each IMU sample and tracks position using step detection and heading integration.

1. **EMA filter** on accelerometer magnitude (cutoff 3.2 Hz)
2. **Gyro bias calibration** from the first 200 samples (~2 s)
3. **Step detection** — 5 conditions on a 200 ms rolling window (peak, swing, std, mean delta, cooldown)
4. **Weinberg stride length:** `L = 0.47 × (a_max − a_min)^0.25`, clamped to [0.25 m, 1.40 m]
5. **Heading integration** with 0.02 rad/s dead zone to suppress noise drift

**Measured accuracy:** 3.75% error over an 88-step, 64 m test loop.

### RSSI-Based Localization

RSSI localization runs every ~5 seconds when a scanner packet arrives and anchors the PDR position to prevent drift.

**Calibrated constants (H07-C corridor, measured on-site):**
- Reference RSSI at 1 m: −38.0 dBm (2.4 GHz, Aruba APs)
- Path-loss exponent: 2.1

**Pipeline:**
1. Collapse scan by physical AP prefix (first 5 BSSID octets)
2. Adaptive Kalman filter smooths RSSI per AP (asymmetric Q: trusts signal increases, resists sudden drops)
3. Log-distance model: `dist = 10^((−38 − rssi) / (10 × 2.1))`
4. Outlier rejection: remove APs > 2σ from mean distance; keep top 5
5. Inverse-square weighted centroid: `w = 1 / (d² + 0.1)`
6. Three-zone adaptive EMA on final position (α = 0.50 stationary / 0.85 walking / 0.95 fast), with 6 m/scan jump cap

### Sensor Fusion

PDR provides continuous heading and step-count updates. Every ~5 seconds, when a valid RSSI estimate is computed, the PDR position is snapped to the RSSI estimate. This prevents PDR drift (~1.5 m/min heading error) without requiring a full EKF implementation.

---

## Android Apps

### AP Survey Tool (`tools/trakn-ap-tool/`)

Used to register AP positions on the floor plan before deployment.

1. Select a floor plan from the server
2. Tap the AP's physical location on the map
3. The tool does a 5-second RSSI capture (500 ms intervals) and picks the strongest AP group
4. Confirm and save — all BSSIDs (2.4 GHz and 5 GHz bands) are stored with calibrated constants

**BSSID grouping:** Two BSSIDs belong to the same physical AP if their MAC address, excluding the last hex digit, is identical.

### Parent App (`tools/trakn-parent-app/`)

Displays the child's live position on the floor plan. Also localizes the parent's own phone using the same RSSI algorithm for distance-to-child calculation.

- Connects to `WSS /ws/position/{tag_id}` on startup
- Auto-follows the child's floor when the tag sends a barometer reading
- Triggers a push notification when the child is more than 20 m away

---

## Web Mapping Tool (`web_app/web-react/`)

A browser-based tool for setting up a venue before deployment.

**Workflow:** Upload floor plan image → Define zones → Generate grid (0.5 m spacing) → Place APs → Export

**Build:**
```bash
cd web_app/web-react
npm install
npm run build
```

The build output in `web_app/web/` is served by Nginx at `/tool/`.

---

## Deployment

```bash
# On the GCP VM
cd ~/child-localization
git pull
docker compose up -d --build backend
docker logs -f child-localization-backend-1 --tail 20
```

The stack runs behind Nginx on port 443. Port 8000 (FastAPI) is never exposed publicly.

---

## Database Schema

```
Venue         (id, name, description, created_at)
FloorPlan     (id, venue_id, name, floor_number, scale_px_per_m, image_path, created_at)
AccessPoint   (id, floor_plan_id, bssid, ssid, rssi_ref, path_loss_n, x, y)
GridPoint     (id, floor_plan_id, x, y)
```

---

## Key Results

| Metric | Value |
|---|---|
| PDR accuracy | 3.75% error over 64 m (88 steps) |
| RSSI scan interval | 5 seconds |
| HTTP POST interval | ~1 second |
| WebSocket update rate | ~1 s (IMU-only) / ~5 s (RSSI-anchored) |
| Path-loss exponent (H07-C) | 2.1 |
| Reference RSSI at 1 m | −38.0 dBm (2.4 GHz, Aruba) |
| Floor detection settling time | ~10 seconds |
| Position EMA — stationary | α = 0.50 |
| Position EMA — walking | α = 0.85 |
| Jump cap per scan | 6 m |

---

## Project Structure

```
TRAKN-Tracking-Indoors/
├── firmware/
│   ├── beetle_c6_main/new_trakn_tag/   ← Active tag firmware
│   └── beetle_c6_scanner/              ← Scanner board firmware
├── backend/
│   └── app/
│       ├── api/gateway.py              ← Packet handler + fusion
│       ├── api/websocket.py            ← WebSocket stream
│       ├── fusion/pdr.py               ← PDR engine
│       └── fusion/rssi_localizer.py    ← RSSI positioning
├── web_app/web-react/                  ← Web mapping tool (source)
├── web_app/web/                        ← Built web tool (served by Nginx)
├── tools/trakn-ap-tool/                ← Android AP survey app
├── tools/trakn-parent-app/             ← Android parent tracking app
├── docker-compose.yml
└── nginx/nginx.conf
```

---

## Team

Senior Design Project — Qatar University, Department of Computer Engineering, 2025–2026.
