import React, { useState } from 'react';
import { AnalysisResponse, MethodKey } from '../types';
import { METHOD_COLORS, METHOD_LABELS } from '../utils/colors';
import ScatterPlot from '../components/charts/ScatterPlot';
import ScoreDistribution from '../components/charts/ScoreDistribution';
import MetricCard from '../components/MetricCard';
import { AlertTriangle, CheckCircle, Clock, Target } from 'lucide-react';

interface Props {
  response: AnalysisResponse | null;
}

export default function AnalyzePage({ response }: Props) {
  const [activeMethod, setActiveMethod] = useState<MethodKey>('zscore');

  if (!response) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', gap: 16, color: 'var(--text-muted)' }}>
        <AlertTriangle size={48} strokeWidth={1} style={{ opacity: 0.4 }} />
        <div style={{ fontSize: 18, fontWeight: 600, color: 'var(--text)' }}>No Analysis Yet</div>
        <div style={{ fontSize: 13 }}>Configure parameters in the sidebar and click <strong>Run Analysis</strong></div>
      </div>
    );
  }

  const methodKeys = Object.keys(response.results) as MethodKey[];
  if (!methodKeys.includes(activeMethod)) setActiveMethod(methodKeys[0]);

  const result = response.results[activeMethod];
  if (!result) return null;
  const m = result.metrics;

  return (
    <div style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 20, overflowY: 'auto', height: '100%' }}>
      {/* Method tabs */}
      <div style={{ display: 'flex', gap: 8 }}>
        {methodKeys.map(key => (
          <button key={key} onClick={() => setActiveMethod(key)} style={{
            padding: '8px 16px', borderRadius: 7, fontSize: 13, fontWeight: 600,
            background: activeMethod === key ? METHOD_COLORS[key] : 'var(--surface2)',
            color: activeMethod === key ? 'white' : 'var(--text-muted)',
            border: `1px solid ${activeMethod === key ? METHOD_COLORS[key] : 'var(--border)'}`,
          }}>
            {METHOD_LABELS[key]}
          </button>
        ))}
      </div>

      {/* Metric cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
        <MetricCard label="Anomalies Found" value={m.n_anomalies} color={METHOD_COLORS[activeMethod]} subtext={`${m.anomaly_rate}% of data`} />
        <MetricCard label="F1 Score" value={m.f1_score !== null ? (m.f1_score * 100).toFixed(1) : null} unit={m.f1_score !== null ? '%' : undefined} color={m.f1_score !== null && m.f1_score > 0.7 ? 'var(--green)' : 'var(--amber)'} />
        <MetricCard label="Precision" value={m.precision !== null ? (m.precision * 100).toFixed(1) : null} unit={m.precision !== null ? '%' : undefined} />
        <MetricCard label="Exec Time" value={m.execution_time_ms} unit="ms" color="var(--text-muted)" />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        {/* Scatter */}
        <div style={{ background: 'var(--surface2)', border: '1px solid var(--border)', borderRadius: 'var(--radius)', padding: 16 }}>
          <ScatterPlot response={response} method={activeMethod} height={340} />
        </div>
        {/* Score dist */}
        <div style={{ background: 'var(--surface2)', border: '1px solid var(--border)', borderRadius: 'var(--radius)', padding: 16 }}>
          <ScoreDistribution result={result} color={METHOD_COLORS[activeMethod]} height={340} />
        </div>
      </div>

      {/* More metrics */}
      {m.accuracy !== null && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
          <MetricCard label="Accuracy" value={(m.accuracy * 100).toFixed(1)} unit="%" color="var(--green)" />
          <MetricCard label="Recall" value={(m.recall! * 100).toFixed(1)} unit="%" />
          <MetricCard label="False Positive Rate" value={(m.false_positive_rate! * 100).toFixed(1)} unit="%" color={m.false_positive_rate! > 0.1 ? 'var(--red)' : 'var(--green)'} />
          <MetricCard label="Dataset Size" value={response.n_samples} subtext={`${response.feature_names.length} features`} />
        </div>
      )}

      {/* Confusion matrix */}
      {m.confusion_matrix && (
        <div style={{ background: 'var(--surface2)', border: '1px solid var(--border)', borderRadius: 'var(--radius)', padding: 20 }}>
          <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 16 }}>Confusion Matrix</div>
          <ConfusionMatrix matrix={m.confusion_matrix} />
        </div>
      )}
    </div>
  );
}

function ConfusionMatrix({ matrix }: { matrix: number[][] }) {
  const labels = ['Normal', 'Anomaly'];
  const max = Math.max(...matrix.flat());
  return (
    <div>
      <div style={{ display: 'grid', gridTemplateColumns: '80px 1fr 1fr', gap: 6, maxWidth: 320 }}>
        <div />
        <div style={{ textAlign: 'center', fontSize: 11, color: 'var(--text-muted)', fontWeight: 600 }}>Pred Normal</div>
        <div style={{ textAlign: 'center', fontSize: 11, color: 'var(--text-muted)', fontWeight: 600 }}>Pred Anomaly</div>
        {matrix.map((row, i) => (
          <React.Fragment key={i}>
            <div style={{ display: 'flex', alignItems: 'center', fontSize: 11, color: 'var(--text-muted)', fontWeight: 600 }}>
              Act {labels[i]}
            </div>
            {row.map((val, j) => {
              const isCorrect = i === j;
              const intensity = max > 0 ? val / max : 0;
              return (
                <div key={j} style={{
                  padding: '12px 8px', textAlign: 'center', borderRadius: 6,
                  background: isCorrect ? `rgba(16,185,129,${intensity * 0.5 + 0.1})` : `rgba(239,68,68,${intensity * 0.5 + 0.05})`,
                  color: 'var(--text)', fontWeight: 700, fontSize: 18,
                }}>
                  {val}
                </div>
              );
            })}
          </React.Fragment>
        ))}
      </div>
    </div>
  );
}
