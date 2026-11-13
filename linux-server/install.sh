#!/usr/bin/env bash
set -euo pipefail

if ! command -v python3 >/dev/null 2>&1; then
  echo "Python 3 is required. Install it first (for Ubuntu/Debian: sudo apt install python3 python3-venv)."
  exit 1
fi

BASE_DIR="/opt/sensor-server"
CURRENT_USER="${SUDO_USER:-$USER}"

sudo mkdir -p "$BASE_DIR/data"
sudo cp server.py requirements.txt "$BASE_DIR/"
sudo chown -R "$CURRENT_USER":"$(id -gn "$CURRENT_USER")" "$BASE_DIR"

sudo -u "$CURRENT_USER" python3 -m venv "$BASE_DIR/venv"
sudo -u "$CURRENT_USER" "$BASE_DIR/venv/bin/pip" install --upgrade pip
sudo -u "$CURRENT_USER" "$BASE_DIR/venv/bin/pip" install -r "$BASE_DIR/requirements.txt"

sudo tee /etc/systemd/system/sensor-server.service >/dev/null <<EOF
[Unit]
Description=Android Sensor Collector HTTP Server
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$CURRENT_USER
WorkingDirectory=$BASE_DIR
Environment=SENSOR_DATA_DIR=$BASE_DIR/data
ExecStart=$BASE_DIR/venv/bin/python $BASE_DIR/server.py
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now sensor-server

if command -v ufw >/dev/null 2>&1; then
  echo "If UFW is active, allowing TCP port 5000..."
  sudo ufw allow 5000/tcp || true
fi

echo
echo "Sensor server is running."
echo "Health check: curl http://127.0.0.1:5000/health"
echo "Computer LAN IP(s):"
hostname -I || true
echo "Enter this in the Android app: http://<COMPUTER-IP>:5000/sensors"
