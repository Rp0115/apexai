import { useActionState, useOptimistic, useState, useEffect, useRef, startTransition } from 'react'
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
} from 'recharts'

const API = import.meta.env.VITE_API_BASE || ''

const PROMPTS = [
  { label: 'Race winner', text: 'Who won Vegas 2025?' },
  { label: 'Podium', text: 'What was the podium at Silverstone 2024?' },
  { label: 'Qualifying', text: 'Which position did Hamilton qualify in Suzuka 2023?' },
  { label: 'Fastest lap', text: 'Who had the fastest lap at Spa 2024?' },
  { label: 'Sector times', text: 'Fastest S2 at Monza 2025?' },
  { label: 'Driver performance', text: 'How did Norris perform in Austin 2025?' },
  { label: 'Speed / lap trace', text: 'Speed trace for Antonelli lap 3 Madrid 2026' },
  { label: 'Championships', text: 'Who won the 2025 drivers and constructors championships?' },
  { label: 'Session brief', text: 'Session brief for Barcelona 2026' },
  { label: 'Stewards / FIA', text: 'Any steward penalties at Japan 2024?' },
]

async function askOracle(previous, formData) {
  const question = String(formData.get('question') || '').trim()
  if (!question) {
    return { ...previous, error: 'Ask a race engineer question.' }
  }

  const response = await fetch(`${API}/api/oracle/ask?q=${encodeURIComponent(question)}`, {
    headers: { Accept: 'text/event-stream' },
  })

  if (!response.ok || !response.body) {
    return { ...previous, error: `Oracle unavailable (${response.status})`, question }
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let answer = ''
  let speedTrace = null
  let podium = null
  let session = null
  let eventName = 'message'

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const chunks = buffer.split('\n')
    buffer = chunks.pop() || ''

    for (const line of chunks) {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        let data = line.slice(5)
        if (data.startsWith(' ')) data = data.slice(1)
        if (eventName === 'token') {
          answer += data === '{{NL}}' ? '\n' : data
          if (previous.onToken) previous.onToken(answer)
        } else if (eventName === 'speed-trace') {
          try {
            speedTrace = JSON.parse(data.trim())
          } catch {
            speedTrace = null
          }
        } else if (eventName === 'podium') {
          try {
            podium = JSON.parse(data.trim())
          } catch {
            podium = null
          }
        } else if (eventName === 'session') {
          try {
            session = JSON.parse(data.trim())
            if (previous.onSession) previous.onSession(session)
          } catch {
            session = null
          }
        } else if (eventName === 'error') {
          return { question, answer, error: data.trim(), speedTrace, podium, session }
        }
        eventName = 'message'
      }
    }
  }

  return { question, answer, error: null, speedTrace, podium, session }
}

function PodiumStand({ podium }) {
  if (!podium?.entries?.length) return null

  const byPos = Object.fromEntries(podium.entries.map((e) => [e.position, e]))
  const order = [byPos[2], byPos[1], byPos[3]].filter(Boolean)
  const heights = { 1: 'h-36', 2: 'h-28', 3: 'h-24' }

  return (
    <section className="radio-enter mt-8 border-t border-[var(--line)] pt-6">
      <p className="display text-sm uppercase tracking-[0.18em] text-[var(--signal)]">Podium</p>
      <h2 className="display mt-1 text-2xl font-bold leading-none sm:text-3xl">{podium.label}</h2>
      <div className="mt-8 flex items-end justify-center gap-3 sm:gap-6">
        {order.map((entry) => (
          <div key={entry.position} className="flex w-28 flex-col items-center sm:w-36">
            <div className="mb-3 flex h-24 w-24 items-end justify-center overflow-hidden sm:h-28 sm:w-28">
              {entry.headshotUrl ? (
                <img
                  src={entry.headshotUrl}
                  alt={entry.broadcastName}
                  className="max-h-full max-w-full object-contain"
                  loading="lazy"
                />
              ) : (
                <div className="flex h-full w-full items-center justify-center bg-[var(--mist)] text-2xl font-bold text-[var(--carbon)]/40">
                  {entry.position}
                </div>
              )}
            </div>
            <p className="display text-center text-xs font-bold uppercase tracking-[0.14em] text-[var(--signal)]">
              P{entry.position}
            </p>
            <p className="mt-1 text-center text-sm font-semibold text-[var(--ink)]">{entry.broadcastName}</p>
            {entry.teamName && (
              <p className="mt-0.5 text-center text-xs text-[var(--carbon)]/60">{entry.teamName}</p>
            )}
            <div
              className={`mt-3 w-full border border-[var(--line)] bg-[var(--mist)] ${heights[entry.position] || 'h-20'}`}
            />
          </div>
        ))}
      </div>
    </section>
  )
}

