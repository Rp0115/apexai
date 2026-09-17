-- ApexAI quick checks after Japan GP ingestion
SELECT COUNT(*) AS drivers FROM drivers;
SELECT COUNT(*) AS laps FROM laps;
SELECT driver_name, lap_number, sector2_time, total_lap_time
FROM laps
WHERE sector2_time IS NOT NULL
ORDER BY sector2_time ASC
LIMIT 5;
