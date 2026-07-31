import React, { useState } from 'react';
import Plot from 'react-plotly.js';
import { Download, Play, CheckCircle, Clock, AlertTriangle, Circle, RefreshCw, FileText } from 'lucide-react';
import WoWLineChart from '../components/charts/WoWLineChart';
import RawDataTab from './payment/RawDataTab';
import ContributionShiftTab from './payment/ContributionShiftTab';
import HistoricalAnalysisTab from './payment/HistoricalAnalysisTab';

type MainTab = 'pipeline' | 'raw' | 'contribution' | 'historical';
const MAIN_TABS: { id: MainTab; label: string }[] = [
  { id: 'pipeline', label: 'Pipeline' },
  { id: 'raw', label: 'Raw Data' },
  { id: 'contribution', label: 'Contribution Shift' },
  { id: 'historical', label: 'Historical Analysis' },
];

const API = '/api';

type StageStatus = 'idle' | 'running' | 'done' | 'error';

interface GenerateResult {
  n_rows: number;
  n_cells: number;
  weeks: number;
  current_window: { label: string };
  anomalies_injected: { stage: number; cell: string; description: string; reason: string; multiplier: number }[];
}

interface StageResult {
  stage: number;
  name: string;
  description: string;
  n_monitors?: number;
  n_active_cells?: number;
  n_alerts: number;
  n_new_cells?: number;
  threshold: number;
  execution_ms: number;
  chart_data?: unknown[];
  wow_chart_data?: unknown[];
  alerts: Record<string, unknown>[];
}

const STAGE_COLORS = ['#6366f1', '#f59e0b', '#10b981'];
const STAGE_DESCS = [
  'Roll up to (decline_code × channel). WoW Z-Score catches high-volume rate spikes.',
  'Per-cell contribution % vs baseline. Catches masking — small cells on fire, invisible at roll-up.',
  'Full granularity, no roll-up. WoW Z-Score catches low-volume and new-combination anomalies.',
];

