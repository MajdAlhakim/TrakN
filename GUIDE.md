# TRAKN — Installation, Deployment, and Usage Guide

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Prerequisites](#2-prerequisites)
3. [Server Deployment](#3-server-deployment)
4. [Web Mapping Tool — Venue Setup](#4-web-mapping-tool--venue-setup)
5. [AP Survey Tool — Access Point Calibration](#5-ap-survey-tool--access-point-calibration)
6. [Firmware — Wearable Tag](#6-firmware--wearable-tag)
7. [Parent App — Live Tracking](#7-parent-app--live-tracking)
8. [First-Time Setup Checklist](#8-first-time-setup-checklist)
9. [API Reference](#9-api-reference)

---

## 1. System Overview

TRAKN tracks a child's indoor position in real time. The wearable tag collects IMU and Wi-Fi RSSI data and POSTs it to the cloud server every second. The server runs PDR and RSSI-based fusion to estimate position and streams it to the parent's Android app over WebSocket.

```
[Scanner ESP32-C6] ──UART──▶ [Main ESP32-C6] ──HTTPS──▶ [GCP Server] ──WSS──▶ [Parent App]
```

---

## 2. Prerequisites

### Server
- GCP e2-micro VM (or equivalent), Ubuntu 22.04
- Docker and Docker Compose installed
- Domain name pointed at the VM (used for TLS — project uses `trakn.duckdns.org`)
- Let's Encrypt TLS certificate issued for the domain

### Firmware
- Arduino IDE 2.x
- Espressif ESP32 board support package installed via Boards Manager
- Two Beetle ESP32-C6 boards
- MPU6050 IMU, BMP180 barometer

### Android Apps
- Android Studio (for building from source)
- Android device running Android 8.0 (API 26) or higher
- Device connected to the same Wi-Fi network as the server during AP calibration

---

## 3. Server Deployment

### 3.1 Clone the repository

```bash
git clone https://github.com/sdp2025f/g33.git
cd g33
```

### 3.2 Create the environment file

Create a `.env` file in the project root:

```env
DATABASE_URL=postgresql+asyncpg://admin:changeme@db:5432/localization
GATEWAY_API_KEY=your_secret_api_key_here
BEETLE_RSSI_OFFSET_DB=4.0
```

| Variable | Description |
|---|---|
| `DATABASE_URL` | PostgreSQL connection string (keep host as `db` for Docker) |
| `GATEWAY_API_KEY` | Secret key the tag sends in the `X-API-Key` header |
| `BEETLE_RSSI_OFFSET_DB` | RSSI hardware offset correction in dBm (default: 4.0) |

### 3.3 Issue a TLS certificate

```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot certonly --standalone -d your-domain.com
```

Update `nginx/nginx.conf` to replace `trakn.duckdns.org` with your domain.

### 3.4 Start the stack

```bash
sudo docker compose up -d --build
```

This starts three containers: `db` (PostgreSQL 16), `backend` (FastAPI), and `nginx` (TLS + reverse proxy).

### 3.5 Verify

```bash
curl https://your-domain.com/health
# Expected: {"status":"ok"}
```

### 3.6 View logs

```bash
sudo docker compose logs backend --tail=50
sudo docker compose logs nginx --tail=20
```

### 3.7 Redeployment after code changes

```bash
git pull
sudo docker compose up -d --build backend
```

---

## 4. Web Mapping Tool — Venue Setup

The web tool runs at `https://your-domain.com/tool/`. Use it to configure a venue before deploying the tag.

### Step 1 — Create a venue

1. Open `https://your-domain.com/tool/` in a browser
2. Click **New Venue** and enter a name
3. Click **Add Floor Plan**, upload a floor plan image (PNG or JPG), and set the scale (pixels per metre)

### Step 2 — Define zones (optional)

Draw zones on the floor plan to label areas (e.g., "Corridor", "Room A"). Zones are used for display only.

### Step 3 — Generate the grid

Go to the **Grid** panel and click **Generate Grid**. The tool places grid points at 0.5 m intervals across the floor plan. These are used internally by the server for bounds checking.

### Step 4 — Place access points

Go to the **APs** panel. For each physical AP in the space:

1. Tap its location on the floor plan image
2. The tool records the pixel coordinates and converts them to metres using the scale

> APs are registered in detail using the Android AP Survey Tool (see Section 5). The web tool is for initial placement only.

### Step 5 — Export

Click **Export** to save the venue configuration to the server. The server stores it in PostgreSQL and uses it for localization.

---

## 5. AP Survey Tool — Access Point Calibration

The AP Survey Tool (Android app at `tools/trakn-ap-tool/`) is used on-site to register each physical AP's RSSI signature.

### Build and install

1. Open `tools/trakn-ap-tool/` in Android Studio
2. Update `local.properties` with your Android SDK path
3. Edit `RetrofitClient.kt` to point to your server URL
4. Build and install on an Android device

### Calibration workflow

For each physical AP in the venue:

1. **Stand at the AP's physical location** (or as close as possible)
2. Open the app and select the floor plan
3. Tap the AP's position on the map
4. The app performs a **5-second RSSI capture** (500 ms intervals, 10 readings)
5. It selects the strongest AP group by BSSID prefix (first 5 octets)
6. Confirm and save — the server stores the BSSID, SSID, RSSI, x/y coordinates, and calibrated path-loss constants

> Run the survey when the building is in normal operating conditions (people present, typical interference). An empty building gives an overly optimistic RSSI that will not match real deployment.

---

## 6. Firmware — Wearable Tag

### 6.1 Hardware connections

**Main board (IMU + HTTPS):**

| Sensor | Pin |
|---|---|
| MPU6050 SDA | GPIO19 |
| MPU6050 SCL | GPIO20 |
| BMP180 SDA | GPIO19 (shared) |
| BMP180 SCL | GPIO20 (shared) |
| UART RX (from scanner) | GPIO17 |

**Scanner board:**

| Connection | Pin |
|---|---|
| UART TX (to main board) | GPIO16 |

UART link: 115200 baud, scanner TX → main board RX.

### 6.2 Flash the main board

1. Open `firmware/beetle_c6_main/beetle_c6_main/beetle_c6_main.ino` in Arduino IDE
2. Select board: `Tools → Board → esp32 → Beetle ESP32-C6`
3. Enable USB CDC: `Tools → USB CDC On Boot → Enabled`
4. Edit the following constants at the top of the sketch:

```cpp
const char* WIFI_SSID     = "your_network_ssid";
const char* WIFI_PASSWORD = "your_network_password";
const char* SERVER_URL    = "https://your-domain.com/api/v1/gateway/packet";
const char* API_KEY       = "your_secret_api_key_here";   // must match GATEWAY_API_KEY in .env
```

5. Click **Upload**

### 6.3 Flash the scanner board

1. Open `firmware/beetle_c6_scanner/beetle_c6_scanner.ino` in Arduino IDE
2. Select the same board and USB CDC settings
3. Click **Upload**

### 6.4 Verify operation

Open the Serial Monitor (115200 baud) on the main board. You should see:

```
[WIFI] Connected. IP: 192.168.x.x
[IMU]  Calibrating gyro bias...
[POST] 200 OK — {"tag_id":"TRAKN-0001"}
```

The tag registers itself automatically on first POST using its MAC address. The server assigns it a `TRAKN-XXXX` ID.

---

## 7. Parent App — Live Tracking

The parent app (`tools/trakn-parent-app/`) displays the child's live position on the floor plan.

### Build and install

1. Open `tools/trakn-parent-app/` in Android Studio
2. Update `local.properties` with your Android SDK path
3. Edit `RetrofitClient.kt` to point to your server URL
4. Build and install on the parent's Android device

### Usage

1. **Open the app** — it connects to the server and loads the floor plan
2. **Select the tag** — choose the tag ID assigned to the child's device (`TRAKN-XXXX`)
3. The app opens a WebSocket connection to `wss://your-domain.com/ws/position/{tag_id}`
4. The child's position is shown as a dot on the floor plan, updated every ~1 second
5. The app also localizes the **parent's own phone** using Wi-Fi RSSI to show distance to child
6. A **push notification** is triggered when the child is more than 20 m away

### Floor switching

The tag's barometer detects floor changes automatically. The app follows the child's floor when the server sends a barometer reading. A 10-reading confirmation window (~10 seconds) is required before committing a floor change to prevent false triggers.

---

## 8. First-Time Setup Checklist

Follow this order when deploying the system in a new building:

- [ ] Deploy the server (Section 3)
- [ ] Upload the floor plan and generate the grid using the web tool (Section 4)
- [ ] Walk the corridor and calibrate each AP using the Android AP Survey Tool (Section 5)
- [ ] Flash both ESP32-C6 boards with the correct Wi-Fi credentials and server URL (Section 6)
- [ ] Power on the tag and confirm it posts successfully (check server logs)
- [ ] Install and open the parent app, select the tag, and confirm live position appears (Section 7)

---

## 9. API Reference

All endpoints are served at `https://your-domain.com`.

| Endpoint | Method | Auth | Description |
|---|---|---|---|
| `/health` | GET | None | Server health check |
| `/api/v1/gateway/packet` | POST | `X-API-Key` | Receive IMU + Wi-Fi packet from tag |
| `/api/v1/venue/floor-plan` | GET | `X-API-Key` | Get active floor plan |
| `/api/v1/venue/floor-plan/image` | GET | None | Download floor plan image |
| `/api/v1/venue/aps` | GET | `X-API-Key` | List registered access points |
| `/api/v1/venue/aps` | POST | `X-API-Key` | Register an access point |
| `/api/v1/venue/grid` | POST | `X-API-Key` | Upload grid points |
| `/api/v1/tags` | GET | `X-API-Key` | List registered tags |
| `/ws/position/{tag_id}` | WebSocket | None | Live position stream for a tag |

### Packet format (tag → server)

```json
{
  "mac":   "9C:9E:6E:77:17:50",
  "ts":    12345,
  "floor": 0,
  "imu":   [{"ts": 100, "ax": 0.01, "ay": 0.00, "az": 9.82, "gx": 0.00, "gy": 0.00, "gz": 0.01}],
  "wifi":  [{"bssid": "24:16:1B:76:28:C0", "ssid": "QU User", "rssi": -57, "ch": 11}]
}
```

### WebSocket message (server → parent app)

```json
{
  "tag_id":       "TRAKN-0001",
  "x":            12.450,
  "y":            3.820,
  "floor":        0,
  "step_count":   142,
  "anchor_count": 4,
  "heading_deg":  270.0
}
```
