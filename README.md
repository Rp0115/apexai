# ApexAI — The Virtual Race Engineer

**Live app:** [https://apexai-xbu8.onrender.com/](https://apexai-xbu8.onrender.com/)

High-performance F1 analytics over **any OpenF1 race** (defaults to 2023 Japan): natural-language questions over SQL lap timing + steward-archive RAG, streamed as race-radio SSE.

Ask the Oracle — it resolves the race from your prompt, loads OpenF1 data if needed, answers directly over race radio, and updates the Session Brief for the active race.

## Why I Built This?

As an F1 fan, I am always looking back at past race weekends to analyze driver pace, compare stint strategies, and review historical timing stats. Most official archives provide static results tables, making it difficult to visualize how a previous Grand Prix actually unfolded.  
I built **ApexAI** to turn historical Formula 1 session archives into an interactive analytics tool, allowing users to easily pull speed traces, review past lap times, and query completed races through natural language without combing through raw timing logs.  
I believe this could be helpful for people who like to participate in F1 Fantasy!

## Data source — OpenF1

Telemetry comes from the public **[OpenF1 API](https://openf1.org/)** (`https://api.openf1.org/v1`). ApexAI resolves meetings/sessions, then pulls drivers, laps, session results, and related timing for the active `session_key`.

**Coverage is limited to the 2023 season onward.** OpenF1 does not provide historical races before 2023 — questions about earlier years (e.g. 2021 or 2022) will not resolve to real session data.

## What you can ask

Name the race (and year when it matters; **2023+ only**). Venue beats country when a country has multiple GPs (e.g. Barcelona vs Madrid; Miami / Austin / Las Vegas).


| Ask about                    | Example prompts                                                                                                 |
| ---------------------------- | --------------------------------------------------------------------------------------------------------------- |
| **Race winner**              | Who won Monaco 2023? · Who won Vegas 2025? · Who won Barcelona 2026?                                            |
| **Podium**                   | What was the podium at Silverstone 2024?                                                                        |
| **Qualifying**               | Which position did Hamilton qualify in Suzuka 2023? · Who took pole at Monaco 2024? · Qualifying order Spa 2024 |
| **Fastest lap**              | Who had the fastest lap at Spa 2024? · Fastest lap Suzuka 2023                                                  |
| **Sector times**             | Fastest S1 at Monza 2025? · Fastest S2 at Silverstone 2024? · Fastest S3 Miami 2023                             |
| **Driver performance**       | How did Hamilton perform in Belgium 2023? · Norris summary Austin 2025                                          |
| **Speed / lap trace**        | Show me a speed trace for Verstappen lap 15 at Suzuka 2023 · Speed trace for Antonelli lap 3 Madrid 2026        |
| **Championships**            | Who won the 2025 drivers and constructors championships? · 2024 WDC · Who won the 2023 constructors title?      |
| **Session brief / overview** | Session brief for Japan 2023 · Give me an overview of Monaco 2023                                               |
| **Stewards / FIA**           | Any steward penalties at Japan 2024? · Track limits incidents Suzuka                                            |


Tips:

- Race data is **2023 and later only** (OpenF1 limitation).
- Prefer venue names for multi-GP countries: **Barcelona / Madrid**, **Miami / Austin / Las Vegas** (not just “Spain” or “USA”).
- **Sprint vs main race:** asking about a GP (e.g. Belgium 2023) uses the Sunday **Race**. Say “sprint” explicitly for the Sprint (e.g. Belgium sprint 2023). If that weekend had no Sprint in OpenF1, ApexAI says so instead of guessing another race.
- **Qualifying:** ask “qualify”, “qualifying”, or “pole” to load the Qualifying session (not the Race). Named drivers get their grid position (e.g. Hamilton P7 at Suzuka 2023).
- **Championships:** drivers’ / constructors’ titles use OpenF1 standings after the **latest Race** of that year (season champions when the finale is in; otherwise current leaders).
- Speed traces need a **driver** and ideally a **lap number**; the chart appears under race radio.
- The full timing dump (winner, podium, fastest lap, sectors, speed trap) lives in **Session Brief**, not in every short answer.



## Architecture & tech stack

**Deployed stack**

| Layer | Technology |
| ----- | ---------- |
| **Frontend hosting** | [Render.com](https://render.com/) static site — [apexai-xbu8.onrender.com](https://apexai-xbu8.onrender.com/) |
| **API compute** | AWS **EC2** (Spring Boot / Docker on port 8080) |
| **Database** | AWS **RDS** PostgreSQL (+ pgvector for AI/RAG) |
| **Networking** | AWS **VPC** — EC2 and RDS in the same VPC; RDS locked to the EC2 security group |
| **Telemetry source** | [OpenF1 API](https://openf1.org/) (2023+ sessions) |

```
Browser → Render (React UI)
              │  VITE_API_BASE (HTTPS tunnel / API URL)
              ▼
         EC2 :8080 (Spring Boot)
              │  JDBC (private)
              ▼
         RDS PostgreSQL (VPC)
              │
              ▼
         OpenF1 API
```

**Application modules**

| Module                | Role                                                          |
| --------------------- | ------------------------------------------------------------- |
| **Gateway (Paddock)** | `/api/**` entry + per-IP rate limit                           |
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