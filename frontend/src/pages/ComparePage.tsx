import React from 'react';
import { AnalysisResponse, MethodKey } from '../types';
import { METHOD_COLORS, METHOD_LABELS } from '../utils/colors';
import ComparisonRadar from '../components/charts/ComparisonRadar';
import MetricsBarChart from '../components/charts/MetricsBarChart';
import ScatterPlot from '../components/charts/ScatterPlot';
import { CheckCircle, XCircle } from 'lucide-react';

interface Props { response: AnalysisResponse | null; }

export default function ComparePage({ response }: Props) {
  if (!response) {
    return <Placeholder />;
  }

  const keys = Object.keys(response.results) as MethodKey[];
  const best = findBestMethod(response, keys);

  return (
    <div style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 20, overflowY: 'auto', height: '100%' }}>
      {/* Side-by-side scatter */}
      <div style={{ background: 'var(--surface2)', border: '1px solid var(--border)', borderRadius: 'var(--radius)', padding: 16 }}>
        <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 12 }}>Anomaly Detection — Side by Side</h3>
        <div style={{ display: 'grid', gridTemplateColumns: `repeat(${keys.length}, 1fr)`, gap: 12 }}>
          {keys.map(key => (
            <div key={key} style={{ borderLeft: `3px solid ${METHOD_COLORS[key]}`, paddingLeft: 8 }}>
              <ScatterPlot response={response} method={key} height={280} />
            </div>
          ))}
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        {/* Radar */}
        <div style={{ background: 'var(--surface2)', border: '1px solid var(--border)', borderRadius: 'var(--radius)', padding: 16 }}>
          <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>Multi-Dimension Comparison</h3>
          <p style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 8 }}>Higher values = better</p>
          <ComparisonRadar response={response} height={320} />
        </div>
        {/* Bar chart */}
        <div style={{ background: 'var(--surface2)', border: '1px solid var(--border)', borderRadius: 'var(--radius)', padding: 16 }}>
          <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>Performance Metrics</h3>
          <p style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 8 }}>Labeled data evaluation</p>
          <MetricsBarChart response={response} height={280} />
        </div>
      </div>

      {/* Detailed comparison table */}
      <div style={{ background: 'var(--surface2)', border: '1px solid var(--border)', borderRadius: 'var(--radius)', padding: 20 }}>
        <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 16 }}>Detailed Metrics Comparison</h3>
        <ComparisonTable response={response} keys={keys} best={best} />
      </div>

      {/* Scalability */}
      <div style={{ background: 'var(--surface2)', border: '1px solid var(--border)', borderRadius: 'var(--radius)', padding: 20 }}>
        <h3 style={{ fontSize: 14, fontWeight: 600, marginBottom: 16 }}>Scalability Analysis</h3>
        <ScalabilityTable response={response} keys={keys} />
      </div>

      {/* Recommendation */}
      <RecommendationCard response={response} keys={keys} best={best} />
    </div>
  );
}

function findBestMethod(response: AnalysisResponse, keys: MethodKey[]): MethodKey {
  let bestKey = keys[0];
  let bestF1 = -1;
  for (const key of keys) {
    const f1 = response.results[key]?.metrics.f1_score ?? 0;
    if (f1 > bestF1) { bestF1 = f1; bestKey = key; }
  }
  return bestKey;
}

