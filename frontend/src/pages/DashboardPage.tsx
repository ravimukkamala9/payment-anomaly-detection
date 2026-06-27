import React from 'react';
import { AnalysisResponse } from '../types';
import { Activity, AlertTriangle, Database, Zap } from 'lucide-react';
import { METHOD_COLORS, METHOD_LABELS } from '../utils/colors';

interface Props {
  response: AnalysisResponse | null;
  onNavigate: (page: string) => void;
}

export default function DashboardPage({ response, onNavigate }: Props) {
  return (
    <div style={{ padding: 28, overflowY: 'auto', height: '100%' }}>
      {/* Hero */}
      <div style={{
        background: 'linear-gradient(135deg, rgba(99,102,241,0.15) 0%, rgba(245,158,11,0.1) 50%, rgba(16,185,129,0.1) 100%)',
        border: '1px solid var(--border)', borderRadius: 14, padding: 32, marginBottom: 24,
      }}>
        <div style={{ fontSize: 28, fontWeight: 800, marginBottom: 8, background: 'linear-gradient(90deg, #818cf8, #f59e0b, #10b981)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
          Anomaly Detection Framework
        </div>
        <div style={{ fontSize: 14, color: 'var(--text-muted)', maxWidth: 580, lineHeight: 1.6 }}>
          Compare Z-Score, Isolation Forest, and Local Outlier Factor algorithms side by side.
          Detect anomalies, evaluate performance, and learn the math behind each technique.
        </div>
        <div style={{ display: 'flex', gap: 10, marginTop: 20 }}>
          <QuickBtn label="Run Analysis" onClick={() => onNavigate('analyze')} primary />
          <QuickBtn label="Learning Mode" onClick={() => onNavigate('tutorial')} />
        </div>
      </div>

      {/* Algorithm cards */}
      <div style={{ marginBottom: 24 }}>
        <h2 style={{ fontSize: 15, fontWeight: 700, marginBottom: 14 }}>Three Detection Approaches</h2>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 14 }}>
          <AlgoCard
            key="zscore" color={METHOD_COLORS.zscore} title="Z-Score" icon="σ"
            desc="Statistical approach that flags data points whose feature values deviate more than N standard deviations from the mean."
            complexity="O(n·d)" type="Statistical"
            response={response} methodKey="zscore" />
          <AlgoCard
            key="if" color={METHOD_COLORS.isolation_forest} title="Isolation Forest" icon="🌲"
            desc="Ensemble method that isolates anomalies by randomly partitioning data. Anomalies require fewer splits to isolate."
            complexity="O(n·t·log ψ)" type="Ensemble"
            response={response} methodKey="isolation_forest" />
          <AlgoCard
            key="lof" color={METHOD_COLORS.lof} title="Local Outlier Factor" icon="⊕"
            desc="Density-based technique that compares local density of a point to its neighbors. Low-density points are anomalies."
            complexity="O(n²)" type="Density-Based"
            response={response} methodKey="lof" />
        </div>
      </div>

      {/* Dataset summary */}
      {response && (
        <div style={{ marginBottom: 24 }}>
          <h2 style={{ fontSize: 15, fontWeight: 700, marginBottom: 14 }}>Last Analysis Summary</h2>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
            <SummaryCard icon={<Database size={16} />} label="Dataset" value={response.dataset.replace(/_/g, ' ')} />
            <SummaryCard icon={<Activity size={16} />} label="Samples" value={response.n_samples.toLocaleString()} />
            <SummaryCard icon={<Zap size={16} />} label="Features" value={response.feature_names.length} />
            <SummaryCard icon={<AlertTriangle size={16} />} label="Methods Run" value={Object.keys(response.results).length} />
          </div>
        </div>
      )}

      {/* Quick actions */}
      <div>
        <h2 style={{ fontSize: 15, fontWeight: 700, marginBottom: 14 }}>Quick Actions</h2>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12 }}>
          {[
            { title: 'Analyze Data', desc: 'Run detection on sample or uploaded datasets with configurable parameters.', page: 'analyze', color: '#6366f1' },
            { title: 'Compare Methods', desc: 'View side-by-side metrics, radar charts, and detailed trade-off analysis.', page: 'compare', color: '#f59e0b' },
            { title: 'Upload Dataset', desc: 'Bring your own CSV file and detect anomalies with all three methods.', page: 'upload', color: '#10b981' },
            { title: 'Learning Mode', desc: 'Step-by-step tutorial with math intuition and interactive visualizations.', page: 'tutorial', color: '#ec4899' },
          ].map(a => (
            <button key={a.page} onClick={() => onNavigate(a.page)} style={{
              textAlign: 'left', padding: '16px 18px', borderRadius: 10,
              background: 'var(--surface2)', border: `1px solid var(--border)`,
              color: 'var(--text)', transition: 'border-color 0.15s',
            }}
              onMouseEnter={e => (e.currentTarget.style.borderColor = a.color)}
              onMouseLeave={e => (e.currentTarget.style.borderColor = 'var(--border)')}>
              <div style={{ fontSize: 13, fontWeight: 700, color: a.color, marginBottom: 6 }}>{a.title} →</div>
              <div style={{ fontSize: 12, color: 'var(--text-muted)', lineHeight: 1.5 }}>{a.desc}</div>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

function AlgoCard({ color, title, icon, desc, complexity, type, response, methodKey }: {
  color: string; title: string; icon: string; desc: string;
  complexity: string; type: string; response: AnalysisResponse | null; methodKey: string;
}) {
  const result = response?.results[methodKey as keyof typeof response.results];
  return (
    <div style={{ background: 'var(--surface2)', border: `1px solid var(--border)`, borderRadius: 10, padding: 18, borderTop: `3px solid ${color}` }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}>
        <div style={{ fontSize: 20 }}>{icon}</div>
        <div>
          <div style={{ fontWeight: 700, fontSize: 13 }}>{title}</div>
          <div style={{ fontSize: 11, color, fontWeight: 600 }}>{type}</div>
        </div>
      </div>
      <p style={{ fontSize: 12, color: 'var(--text-muted)', lineHeight: 1.6, marginBottom: 12 }}>{desc}</p>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: 'var(--text-muted)', padding: '8px 10px', background: 'rgba(255,255,255,0.03)', borderRadius: 6 }}>
        <span>Complexity: <strong style={{ color: 'var(--text)' }}>{complexity}</strong></span>
        {result && <span style={{ color }}>F1: {result.metrics.f1_score !== null ? (result.metrics.f1_score * 100).toFixed(0) + '%' : '—'}</span>}
      </div>
    </div>
  );
}

function SummaryCard({ icon, label, value }: { icon: React.ReactNode; label: string; value: string | number }) {
  return (
    <div style={{ background: 'var(--surface2)', border: '1px solid var(--border)', borderRadius: 8, padding: '14px 16px', display: 'flex', alignItems: 'center', gap: 12 }}>
      <div style={{ color: 'var(--primary)' }}>{icon}</div>
      <div>
        <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{label}</div>
        <div style={{ fontSize: 16, fontWeight: 700, textTransform: 'capitalize' }}>{value}</div>
      </div>
    </div>
  );
}

function QuickBtn({ label, onClick, primary }: { label: string; onClick: () => void; primary?: boolean }) {
  return (
    <button onClick={onClick} style={{
      padding: '9px 20px', borderRadius: 7, fontWeight: 600, fontSize: 13,
      background: primary ? 'var(--primary)' : 'var(--surface2)',
      color: primary ? 'white' : 'var(--text)',
      border: primary ? 'none' : '1px solid var(--border)',
    }}>
      {label}
    </button>
  );
}
