import React from 'react';
import { AnalysisParams, DatasetInfo, MethodKey } from '../types';

interface Props {
  params: AnalysisParams;
  datasets: DatasetInfo[];
  onChange: (p: AnalysisParams) => void;
}

function Slider({ label, value, min, max, step, onChange, tooltip }: {
  label: string; value: number; min: number; max: number; step: number;
  onChange: (v: number) => void; tooltip?: string;
}) {
  return (
    <div style={{ marginBottom: 14 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
        <span style={{ fontSize: 12, color: 'var(--text-muted)' }} title={tooltip}>{label}</span>
        <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--text)', fontFamily: 'monospace' }}>{value}</span>
      </div>
      <input type="range" min={min} max={max} step={step} value={value}
        onChange={e => onChange(Number(e.target.value))}
        style={{ width: '100%', accentColor: 'var(--primary)' }} />
    </div>
  );
}

const METHOD_KEYS: { key: MethodKey; label: string; color: string }[] = [
  { key: 'zscore', label: 'Z-Score', color: '#6366f1' },
  { key: 'isolation_forest', label: 'Isolation Forest', color: '#f59e0b' },
  { key: 'lof', label: 'Local Outlier Factor', color: '#10b981' },
];

export default function ParameterPanel({ params, datasets, onChange }: Props) {
  const set = (patch: Partial<AnalysisParams>) => onChange({ ...params, ...patch });

  const toggleMethod = (key: MethodKey) => {
    const current = params.methods;
    const next = current.includes(key) ? current.filter(k => k !== key) : [...current, key];
    if (next.length > 0) set({ methods: next });
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
      {/* Dataset */}
      <Section title="Dataset">
        <div style={{ marginBottom: 12 }}>
          <label style={labelStyle}>Dataset</label>
          <select value={params.dataset} onChange={e => set({ dataset: e.target.value })} style={selectStyle}>
            {datasets.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
        </div>
        <Slider label="Sample Size" value={params.n_samples} min={100} max={2000} step={100}
          onChange={v => set({ n_samples: v })} />
      </Section>

      {/* Methods */}
      <Section title="Methods">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {METHOD_KEYS.map(({ key, label, color }) => (
            <button key={key} onClick={() => toggleMethod(key)} style={{
              display: 'flex', alignItems: 'center', gap: 10, padding: '8px 12px',
              borderRadius: 7, border: `1px solid ${params.methods.includes(key) ? color : 'var(--border)'}`,
              background: params.methods.includes(key) ? color + '18' : 'transparent',
              color: params.methods.includes(key) ? color : 'var(--text-muted)',
              fontWeight: params.methods.includes(key) ? 600 : 400, textAlign: 'left', fontSize: 12,
            }}>
              <div style={{
                width: 10, height: 10, borderRadius: '50%', flexShrink: 0,
                background: params.methods.includes(key) ? color : 'var(--border)',
              }} />
              {label}
            </button>
          ))}
        </div>
      </Section>

      {/* Z-Score */}
      {params.methods.includes('zscore') && (
        <Section title="Z-Score Parameters" accent="#6366f1">
          <Slider label="Threshold (σ)" value={params.zscore_params.threshold} min={1} max={6} step={0.1}
            tooltip="Points with max |z-score| > threshold are flagged as anomalies"
            onChange={v => set({ zscore_params: { threshold: v } })} />
        </Section>
      )}

      {/* Isolation Forest */}
      {params.methods.includes('isolation_forest') && (
        <Section title="Isolation Forest Parameters" accent="#f59e0b">
          <Slider label="Contamination" value={params.if_params.contamination} min={0.01} max={0.4} step={0.01}
            tooltip="Expected proportion of anomalies in the dataset"
            onChange={v => set({ if_params: { ...params.if_params, contamination: v } })} />
          <Slider label="N Estimators (trees)" value={params.if_params.n_estimators} min={10} max={300} step={10}
            tooltip="More trees = more stable results but slower"
            onChange={v => set({ if_params: { ...params.if_params, n_estimators: v } })} />
        </Section>
      )}

      {/* LOF */}
      {params.methods.includes('lof') && (
        <Section title="LOF Parameters" accent="#10b981">
          <Slider label="N Neighbors" value={params.lof_params.n_neighbors} min={5} max={50} step={1}
            tooltip="Number of neighbors used for density estimation"
            onChange={v => set({ lof_params: { ...params.lof_params, n_neighbors: v } })} />
          <Slider label="Contamination" value={params.lof_params.contamination} min={0.01} max={0.4} step={0.01}
            onChange={v => set({ lof_params: { ...params.lof_params, contamination: v } })} />
        </Section>
      )}
    </div>
  );
}

function Section({ title, children, accent }: { title: string; children: React.ReactNode; accent?: string }) {
  return (
    <div style={{
      padding: '16px 18px',
      borderBottom: '1px solid var(--border)',
    }}>
      <div style={{
        fontSize: 11, fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em',
        color: accent || 'var(--text-muted)', marginBottom: 12,
      }}>{title}</div>
      {children}
    </div>
  );
}

const labelStyle: React.CSSProperties = {
  display: 'block', fontSize: 12, color: 'var(--text-muted)', marginBottom: 5,
};

const selectStyle: React.CSSProperties = {
  width: '100%', padding: '7px 10px', borderRadius: 6,
  background: 'var(--surface2)', border: '1px solid var(--border)',
  color: 'var(--text)', fontSize: 12, outline: 'none',
};
