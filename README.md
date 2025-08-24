# Weather Metrics API (Java / Spring Boot)

A small REST API that uses weather sensor readings (temperature, humidity, wind_speed), stores them in an H2 **file** database, and supports querying a time window and computing basic stats (min, max, sum, avg).

## Info

- Minimal model: `sensorId`, `metricType`, `metricValue`, `timestamp`.
- Input hygiene: metric types are normalized to lowercase and validated (allowed: `temperature`, `humidity`, `wind_speed`).
- H2: runs without external setup and **persists** between restarts under `./.data/weatherdb`.
- Focused endpoints: ingest (POST), list (GET), window query, A 1–31 day window.


---

## Prerequisites

- Java 17 or 21 (tested with Java 21).

---

## Get the code

```bash
git clone https://github.com/ConorRoberts1/weatherAPI
cd weatherAPI
```

---

## Run the jar 

If the jar is already present (this repo includes a built jar), run it directly:

```bash
cd target
java -jar weatherAPI-0.0.1-SNAPSHOT.jar
```

The API will start on http://localhost:8080

---

## H2 Console

While the app is running, the H2 console is available at: http://localhost:8080/h2-console

Use these connection settings:
- JDBC URL: `jdbc:h2:file:./.data/weatherdb`
- User: `sa`
- Password: (leave empty)

---

## Starter commands

### Windows (PowerShell)

Create a reading (POST):
```powershell
$body = @{ sensorId = 1; metricType = "Temperature"; metricValue = 21.3 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/metrics" -ContentType "application/json" -Body $body
```

List all (GET):
```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/metrics"
```

Query a window (GET):
```powershell
$start = "2025-08-24T00:00:00"
$end   = "2025-08-25T00:00:00"
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/metrics/query?sensorId=1&metricType=temperature&start=$start&end=$end"
```

Stats examples (GET):
```powershell
# Default last 24h, avg across all sensors/metrics
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/metrics/stats"

# Max temperature for an explicit 1-day window
$start = "2025-08-24T00:00:00"
$end   = "2025-08-25T00:00:00"
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/metrics/stats?metrics=temperature&stat=max&start=$start&end=$end"
```

### Windows using curl.exe
```powershell
curl.exe -s -X POST "http://localhost:8080/api/metrics" ^
  -H "Content-Type: application/json" ^
  -d "{\"sensorId\":1,\"metricType\":\"Temperature\",\"metricValue\":21.3}"

curl.exe -s "http://localhost:8080/api/metrics"

curl.exe -s "http://localhost:8080/api/metrics/query?sensorId=1&metricType=temperature&start=2025-08-24T00:00:00&end=2025-08-25T00:00:00"
```

### macOS / Linux (curl)
```bash
curl -s -X POST http://localhost:8080/api/metrics \
  -H "Content-Type: application/json" \
  -d '{"sensorId":1,"metricType":"Temperature","metricValue":21.3}'

curl -s http://localhost:8080/api/metrics

curl -s "http://localhost:8080/api/metrics/query?sensorId=1&metricType=temperature&start=2025-08-24T00:00:00&end=2025-08-25T00:00:00"
```

---

## Optional: Seed sample data

PowerShell:
```powershell
$uri = "http://localhost:8080/api/metrics"
$headers = @{ "Content-Type" = "application/json" }
$readings = @(
  @{ sensorId=1; metricType="temperature"; metricValue=21.3; timestamp="2025-08-24T10:00:00" },
  @{ sensorId=1; metricType="temperature"; metricValue=19.7; timestamp="2025-08-24T07:00:00" },
  @{ sensorId=1; metricType="humidity";    metricValue=0.55; timestamp="2025-08-24T10:05:00" },
  @{ sensorId=1; metricType="wind_speed";  metricValue=5.2;  timestamp="2025-08-24T09:00:00" },
  @{ sensorId=2; metricType="temperature"; metricValue=25.0; timestamp="2025-08-24T11:00:00" }
)
$readings | ForEach-Object {
  $_ | ConvertTo-Json | % { Invoke-RestMethod -Method Post -Uri $uri -Headers $headers -Body $_ }
}
```

H2 Console (SQL):
```sql
INSERT INTO sensor_metric (sensor_id, metric_type, metric_value, "timestamp") VALUES
  (1, 'temperature', 21.3, TIMESTAMP '2025-08-24 10:00:00'),
  (1, 'temperature', 19.7, TIMESTAMP '2025-08-24 07:00:00'),
  (1, 'humidity',     0.55, TIMESTAMP '2025-08-24 10:05:00'),
  (1, 'wind_speed',   5.2,  TIMESTAMP '2025-08-24 09:00:00'),
  (2, 'temperature', 25.0, TIMESTAMP '2025-08-24 11:00:00');
```

---

## Run tests 

From the repo root:
- Windows: `mvnw.cmd test`
- macOS/Linux: `./mvnw test`

---