function SpeedTraceChart({ driverNumber, lapNumber, sessionKey }) {
  const [points, setPoints] = useState([])
  const [meta, setMeta] = useState(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      const qs = new URLSearchParams({
        driverNumber: String(driverNumber),
        lapNumber: String(lapNumber),
      })
      if (sessionKey) qs.set('sessionKey', String(sessionKey))
      const res = await fetch(`${API}/api/telemetry/speed-trace?${qs}`)
      if (!res.ok) return
      const json = await res.json()
      if (cancelled) return
      setMeta(json)
      setPoints(
        (json.samples || []).map((s) => ({
          distance: Math.round(s.distanceMeters),
          speed: s.speedKph,
          gear: s.gear,
        })),
      )
    }
    load()
    return () => {
      cancelled = true
    }
  }, [driverNumber, lapNumber, sessionKey])

  if (!points.length) return null

  return (
    <section className="radio-enter mt-8 border-t border-[var(--line)] pt-6">
      <div className="mb-3 flex items-end justify-between gap-4">
        <div>
          <p className="display text-sm uppercase tracking-[0.18em] text-[var(--signal)]">Speed Trace</p>
          <h2 className="display text-3xl font-bold leading-none">
            {meta?.driverName || `Driver #${driverNumber}`} · Lap {lapNumber}
          </h2>
        </div>
        <p className="text-sm text-[var(--carbon)]/70">Distance vs speed</p>
      </div>
      <div className="h-64 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={points} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
            <CartesianGrid stroke="#d2cec5" strokeDasharray="3 6" />
            <XAxis dataKey="distance" tick={{ fontSize: 11 }} unit="m" />
            <YAxis dataKey="speed" tick={{ fontSize: 11 }} unit=" km/h" width={56} />
            <Tooltip
              contentStyle={{
                background: '#f7f5f0',
                border: '1px solid #b8b3a8',
                borderRadius: 0,
                fontFamily: 'IBM Plex Sans, sans-serif',
              }}
            />
            <Line
              type="monotone"
              dataKey="speed"
              stroke="#e10600"
              strokeWidth={2.4}
              dot={false}
              isAnimationActive
              animationDuration={900}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </section>
  )
}

