from flask import Flask, request, jsonify
from datetime import datetime, timezone
from pathlib import Path
import json
import os

app = Flask(__name__)
DATA_DIR = Path(os.environ.get("SENSOR_DATA_DIR", "./data"))
DATA_DIR.mkdir(parents=True, exist_ok=True)
DATA_FILE = DATA_DIR / "sensor_data.jsonl"

@app.get("/health")
def health():
    return jsonify({"status": "ok", "service": "sensor-server"})

@app.post("/sensors")
def receive_sensors():
    payload = request.get_json(silent=True)
    if not isinstance(payload, dict):
        return jsonify({"error": "Expected a JSON object"}), 400

    sensors = payload.get("sensors", [])
    if not isinstance(sensors, list):
        return jsonify({"error": "'sensors' must be a list"}), 400

    record = {
        "received_at": datetime.now(timezone.utc).isoformat(),
        **payload
    }
    with DATA_FILE.open("a", encoding="utf-8") as f:
        f.write(json.dumps(record, ensure_ascii=False) + "\n")

    print(
        f"Received {len(sensors)} sensor readings "
        f"from {payload.get('device', 'unknown device')}",
        flush=True
    )
    return jsonify({"status": "success", "received": len(sensors)}), 200

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)