export default function PaymentPipelinePage() {
  const [genResult, setGenResult] = useState<GenerateResult | null>(null);
  const [stageStatuses, setStageStatuses] = useState<StageStatus[]>(['idle', 'idle', 'idle']);
  const [stageResults, setStageResults] = useState<(StageResult | null)[]>([null, null, null]);
  const [threshold, setThreshold] = useState(3.0);
  const [generating, setGenerating] = useState(false);
  const [activeStage, setActiveStage] = useState<number>(0);
  const [mainTab, setMainTab] = useState<MainTab>('pipeline');

  const setStatus = (i: number, s: StageStatus) =>
    setStageStatuses(prev => prev.map((v, idx) => idx === i ? s : v));

  const setResult = (i: number, r: StageResult) =>
    setStageResults(prev => prev.map((v, idx) => idx === i ? r : v));

  const handleGenerate = async () => {
    setGenerating(true);
    setGenResult(null);
    setStageStatuses(['idle', 'idle', 'idle']);
    setStageResults([null, null, null]);
    try {
      const res = await fetch(`${API}/payment/generate`, { method: 'POST' });
      const data = await res.json();
      setGenResult(data);
    } finally {
      setGenerating(false);
    }
  };

  const handleRunPipeline = async () => {
    if (!genResult) return;
    setStageStatuses(['idle', 'idle', 'idle']);
    setStageResults([null, null, null]);

    for (let i = 0; i < 3; i++) {
      setStatus(i, 'running');
      setActiveStage(i);
      try {
        const res = await fetch(`${API}/payment/stage/${i + 1}?threshold=${threshold}`, { method: 'POST' });
        const data = await res.json();
        setResult(i, data);
        setStatus(i, 'done');
      } catch {
        setStatus(i, 'error');
      }
    }
  };

  const handleDownload = () => window.open(`${API}/payment/download`, '_blank');

  return (
    <div style={{ display: 'flex', height: '100%', overflow: 'hidden' }}>
      {/* Left panel */}
      <div style={{ width: 260, background: 'var(--surface)', borderRight: '1px solid var(--border)', display: 'flex', flexDirection: 'column', flexShrink: 0 }}>
        <div style={{ padding: '18px 18px 14px', borderBottom: '1px solid var(--border)' }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 12 }}>
            Payment Pipeline
          </div>

          <a href="/docs/payment_anomaly_brief.html" target="_blank" rel="noopener noreferrer"
            style={{
              display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'var(--text-muted)',
              textDecoration: 'none', marginBottom: 12, padding: '6px 0',
            }}
            onMouseEnter={e => (e.currentTarget.style.color = 'var(--primary-light)')}
            onMouseLeave={e => (e.currentTarget.style.color = 'var(--text-muted)')}
          >
            <FileText size={13} /> Executive Briefing
          </a>

          <button onClick={handleGenerate} disabled={generating} style={btnStyle('var(--primary)', generating)}>
            <RefreshCw size={13} style={{ animation: generating ? 'spin 1s linear infinite' : 'none' }} />
            {generating ? 'Generating…' : 'Generate Test Data'}
          </button>

          {genResult && (
            <>
              <button onClick={handleDownload} style={{ ...btnStyle('var(--surface2)'), color: 'var(--text-muted)', border: '1px solid var(--border)', marginTop: 8 }}>
                <Download size={13} /> Download CSV
              </button>

              <div style={{ marginTop: 14 }}>
                <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 6 }}>
                  WoW Threshold (σ)
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                  <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>Threshold</span>
                  <span style={{ fontSize: 11, fontWeight: 700, fontFamily: 'monospace' }}>{threshold}σ</span>
                </div>
                <input type="range" min={1} max={6} step={0.5} value={threshold}
                  onChange={e => setThreshold(Number(e.target.value))}
                  style={{ width: '100%', accentColor: 'var(--primary)' }} />
              </div>

              <button onClick={handleRunPipeline} style={{ ...btnStyle('#10b981'), marginTop: 14 }}>
                <Play size={13} /> Run Pipeline
              </button>
            </>
          )}
        </div>

        {/* Data summary */}
        {genResult && (
          <div style={{ padding: '14px 18px', borderBottom: '1px solid var(--border)' }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10 }}>Data Summary</div>
            {[
              ['Rows', genResult.n_rows.toLocaleString()],
              ['Active Cells', genResult.n_cells],
              ['Weeks of History', genResult.weeks],
              ['Current Window', genResult.current_window.label],
              ['Decline Codes', 40],
            ].map(([l, v]) => (
              <div key={l as string} style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 5, fontSize: 12 }}>
                <span style={{ color: 'var(--text-muted)' }}>{l}</span>
                <span style={{ fontWeight: 600 }}>{v}</span>
              </div>
            ))}
          </div>
        )}

        {/* Stage status */}
        <div style={{ padding: '14px 18px', flex: 1, overflowY: 'auto' }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10 }}>
            Pipeline Stages
          </div>
          {[0, 1, 2].map(i => {
            const s = stageStatuses[i];
            const r = stageResults[i];
            const color = STAGE_COLORS[i];
            return (
              <button key={i} onClick={() => r && setActiveStage(i)} style={{
                width: '100%', textAlign: 'left', padding: '10px 12px', borderRadius: 8, marginBottom: 8,
                background: activeStage === i && r ? color + '18' : 'var(--surface2)',
                border: `1px solid ${activeStage === i && r ? color : 'var(--border)'}`,
                color: 'var(--text)',
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                  <StageIcon status={s} color={color} />
                  <span style={{ fontSize: 12, fontWeight: 600, color }}>Stage {i + 1}</span>
                  {r && <span style={{ fontSize: 10, color: 'var(--text-muted)', marginLeft: 'auto' }}>{r.execution_ms}ms</span>}
                </div>
                <div style={{ fontSize: 11, color: 'var(--text-muted)', lineHeight: 1.4 }}>
                  {r ? `${r.n_alerts} alert${r.n_alerts !== 1 ? 's' : ''} · ${r.n_monitors ?? r.n_active_cells} monitors` : STAGE_DESCS[i].slice(0, 60) + '…'}
                </div>
              </button>
            );
          })}
        </div>

        {/* Injected anomalies legend */}
        {genResult && (
          <div style={{ padding: '14px 18px', borderTop: '1px solid var(--border)' }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 8 }}>
              Injected Anomalies
            </div>
            {genResult.anomalies_injected.map(a => (
              <div key={a.stage} style={{ marginBottom: 8, padding: '6px 8px', background: STAGE_COLORS[a.stage - 1] + '11', borderRadius: 6, borderLeft: `3px solid ${STAGE_COLORS[a.stage - 1]}` }}>
                <div style={{ fontSize: 11, fontWeight: 700, color: STAGE_COLORS[a.stage - 1] }}>Stage {a.stage} · ×{a.multiplier}</div>
                <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2, lineHeight: 1.4 }}>{a.description}</div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Main content */}
      <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column' }}>
        <div style={{ display: 'flex', gap: 4, padding: '12px 24px 0', borderBottom: '1px solid var(--border)', flexShrink: 0 }}>
          {MAIN_TABS.map(t => (
            <button key={t.id} onClick={() => setMainTab(t.id)} style={{
              padding: '8px 14px', fontSize: 12, fontWeight: 600, borderRadius: '8px 8px 0 0',
              background: mainTab === t.id ? 'var(--surface2)' : 'transparent',
              color: mainTab === t.id ? 'var(--primary-light)' : 'var(--text-muted)',
              borderBottom: mainTab === t.id ? '2px solid var(--primary)' : '2px solid transparent',
            }}>
              {t.label}
            </button>
          ))}
        </div>

        <div style={{ flex: 1, overflowY: 'auto', padding: 24, display: 'flex', flexDirection: 'column', gap: 20 }}>
          {mainTab === 'pipeline' && (
            !genResult ? (
              <EmptyState />
            ) : stageResults.every(r => r === null) ? (
              <ReadyState window={genResult.current_window.label} />
            ) : (
              [0, 1, 2].map(i => stageResults[i] && (
                <StagePanel key={i} result={stageResults[i]!} color={STAGE_COLORS[i]} index={i}
                  anomaly={genResult.anomalies_injected.find(a => a.stage === i + 1)}
                  active={activeStage === i} onClick={() => setActiveStage(i)} />
              ))
            )
          )}
          {mainTab === 'raw' && <RawDataTab />}
          {mainTab === 'contribution' && <ContributionShiftTab />}
          {mainTab === 'historical' && <HistoricalAnalysisTab />}
        </div>
      </div>
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}

function StageIcon({ status, color }: { status: StageStatus; color: string }) {
  if (status === 'done') return <CheckCircle size={14} color={color} />;
  if (status === 'running') return <RefreshCw size={14} color={color} style={{ animation: 'spin 1s linear infinite' }} />;
  if (status === 'error') return <AlertTriangle size={14} color="#ef4444" />;
  return <Circle size={14} color="var(--border)" />;
}

function StagePanel({ result, color, index, anomaly, active, onClick }: {
  result: StageResult; color: string; index: number;
  anomaly?: { stage: number; cell: string; description: string; reason: string };
  active: boolean; onClick: () => void;
}) {
  return (
    <div onClick={onClick} style={{
      background: 'var(--surface2)', border: `1px solid ${active ? color : 'var(--border)'}`,
      borderRadius: 12, overflow: 'hidden', cursor: 'pointer', transition: 'border-color 0.15s',
      flexShrink: 0,
    }}>
      {/* Stage header */}
      <div style={{ padding: '14px 18px', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', gap: 12 }}>
        <div style={{ width: 28, height: 28, borderRadius: 7, background: color + '22', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
          <CheckCircle size={14} color={color} />
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ fontWeight: 700, fontSize: 13, color }}>Stage {index + 1} — {result.name}</div>
          <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>{result.description}</div>
        </div>
        <div style={{ display: 'flex', gap: 16, flexShrink: 0 }}>
          <Stat label="Monitors" value={result.n_monitors ?? result.n_active_cells ?? 0} />
          <Stat label="Alerts" value={result.n_alerts} color={result.n_alerts > 0 ? '#ef4444' : undefined} />
          <Stat label="Time" value={`${result.execution_ms}ms`} />
        </div>
      </div>

      {/* Anomaly callout */}
      {anomaly && (
        <div style={{ padding: '10px 18px', background: color + '0a', borderBottom: '1px solid var(--border)' }}>
          <div style={{ fontSize: 11, fontWeight: 700, color, marginBottom: 3 }}>INJECTED ANOMALY THIS STAGE CATCHES</div>
          <div style={{ fontSize: 12, color: 'var(--text-muted)', lineHeight: 1.5 }}>
            <strong style={{ color: 'var(--text)' }}>{anomaly.description}</strong><br />
            {anomaly.reason}
          </div>
        </div>
      )}

      {/* Charts + table */}
      <div style={{ padding: 18 }}>
        {index === 0 && result.chart_data && <Stage1Chart data={result.chart_data as never} />}
        {index === 1 && result.chart_data && <Stage2Chart data={result.chart_data as never} />}
        {index === 2 && result.wow_chart_data && result.wow_chart_data.length > 0 && (
          <div style={{ marginBottom: 16 }}>
            <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 8 }}>Week-over-Week Rate — Alert Cells (Same Mon 14:00, Weeks −4 to Current)</div>
            <WoWLineChart data={result.wow_chart_data as never} height={300} />
          </div>
        )}
        {result.alerts.length > 0 && <AlertTable alerts={result.alerts} index={index} color={color} />}
      </div>
    </div>
  );
}

function Stage1Chart({ data }: { data: { label: string; z_score: number; alerted: boolean }[] }) {
  return (
    <div style={{ marginBottom: 16 }}>
      <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 8 }}>WoW Z-Score — All Roll-up Monitors (decline_code × channel)</div>
      <Plot
        data={[{
          type: 'bar',
          x: data.map(d => d.label),
          y: data.map(d => d.z_score > 50 ? 50 : d.z_score),
          marker: { color: data.map(d => d.alerted ? '#ef4444' : '#6366f133') },
          text: data.map(d => d.z_score > 10 ? `z=${d.z_score.toFixed(1)}` : ''),
          textposition: 'outside',
        }]}
        layout={{
          paper_bgcolor: 'transparent', plot_bgcolor: 'transparent',
          font: { color: '#94a3b8', size: 10 },
          xaxis: { color: '#94a3b8', gridcolor: '#2e3347', tickangle: -35 },
          yaxis: { color: '#94a3b8', gridcolor: '#2e3347', title: 'Z-Score (capped 50)' },
          margin: { t: 10, r: 10, b: 90, l: 55 }, height: 240,
          shapes: [{ type: 'line', x0: -0.5, x1: data.length - 0.5, y0: 3, y1: 3, line: { color: '#f59e0b', width: 1, dash: 'dot' } }],
        }}
        config={{ displayModeBar: false, responsive: true }}
        style={{ width: '100%' }}
      />
    </div>
  );
}