export default function App() {
  const [liveText, setLiveText] = useState('')
  const [stats, setStats] = useState('')
  const [activeSession, setActiveSession] = useState(null)
  const formRef = useRef(null)

  const [state, formAction, pending] = useActionState(
    (prev, formData) =>
      askOracle(
        {
          ...prev,
          onToken: (text) => startTransition(() => setLiveText(text)),
          onSession: (session) => {
            if (session?.sessionKey) {
              startTransition(() => setActiveSession(session))
            }
          },
        },
        formData,
      ),
    { question: '', answer: '', error: null, speedTrace: null, podium: null, session: null },
  )

  const [optimisticAnswer, setOptimisticAnswer] = useOptimistic(
    liveText || state.answer || '',
    (_current, next) => next,
  )

  const sessionKey = state.session?.sessionKey ?? activeSession?.sessionKey
  const briefLabel = state.session?.label ?? activeSession?.label

  useEffect(() => {
    let cancelled = false
    async function loadBrief() {
      const qs = sessionKey ? `?sessionKey=${sessionKey}` : ''
      try {
        const res = await fetch(`${API}/api/telemetry/stats${qs}`)
        const json = await res.json()
        if (!cancelled) setStats(json.summary || '')
      } catch {
        if (!cancelled) setStats('Backend offline — start Spring Boot on :8080')
      }
    }
    loadBrief()
    return () => {
      cancelled = true
    }
  }, [sessionKey, state.answer])

  useEffect(() => {
    if (state.session?.sessionKey) {
      setActiveSession(state.session)
    }
  }, [state.session])

  useEffect(() => {
    if (!pending && state.answer) {
      setLiveText(state.answer)
    }
  }, [pending, state.answer])

  function submitPrompt(text) {
    const form = formRef.current
    if (!form) return
    form.question.value = text
    startTransition(() => {
      setOptimisticAnswer('Connecting race radio…')
      setLiveText('')
      form.requestSubmit()
    })
  }

  return (
    <div className="relative mx-auto min-h-screen max-w-5xl px-5 pb-16 pt-8 sm:px-8">
      <header className="radio-enter mb-12">
        <h1 className="display text-6xl font-extrabold leading-[0.9] sm:text-8xl">
          Apex<span className="text-[var(--signal)]">AI</span>
        </h1>
        <p className="mt-4 max-w-xl text-lg text-[var(--carbon)]/80">
          Ask about any Grand Prix — the Oracle resolves the race, loads OpenF1 data if needed, then answers over race radio SSE.
        </p>
      </header>

      <main className="radio-enter border-y border-[var(--line)] py-8" style={{ animationDelay: '80ms' }}>
        <div className="mb-6">
          <p className="display text-sm uppercase tracking-[0.18em] text-[var(--carbon)]/60">Race Radio</p>
          <h2 className="display text-3xl font-bold">Ask the Oracle</h2>
        </div>

        <form
          ref={formRef}
          action={(formData) => {
            setLiveText('')
            setOptimisticAnswer('Connecting race radio…')
            return formAction(formData)
          }}
          className="flex flex-col gap-3 sm:flex-row"
        >
          <input
            name="question"
            placeholder="e.g. Who won Vegas 2025? Who won Barcelona 2026?"
            className="min-w-0 flex-1 border border-[var(--line)] bg-white/70 px-4 py-3 text-base outline-none ring-[var(--signal)] placeholder:text-[var(--carbon)]/40 focus:ring-2"
            disabled={pending}
          />
          <button
            type="submit"
            disabled={pending}
            className="display bg-[var(--signal)] px-6 py-3 text-lg font-bold uppercase tracking-wide text-white transition hover:brightness-110 disabled:opacity-60"
          >
            {pending ? 'On air…' : 'Send'}
          </button>
        </form>

        <div className="mt-4 flex flex-wrap gap-2">
          {PROMPTS.map((p) => (
            <button
              key={p.label}
              type="button"
              onClick={() => submitPrompt(p.text)}
              className="group cursor-pointer border border-[var(--line)] bg-white/50 px-3 py-2 text-left transition hover:border-[var(--signal)] hover:bg-[var(--signal)]/5 hover:shadow-[inset_3px_0_0_var(--signal)] active:scale-[0.98]"
            >
              <span className="display block text-[10px] font-medium uppercase tracking-[0.16em] text-[var(--carbon)]/50 transition group-hover:text-[var(--signal)]">
                {p.label}
              </span>
              <span className="mt-0.5 block text-sm text-[var(--carbon)] transition group-hover:text-[var(--ink)]">
                {p.text}
              </span>
            </button>
          ))}
        </div>

        <div className="mt-8 min-h-40 max-w-full overflow-x-hidden">
          {pending && (
            <div className="mb-3 h-0.5 w-full overflow-hidden bg-[var(--mist)]">
              <div className="live-bar h-full w-full bg-[var(--signal)]" />
            </div>
          )}
          {state.error && <p className="mb-3 text-[var(--signal)]">{state.error}</p>}
          <p className="max-w-full break-words whitespace-pre-wrap text-base leading-relaxed text-[var(--ink)] [overflow-wrap:anywhere]">
            {optimisticAnswer || 'Standing by for race radio…'}
          </p>
        </div>

        {briefLabel && (
          <p className="mt-4 text-sm text-[var(--carbon)]/60">
            Active session: <span className="text-[var(--ink)]">{briefLabel}</span>
          </p>
        )}

        {state.podium && <PodiumStand podium={state.podium} />}

        {state.speedTrace && (
          <SpeedTraceChart
            driverNumber={state.speedTrace.driverNumber}
            lapNumber={state.speedTrace.lapNumber}
            sessionKey={state.speedTrace.sessionKey || sessionKey}
          />
        )}
      </main>

      <aside className="radio-enter mt-8" style={{ animationDelay: '140ms' }}>
        <p className="display text-sm uppercase tracking-[0.18em] text-[var(--carbon)]/60">Session Brief</p>
        <p className="mt-1 text-sm text-[var(--carbon)]/55">
          {briefLabel
            ? `Live brief for ${briefLabel}.`
            : 'Default session brief — updates when you ask the Oracle about a race.'}
        </p>
        <pre className="mt-2 max-w-full overflow-x-hidden whitespace-pre-wrap break-words font-[inherit] text-sm leading-relaxed text-[var(--carbon)]/85 [overflow-wrap:anywhere]">
          {stats || 'Loading telemetry stats…'}
        </pre>
      </aside>
    </div>
  )
}
