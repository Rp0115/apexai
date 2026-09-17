# ApexAI — The Virtual Race Engineer

High-performance F1 analytics over **any OpenF1 race** (defaults to 2023 Japan): natural-language questions over SQL lap timing + steward-archive RAG, streamed as race-radio SSE.

Ask the Oracle — it resolves the race from your prompt, loads OpenF1 data if needed, answers directly over race radio, and updates the Session Brief for the active race.

## Data source — OpenF1

Telemetry comes from the public **[OpenF1 API](https://openf1.org/)** (`https://api.openf1.org/v1`). ApexAI resolves meetings/sessions, then pulls drivers, laps, session results, and related timing for the active `session_key`.

**Coverage is limited to the 2023 season onward.** OpenF1 does not provide historical races before 2023 — questions about earlier years (e.g. 2021 or 2022) will not resolve to real session data.

## What you can ask

Name the race (and year when it matters; **2023+ only**). Venue beats country when a country has multiple GPs (e.g. Barcelona vs Madrid; Miami / Austin / Las Vegas).


| Ask about                    | Example prompts                                                                                          |
| ---------------------------- | -------------------------------------------------------------------------------------------------------- |
| **Race winner**              | Who won Monaco 2023? · Who won Vegas 2025? · Who won Barcelona 2026?                                     |
| **Podium**                   | What was the podium at Silverstone 2023?                                                                 |
| **Fastest lap**              | Who had the fastest lap at Spa 2023? · Fastest lap Suzuka 2023                                           |
| **Sector times**             | Fastest S1 at Monza 2023? · Fastest S2 at Silverstone 2023? · Fastest S3 Miami 2023                      |
| **Driver performance**       | How did Hamilton perform in Belgium 2023? · Norris summary Austin 2023                                   |
| **Speed / lap trace**        | Show me a speed trace for Verstappen lap 15 at Suzuka 2023 · Speed trace for Antonelli lap 3 Madrid 2026 |
| **Session brief / overview** | Session brief for Japan 2023 · Give me an overview of Monaco 2023                                        |
| **Stewards / FIA**           | Any steward penalties at Japan 2023? · Track limits incidents Suzuka                                     |


Tips:

- Race data is **2023 and later only** (OpenF1 limitation).
- Prefer venue names for multi-GP countries: **Barcelona / Madrid**, **Miami / Austin / Las Vegas** (not just “Spain” or “USA”).
- Speed traces need a **driver** and ideally a **lap number**; the chart appears under race radio.
- The full timing dump (winner, podium, fastest lap, sectors, speed trap) lives in **Session Brief**, not in every short answer.



## Architecture


| Module                | Role                                                          |
| --------------------- | ------------------------------------------------------------- |
| **Gateway (Paddock)** | `/api/`** entry + per-IP rate limit                           |
| **Telemetry**         | PostgreSQL lap/sector/speed-trace queries (per `session_key`) |
| **Session**           | Resolves race from the prompt + on-demand OpenF1 ingest       |
| **Steward**           | Narrative archive + pgvector RAG (when AI enabled)            |
| **Oracle**            | Hybrid SQL / tools / RAG; SSE race radio                      |
| **Batch**             | Manual ingest for any session (`/api/batch/ingest`)           |




## Quick start (local)



### 1. Database

```bash
docker compose -f compose.yaml up -d
```



### 2. Backend

Put your Gemini key in a local `.env` once (gitignored):

```powershell
copy .env.example .env
# edit .env → set GEMINI_API_KEY=your_key
```

Then run:

```powershell
.\mvnw.cmd spring-boot:run
```

`.env` can set `SPRING_PROFILES_ACTIVE=ai` so you don't pass profiles each time.
For hybrid mode without AI, leave `SPRING_PROFILES_ACTIVE` empty in `.env`.

### 3. Ingest (optional)

You usually **don’t need this** — asking the Oracle about a race auto-loads it.

Manual ingest (PowerShell):

```powershell
# Default configured session (Japan 2023)
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/batch/japan-2023

# Any race by name
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/batch/ingest?year=2023&race=Monaco"

# Or by OpenF1 session key
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/batch/ingest?sessionKey=9122"

# List 2023 race sessions
Invoke-RestMethod "http://localhost:8080/api/batch/races?year=2023" | Format-Table
```

`curl.exe` alternative:

```powershell
curl.exe -X POST "http://localhost:8080/api/batch/ingest?year=2023&race=Belgium"
```



### 4. Frontend

```bash
cd frontend
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173) and ask anything from the table above.

### Full stack in Docker

```bash
docker compose -f docker-compose.yml up --build
```

- UI: [http://localhost:3000](http://localhost:3000)
- API: [http://localhost:8080](http://localhost:8080)



## Key endpoints

- `GET /api/health` — gateway map
- `GET /api/oracle/ask?q=...` — SSE race radio (auto session resolve + ingest)
- `GET /api/batch/races?year=2023` — list race sessions
- `POST /api/batch/ingest?year=2023&race=Monaco` — manual ingest
- `GET /api/telemetry/stats?sessionKey=9173` — session brief summary
- `GET /api/telemetry/speed-trace?driverNumber=1&lapNumber=10&sessionKey=9173`
- `GET /actuator/metrics/apexai.ai.latency` — AI latency



## AI profile notes

Requires `spring-ai-starter-model-google-genai-embedding` for pgvector. Default profile uses `spring.ai.model.*=none`; profile `ai` switches chat/embeddings/vector store on.