function Stage2Chart({ data }: { data: { cell_short: string; contribution_pct: number; hist_mean_pct: number; alerted: boolean }[] }) {
  return (
    <div style={{ marginBottom: 16 }}>
      <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 8 }}>Contribution % — Current vs Baseline (Top 20 cells)</div>
      <Plot
        data={[
          {
            type: 'bar', name: 'Baseline (WoW avg)',
            x: data.map(d => d.cell_short), y: data.map(d => d.hist_mean_pct),
            marker: { color: '#3b82f655' },
          },
          {
            type: 'bar', name: 'Current',
            x: data.map(d => d.cell_short), y: data.map(d => d.contribution_pct),
            marker: { color: data.map(d => d.alerted ? '#ef4444' : '#6366f1') },
          },
        ]}
        layout={{
          barmode: 'group',
          paper_bgcolor: 'transparent', plot_bgcolor: 'transparent',
          font: { color: '#94a3b8', size: 10 },
          xaxis: { color: '#94a3b8', gridcolor: '#2e3347', tickangle: -35 },
          yaxis: { color: '#94a3b8', gridcolor: '#2e3347', title: 'Contribution %', ticksuffix: '%' },
          legend: { font: { color: '#94a3b8', size: 10 }, bgcolor: 'transparent' },
          margin: { t: 10, r: 10, b: 90, l: 60 }, height: 260,
        }}
        config={{ displayModeBar: false, responsive: true }}
        style={{ width: '100%' }}
      />
    </div>
  );
}

