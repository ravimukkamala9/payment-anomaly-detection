import React, { useEffect, useMemo, useState } from 'react';
import { RefreshCw, Search } from 'lucide-react';

const API = '/api';
const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

interface RawRow {
  week: number; dayOfWeek: number; hour: number;
  network: string; geography: string; entryMode: string; purchaseType: string;
  authType: string; channel: string; declineCode: string;
  totalCount: number; declineCount: number; declineRate: number;
}

interface ScoredRow {
  week: number; day_of_week: number; hour: number;
  network: string; geography: string; entry_mode: string; purchase_type: string;
  auth_type: string; channel: string; decline_code: string;
  total_count: number; decline_count: number; decline_rate: number;
  anomaly_score: number; is_anomaly: boolean;
}

const FILTER_COLS: { key: keyof RawRow; label: string }[] = [
  { key: 'network', label: 'Network' },
  { key: 'geography', label: 'Geography' },
  { key: 'entryMode', label: 'Entry Mode' },
  { key: 'purchaseType', label: 'Purchase Type' },
  { key: 'authType', label: 'Auth Type' },
  { key: 'channel', label: 'Channel' },
  { key: 'declineCode', label: 'Decline Code' },
];

export default function RawDataTab() {
  const [rows, setRows] = useState<RawRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [week, setWeek] = useState<string>('0');
  const [day, setDay] = useState<string>('0');
  const [hour, setHour] = useState<string>('14');
  const [page, setPage] = useState(0);
  const pageSize = 50;

  const [method, setMethod] = useState<'zscore' | 'isolation_forest'>('zscore');
  const [threshold, setThreshold] = useState(3.0);
  const [contamination, setContamination] = useState(0.1);
  const [scoring, setScoring] = useState(false);
  const [scored, setScored] = useState<ScoredRow[] | null>(null);
  const [scoredMeta, setScoredMeta] = useState<{ n_rows: number; n_anomalies: number } | null>(null);

  const loadRaw = async () => {
    setLoading(true);
    try {
      const res = await fetch(`${API}/payment/raw`);
      const data = await res.json();
      setRows(data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadRaw(); }, []);

  const options = useMemo(() => {
    const out: Record<string, Set<string>> = {};
    for (const c of FILTER_COLS) out[c.key] = new Set();
    for (const r of rows) for (const c of FILTER_COLS) out[c.key].add(String(r[c.key]));
    return out;
  }, [rows]);

  const filtered = useMemo(() => {
    return rows.filter(r => {
      if (week !== '' && r.week !== Number(week)) return false;
      if (day !== '' && r.dayOfWeek !== Number(day)) return false;
      if (hour !== '' && r.hour !== Number(hour)) return false;
      for (const c of FILTER_COLS) {
        const v = filters[c.key];
        if (v && String(r[c.key]) !== v) return false;
      }
      return true;
    });
  }, [rows, filters, week, day, hour]);

  const pageRows = filtered.slice(page * pageSize, (page + 1) * pageSize);

  const runDetector = async () => {
    setScoring(true);
    setScored(null);
    try {
      const body: Record<string, unknown> = {
        method, threshold, contamination,
        week: week === '' ? null : Number(week),
        dayOfWeek: day === '' ? null : Number(day),
        hour: hour === '' ? null : Number(hour),
      };
      for (const c of FILTER_COLS) {
        const v = filters[c.key];
        if (v) body[c.key] = v;
      }
      const res = await fetch(`${API}/payment/raw/detect`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
      });
      if (!res.ok) { const err = await res.json(); alert(err.detail || 'Detection failed'); return; }
      const data = await res.json();
      setScored(data.rows);
      setScoredMeta({ n_rows: data.n_rows, n_anomalies: data.n_anomalies });
    } finally {
      setScoring(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>
          {loading ? 'Loading raw dataset…' : `${rows.length.toLocaleString()} rows loaded — ${filtered.length.toLocaleString()} match current filters`}
        </div>
        <button onClick={loadRaw} disabled={loading} style={smallBtnStyle()}>
          <RefreshCw size={12} style={{ animation: loading ? 'spin 1s linear infinite' : 'none' }} /> Reload
        </button>
      </div>

      {/* Filters */}
      <div style={{ background: 'var(--surface2)', border: '1px solid var(--border)', borderRadius: 10, padding: 14 }}>
        <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10 }}>Filters</div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))', gap: 10 }}>
          <Select label="Week" value={week} onChange={v => { setWeek(v); setPage(0); }}
            options={['', '0', '1', '2', '3', '4']} display={v => v === '' ? 'Any' : v === '0' ? 'Current (0)' : `−${v}`} />
          <Select label="Day" value={day} onChange={v => { setDay(v); setPage(0); }}
            options={['', '0', '1', '2', '3', '4', '5', '6']} display={v => v === '' ? 'Any' : DAY_NAMES[Number(v)]} />
          <Select label="Hour" value={hour} onChange={v => { setHour(v); setPage(0); }}
            options={['', ...Array.from({ length: 24 }, (_, i) => String(i))]} display={v => v === '' ? 'Any' : `${v}:00`} />
          {FILTER_COLS.map(c => (
            <Select key={c.key} label={c.label} value={filters[c.key] ?? ''}
              onChange={v => { setFilters(prev => ({ ...prev, [c.key]: v })); setPage(0); }}
              options={['', ...Array.from(options[c.key] ?? []).sort()]}
              display={v => v === '' ? 'Any' : v} />
          ))}
        </div>
      </div>

      {/* Anomaly detector */}
      <div style={{ background: 'var(--surface2)', border: '1px solid var(--border)', borderRadius: 10, padding: 14 }}>
        <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10 }}>
          Run detector on filtered slice
        </div>
        <div style={{ display: 'flex', gap: 12, alignItems: 'flex-end', flexWrap: 'wrap' }}>
          <Select label="Method" value={method} onChange={v => setMethod(v as 'zscore' | 'isolation_forest')}
            options={['zscore', 'isolation_forest']} display={v => v === 'zscore' ? 'Z-Score' : 'Isolation Forest'} />
          {method === 'zscore' ? (
            <NumberField label="Threshold (σ)" value={threshold} onChange={setThreshold} step={0.5} min={0.5} max={10} />
          ) : (
            <NumberField label="Contamination" value={contamination} onChange={setContamination} step={0.01} min={0.01} max={0.5} />
          )}
          <button onClick={runDetector} disabled={scoring} style={{ ...smallBtnStyle('var(--primary)'), padding: '8px 16px' }}>
            <Search size={13} /> {scoring ? 'Scoring…' : 'Flag Anomalies'}
          </button>
          {scoredMeta && (
            <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
              {scoredMeta.n_anomalies} of {scoredMeta.n_rows} rows flagged
            </div>
          )}
        </div>
      </div>

      {/* Scored results (if run) else plain filtered browse table */}
      {scored ? (
        <ScoredTable rows={scored} />
      ) : (
        <BrowseTable rows={pageRows} page={page} pageSize={pageSize} total={filtered.length} onPage={setPage} />
      )}
    </div>
  );
}