function ComparisonTable({ response, keys, best }: { response: AnalysisResponse; keys: MethodKey[]; best: MethodKey }) {
  const rows = [
    { label: 'Anomalies Found', fn: (k: MethodKey) => response.results[k]?.metrics.n_anomalies ?? 0, fmt: String },
    { label: 'Anomaly Rate', fn: (k: MethodKey) => response.results[k]?.metrics.anomaly_rate ?? 0, fmt: (v: number) => v.toFixed(1) + '%' },
    { label: 'Accuracy', fn: (k: MethodKey) => response.results[k]?.metrics.accuracy, fmt: (v: number | null) => v !== null ? (v * 100).toFixed(1) + '%' : '—' },
    { label: 'Precision', fn: (k: MethodKey) => response.results[k]?.metrics.precision, fmt: (v: number | null) => v !== null ? (v * 100).toFixed(1) + '%' : '—' },
    { label: 'Recall', fn: (k: MethodKey) => response.results[k]?.metrics.recall, fmt: (v: number | null) => v !== null ? (v * 100).toFixed(1) + '%' : '—' },
    { label: 'F1 Score', fn: (k: MethodKey) => response.results[k]?.metrics.f1_score, fmt: (v: number | null) => v !== null ? (v * 100).toFixed(1) + '%' : '—' },
    { label: 'False Positive Rate', fn: (k: MethodKey) => response.results[k]?.metrics.false_positive_rate, fmt: (v: number | null) => v !== null ? (v * 100).toFixed(1) + '%' : '—' },
    { label: 'Execution Time', fn: (k: MethodKey) => response.results[k]?.metrics.execution_time_ms ?? 0, fmt: (v: number) => v.toFixed(1) + ' ms' },
  ];

  return (
    <div style={{ overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
        <thead>
          <tr>
            <th style={thStyle}>Metric</th>
            {keys.map(k => (
              <th key={k} style={{ ...thStyle, color: METHOD_COLORS[k] }}>
                {METHOD_LABELS[k]}
                {k === best && <span style={{ marginLeft: 6, fontSize: 10, background: METHOD_COLORS[k] + '22', padding: '1px 6px', borderRadius: 4 }}>BEST</span>}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={i} style={{ background: i % 2 === 0 ? 'transparent' : 'rgba(255,255,255,0.02)' }}>
              <td style={tdStyle}>{row.label}</td>
              {keys.map(k => <td key={k} style={{ ...tdStyle, textAlign: 'center' }}>{row.fmt(row.fn(k) as never)}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ScalabilityTable({ response, keys }: { response: AnalysisResponse; keys: MethodKey[] }) {
  const rows = [
    { label: 'Time Complexity', fn: (k: MethodKey) => response.scalability[k]?.time_complexity ?? '—' },
    { label: 'Space Complexity', fn: (k: MethodKey) => response.scalability[k]?.space_complexity ?? '—' },
    { label: 'Scales To', fn: (k: MethodKey) => response.scalability[k]?.scales_to ?? '—' },
    { label: 'Parallelizable', fn: (k: MethodKey) => response.scalability[k]?.parallelizable },
  ];
  return (
    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
      <thead>
        <tr>
          <th style={thStyle}>Property</th>
          {keys.map(k => <th key={k} style={{ ...thStyle, color: METHOD_COLORS[k] }}>{METHOD_LABELS[k]}</th>)}
        </tr>
      </thead>
      <tbody>
        {rows.map((row, i) => (
          <tr key={i} style={{ background: i % 2 === 0 ? 'transparent' : 'rgba(255,255,255,0.02)' }}>
            <td style={tdStyle}>{row.label}</td>
            {keys.map(k => {
              const val = row.fn(k);
              return (
                <td key={k} style={{ ...tdStyle, textAlign: 'center' }}>
                  {typeof val === 'boolean'
                    ? (val ? <CheckCircle size={14} color="var(--green)" /> : <XCircle size={14} color="var(--red)" />)
                    : String(val)}
                </td>
              );
            })}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

const TRADE_OFFS: Record<string, { pros: string[]; cons: string[]; bestFor: string }> = {
  zscore: {
    pros: ['O(n·d) linear complexity', 'Fully interpretable (threshold in σ units)', 'No training phase', 'Works well on Gaussian data'],
    cons: ['Assumes Gaussian distribution', 'Sensitive to multivariate correlations', 'Global threshold may miss local patterns'],
    bestFor: 'Time-series monitoring, numeric KPIs, quick screening',
  },
  isolation_forest: {
    pros: ['Handles high-dimensional data well', 'No distribution assumptions', 'Sub-linear with subsampling', 'Robust to noise'],
    cons: ['Less interpretable (ensemble of trees)', 'Hyperparameter sensitivity (contamination)', 'Struggles with dense cluster anomalies'],
    bestFor: 'General-purpose outlier detection, tabular data, production ML systems',
  },
  lof: {
    pros: ['Captures local density context', 'Finds anomalies in clustered data', 'No global threshold needed'],
    cons: ['O(n²) complexity — slow at scale', 'Sensitive to k (n_neighbors)', 'Not suitable for streaming data'],
    bestFor: 'Datasets with varying density clusters, spatial/geographic anomalies',
  },
};

function RecommendationCard({ response, keys, best }: { response: AnalysisResponse; keys: MethodKey[]; best: MethodKey }) {
  return (
    <div style={{ background: 'var(--surface2)', border: `1px solid ${METHOD_COLORS[best]}44`, borderRadius: 'var(--radius)', padding: 20 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
        <div style={{ width: 8, height: 8, borderRadius: '50%', background: METHOD_COLORS[best] }} />
        <h3 style={{ fontSize: 14, fontWeight: 700 }}>
          Recommendation: <span style={{ color: METHOD_COLORS[best] }}>{METHOD_LABELS[best]}</span>
        </h3>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: `repeat(${keys.length}, 1fr)`, gap: 16 }}>
        {keys.map(key => {
          const info = TRADE_OFFS[key];
          const isBest = key === best;
          return (
            <div key={key} style={{
              padding: 14, borderRadius: 8,
              border: `1px solid ${isBest ? METHOD_COLORS[key] : 'var(--border)'}`,
              background: isBest ? METHOD_COLORS[key] + '0d' : 'transparent',
            }}>
              <div style={{ fontWeight: 700, color: METHOD_COLORS[key], fontSize: 13, marginBottom: 10 }}>
                {METHOD_LABELS[key]} {isBest && '★'}
              </div>
              <div style={{ marginBottom: 8 }}>
                <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--green)', marginBottom: 4 }}>PROS</div>
                {info?.pros.map((p, i) => <div key={i} style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 2 }}>✓ {p}</div>)}
              </div>
              <div style={{ marginBottom: 8 }}>
                <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--red)', marginBottom: 4 }}>CONS</div>
                {info?.cons.map((c, i) => <div key={i} style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 2 }}>✗ {c}</div>)}
              </div>
              <div style={{ marginTop: 8, padding: '6px 10px', background: 'rgba(255,255,255,0.04)', borderRadius: 6 }}>
                <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--text-muted)' }}>BEST FOR: </span>
                <span style={{ fontSize: 11, color: 'var(--text)' }}>{info?.bestFor}</span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function Placeholder() {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--text-muted)', fontSize: 14 }}>
      Run an analysis first to see comparisons.
    </div>
  );
}

const thStyle: React.CSSProperties = {
  padding: '10px 14px', textAlign: 'left', fontSize: 12, fontWeight: 700,
  color: 'var(--text-muted)', borderBottom: '1px solid var(--border)',
};
const tdStyle: React.CSSProperties = {
  padding: '10px 14px', borderBottom: '1px solid var(--border)', color: 'var(--text)',
};