function AlertTable({ alerts, index, color }: { alerts: Record<string, unknown>[]; index: number; color: string }) {
  const cols = index === 0
    ? ['decline_code', 'channel', 'total', 'declines', 'rate', 'wow_mean', 'z_score']
    : index === 1
    ? ['network', 'geography', 'decline_code', 'decline_count', 'contribution_pct', 'hist_mean', 'z_contribution']
    : ['network', 'geography', 'entry_mode', 'decline_code', 'decline_rate', 'wow_mean', 'z_wow', 'n_weeks'];

  const fmtVal = (k: string, v: unknown) => {
    if (v === null || v === undefined) return '—';
    if (typeof v === 'boolean') return v ? '✓ new' : '';
    const numericPct = ['rate', 'wow_mean', 'decline_rate', 'contribution_pct', 'hist_mean'];
    if (numericPct.includes(k) && typeof v === 'number') return (v * (k.includes('contribution') ? 100 : 100)).toFixed(2) + '%';
    if (typeof v === 'number') return Math.abs(v) > 10 ? v.toFixed(1) : v.toFixed(3);
    return String(v);
  };

  return (
    <div style={{ overflowX: 'auto' }}>
      <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 8 }}>
        Alert Details — {alerts.length} flagged
      </div>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 11 }}>
        <thead>
          <tr>
            {cols.map(c => (
              <th key={c} style={{ padding: '7px 10px', textAlign: 'left', borderBottom: `2px solid ${color}44`, color, fontWeight: 700, whiteSpace: 'nowrap', textTransform: 'uppercase', fontSize: 10 }}>
                {c.replace(/_/g, ' ')}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {alerts.map((row, i) => (
            <tr key={i} style={{ background: i % 2 === 0 ? 'transparent' : 'rgba(255,255,255,0.02)' }}>
              {cols.map(c => {
                const v = row[c];
                const isZ = c.startsWith('z_');
                const zVal = typeof v === 'number' ? Math.abs(v) : 0;
                return (
                  <td key={c} style={{
                    padding: '7px 10px', borderBottom: '1px solid var(--border)',
                    color: isZ && zVal > 10 ? '#ef4444' : isZ && zVal > 3 ? '#f59e0b' : 'var(--text)',
                    fontWeight: isZ ? 700 : 400, whiteSpace: 'nowrap', fontFamily: isZ ? 'monospace' : 'inherit',
                  }}>
                    {fmtVal(c, v)}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Stat({ label, value, color }: { label: string; value: string | number; color?: string }) {
  return (
    <div style={{ textAlign: 'center' }}>
      <div style={{ fontSize: 18, fontWeight: 700, color: color || 'var(--text)' }}>{value}</div>
      <div style={{ fontSize: 10, color: 'var(--text-muted)', textTransform: 'uppercase' }}>{label}</div>
    </div>
  );
}

function EmptyState() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', gap: 12, color: 'var(--text-muted)' }}>
      <Play size={48} strokeWidth={1} style={{ opacity: 0.3 }} />
      <div style={{ fontSize: 18, fontWeight: 600, color: 'var(--text)' }}>Payment Decline Pipeline</div>
      <div style={{ fontSize: 13, textAlign: 'center', maxWidth: 400, lineHeight: 1.6 }}>
        Click <strong>Generate Test Data</strong> to create 5 weeks of synthetic payment decline data
        with 3 injected anomalies — one per stage.
      </div>
    </div>
  );
}

function ReadyState({ window: win }: { window: string }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', gap: 12, color: 'var(--text-muted)' }}>
      <CheckCircle size={48} strokeWidth={1} color="var(--green)" style={{ opacity: 0.6 }} />
      <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--text)' }}>Data Ready — Current Window: {win}</div>
      <div style={{ fontSize: 13, textAlign: 'center', maxWidth: 440, lineHeight: 1.6 }}>
        Click <strong>Run Pipeline</strong> to execute all 3 stages sequentially.
        Watch each stage find its anomaly in real time.
      </div>
    </div>
  );
}

function btnStyle(bg: string, disabled?: boolean): React.CSSProperties {
  return {
    width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
    padding: '9px 14px', borderRadius: 7, fontWeight: 600, fontSize: 12,
    background: disabled ? 'var(--border)' : bg, color: 'white', border: 'none', cursor: disabled ? 'not-allowed' : 'pointer',
  };
}