function BrowseTable({ rows, page, pageSize, total, onPage }: { rows: RawRow[]; page: number; pageSize: number; total: number; onPage: (p: number) => void }) {
  const cols: { key: keyof RawRow; label: string }[] = [
    { key: 'week', label: 'Week' }, { key: 'dayOfWeek', label: 'Day' }, { key: 'hour', label: 'Hour' },
    { key: 'network', label: 'Network' }, { key: 'geography', label: 'Geography' }, { key: 'entryMode', label: 'Entry' },
    { key: 'purchaseType', label: 'Purchase' }, { key: 'authType', label: 'Auth' }, { key: 'channel', label: 'Channel' },
    { key: 'declineCode', label: 'Decline Code' }, { key: 'totalCount', label: 'Total' }, { key: 'declineCount', label: 'Declines' },
    { key: 'declineRate', label: 'Rate' },
  ];
  const maxPage = Math.max(0, Math.ceil(total / pageSize) - 1);
  return (
    <div>
      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 11 }}>
          <thead>
            <tr>
              {cols.map(c => (
                <th key={c.key} style={{ padding: '7px 10px', textAlign: 'left', borderBottom: '2px solid var(--primary)44', color: 'var(--primary-light)', fontWeight: 700, whiteSpace: 'nowrap', textTransform: 'uppercase', fontSize: 10 }}>
                  {c.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((r, i) => (
              <tr key={i} style={{ background: i % 2 === 0 ? 'transparent' : 'rgba(255,255,255,0.02)' }}>
                {cols.map(c => (
                  <td key={c.key} style={{ padding: '7px 10px', borderBottom: '1px solid var(--border)', whiteSpace: 'nowrap' }}>
                    {c.key === 'dayOfWeek' ? DAY_NAMES[r.dayOfWeek] : c.key === 'declineRate' ? (r.declineRate * 100).toFixed(2) + '%' : String(r[c.key])}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 10, fontSize: 12, color: 'var(--text-muted)' }}>
        <span>Page {page + 1} of {maxPage + 1} — {total.toLocaleString()} rows</span>
        <div style={{ display: 'flex', gap: 6 }}>
          <button onClick={() => onPage(Math.max(0, page - 1))} disabled={page === 0} style={smallBtnStyle()}>Prev</button>
          <button onClick={() => onPage(Math.min(maxPage, page + 1))} disabled={page >= maxPage} style={smallBtnStyle()}>Next</button>
        </div>
      </div>
    </div>
  );
}

function ScoredTable({ rows }: { rows: ScoredRow[] }) {
  const cols: { key: keyof ScoredRow; label: string }[] = [
    { key: 'network', label: 'Network' }, { key: 'geography', label: 'Geography' }, { key: 'entry_mode', label: 'Entry' },
    { key: 'decline_code', label: 'Decline Code' }, { key: 'total_count', label: 'Total' }, { key: 'decline_count', label: 'Declines' },
    { key: 'decline_rate', label: 'Rate' }, { key: 'anomaly_score', label: 'Score' }, { key: 'is_anomaly', label: 'Flagged' },
  ];
  return (
    <div style={{ overflowX: 'auto' }}>
      <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 8 }}>Detector Results — sorted by anomaly score</div>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 11 }}>
        <thead>
          <tr>
            {cols.map(c => (
              <th key={c.key} style={{ padding: '7px 10px', textAlign: 'left', borderBottom: '2px solid #ef444444', color: '#ef4444', fontWeight: 700, whiteSpace: 'nowrap', textTransform: 'uppercase', fontSize: 10 }}>
                {c.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i} style={{ background: r.is_anomaly ? 'rgba(239,68,68,0.08)' : i % 2 === 0 ? 'transparent' : 'rgba(255,255,255,0.02)' }}>
              {cols.map(c => (
                <td key={c.key} style={{ padding: '7px 10px', borderBottom: '1px solid var(--border)', whiteSpace: 'nowrap', color: c.key === 'is_anomaly' && r.is_anomaly ? '#ef4444' : 'var(--text)', fontWeight: c.key === 'is_anomaly' && r.is_anomaly ? 700 : 400 }}>
                  {c.key === 'decline_rate' ? (r.decline_rate * 100).toFixed(2) + '%'
                    : c.key === 'anomaly_score' ? r.anomaly_score.toFixed(3)
                    : c.key === 'is_anomaly' ? (r.is_anomaly ? 'Yes' : 'No')
                    : String(r[c.key])}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Select({ label, value, onChange, options, display }: { label: string; value: string; onChange: (v: string) => void; options: string[]; display: (v: string) => string }) {
  return (
    <div>
      <div style={{ fontSize: 10, color: 'var(--text-muted)', marginBottom: 3, textTransform: 'uppercase' }}>{label}</div>
      <select value={value} onChange={e => onChange(e.target.value)}
        style={{ width: '100%', background: 'var(--surface)', color: 'var(--text)', border: '1px solid var(--border)', borderRadius: 6, padding: '6px 8px', fontSize: 12 }}>
        {options.map(o => <option key={o} value={o}>{display(o)}</option>)}
      </select>
    </div>
  );
}

function NumberField({ label, value, onChange, step, min, max }: { label: string; value: number; onChange: (v: number) => void; step: number; min: number; max: number }) {
  return (
    <div>
      <div style={{ fontSize: 10, color: 'var(--text-muted)', marginBottom: 3, textTransform: 'uppercase' }}>{label}</div>
      <input type="number" value={value} step={step} min={min} max={max} onChange={e => onChange(Number(e.target.value))}
        style={{ width: 90, background: 'var(--surface)', color: 'var(--text)', border: '1px solid var(--border)', borderRadius: 6, padding: '6px 8px', fontSize: 12 }} />
    </div>
  );
}

function smallBtnStyle(bg?: string): React.CSSProperties {
  return {
    display: 'flex', alignItems: 'center', gap: 6, padding: '6px 10px', borderRadius: 6, fontSize: 11, fontWeight: 600,
    background: bg || 'var(--surface2)', color: bg ? 'white' : 'var(--text-muted)', border: bg ? 'none' : '1px solid var(--border)',
  };
}
