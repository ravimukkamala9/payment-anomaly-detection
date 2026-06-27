import React, { useState } from 'react';
import { ChevronRight, ChevronLeft, BookOpen } from 'lucide-react';

interface Step {
  title: string;
  subtitle: string;
  content: React.ReactNode;
}

const BLUE = '#6366f1';
const AMBER = '#f59e0b';
const GREEN = '#10b981';

function MathBlock({ children }: { children: React.ReactNode }) {
  return (
    <div style={{
      background: 'rgba(255,255,255,0.04)', border: '1px solid var(--border)',
      borderRadius: 8, padding: '12px 18px', fontFamily: 'monospace', fontSize: 14,
      color: '#e2e8f0', margin: '12px 0', overflowX: 'auto',
    }}>{children}</div>
  );
}

function Callout({ color, title, children }: { color: string; title: string; children: React.ReactNode }) {
  return (
    <div style={{
      background: color + '11', border: `1px solid ${color}44`,
      borderLeft: `4px solid ${color}`, borderRadius: 8, padding: '12px 16px', margin: '12px 0',
    }}>
      <div style={{ fontWeight: 700, color, fontSize: 12, marginBottom: 6 }}>{title}</div>
      <div style={{ fontSize: 13, color: 'var(--text-muted)', lineHeight: 1.6 }}>{children}</div>
    </div>
  );
}

function Steps({ items }: { items: string[] }) {
  return (
    <div style={{ margin: '12px 0' }}>
      {items.map((item, i) => (
        <div key={i} style={{ display: 'flex', gap: 12, marginBottom: 10 }}>
          <div style={{
            width: 24, height: 24, borderRadius: '50%', background: BLUE + '33',
            color: '#818cf8', fontSize: 12, fontWeight: 700, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
          }}>{i + 1}</div>
          <div style={{ fontSize: 13, color: 'var(--text-muted)', lineHeight: 1.6, paddingTop: 3 }}>{item}</div>
        </div>
      ))}
    </div>
  );
}

function Tag({ label, color }: { label: string; color: string }) {
  return <span style={{ display: 'inline-block', padding: '2px 8px', borderRadius: 4, background: color + '22', color, fontSize: 11, fontWeight: 700, marginRight: 6 }}>{label}</span>;
}

const STEPS: Step[] = [
  {
    title: 'What is Anomaly Detection?',
    subtitle: 'Core concepts and real-world applications',
    content: (
      <div>
        <p style={{ fontSize: 13, color: 'var(--text-muted)', lineHeight: 1.7, marginBottom: 16 }}>
          Anomaly detection (also called outlier detection) is the process of identifying data points that deviate
          significantly from expected patterns. These deviations may indicate errors, fraud, equipment failure, or novel events.
        </p>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12, marginBottom: 16 }}>
          {[
            { title: 'Point Anomalies', icon: '●', desc: 'A single data point deviates from the rest (e.g., a $50,000 transaction when average is $200).', color: '#ef4444' },
            { title: 'Contextual Anomalies', icon: '◈', desc: 'A value that is anomalous in a specific context but not globally (e.g., 30°C in December).', color: '#f59e0b' },
            { title: 'Collective Anomalies', icon: '◉', desc: 'A collection of data points is anomalous as a group (e.g., a DDoS sequence of requests).', color: '#8b5cf6' },
          ].map(t => (
            <div key={t.title} style={{ padding: 14, borderRadius: 8, background: t.color + '11', border: `1px solid ${t.color}33` }}>
              <div style={{ fontSize: 20, marginBottom: 6 }}>{t.icon}</div>
              <div style={{ fontWeight: 700, fontSize: 12, color: t.color, marginBottom: 6 }}>{t.title}</div>
              <div style={{ fontSize: 12, color: 'var(--text-muted)', lineHeight: 1.5 }}>{t.desc}</div>
            </div>
          ))}
        </div>
        <Callout color={GREEN} title="REAL-WORLD USE CASES">
          <strong>Finance:</strong> Fraud detection in credit card transactions · <strong>Cybersecurity:</strong> Intrusion detection in network traffic ·
          <strong>Manufacturing:</strong> Predictive maintenance from sensor data · <strong>Healthcare:</strong> Patient vital sign monitoring ·
          <strong>E-commerce:</strong> Bot traffic and account takeover detection
        </Callout>
        <Callout color={BLUE} title="WHY IT'S CHALLENGING">
          Anomalies are rare by definition — class imbalance makes supervised learning difficult. Labels are expensive or unavailable.
          The definition of "normal" shifts over time (concept drift). High-dimensional data makes distance metrics less meaningful (curse of dimensionality).
        </Callout>
      </div>
    ),
  },
  {
    title: 'Z-Score Method',
    subtitle: 'Statistical distance from the mean',
    content: (
      <div>
        <p style={{ fontSize: 13, color: 'var(--text-muted)', lineHeight: 1.7, marginBottom: 12 }}>
          The Z-Score (standard score) measures how many standard deviations a data point is from the mean.
          Points beyond a threshold (typically 3σ) are flagged as anomalies.
        </p>
        <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 6 }}>Formula</div>
        <MathBlock>
          {'z = (x - μ) / σ\n\nwhere:\n  x  = observed value\n  μ  = population mean\n  σ  = standard deviation\n\nAnomaly if: max|z_i| > threshold (default: 3.0)'}
        </MathBlock>
        <Steps items={[
          'Compute the mean (μ) and standard deviation (σ) for each feature.',
          'For each data point, compute the z-score for each feature: z = (x - μ) / σ.',
          'Take the maximum absolute z-score across all features for each sample.',
          'Flag samples where this max z-score exceeds the threshold (e.g., 3.0).',
        ]} />
        <Callout color={BLUE} title="MATHEMATICAL INTUITION">
          For a normal (Gaussian) distribution: ~68% of data falls within 1σ, ~95% within 2σ, ~99.7% within 3σ.
          Choosing threshold=3 means we expect to flag ~0.3% of normal data as false positives.
          The threshold directly controls the precision/recall trade-off.
        </Callout>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginTop: 12 }}>
          <Callout color={GREEN} title="WHEN TO USE">
            • Univariate or low-dimensional numeric data<br/>
            • Features follow roughly Gaussian distributions<br/>
            • Need interpretable, explainable results<br/>
            • Real-time monitoring dashboards<br/>
            • First-pass screening before deeper analysis
          </Callout>
          <Callout color="#ef4444" title="LIMITATIONS">
            • Assumes Gaussian distribution — fails with heavy tails<br/>
            • Sensitive to outliers in the mean/std calculation<br/>
            • Per-feature approach misses multivariate patterns<br/>
            • Global threshold may not suit all feature ranges
          </Callout>
        </div>
      </div>
    ),
  },
  {
    title: 'Isolation Forest',
    subtitle: 'Isolating anomalies with random trees',
    content: (
      <div>
        <p style={{ fontSize: 13, color: 'var(--text-muted)', lineHeight: 1.7, marginBottom: 12 }}>
          Isolation Forest (Liu et al., 2008) exploits the fact that anomalies are <em>few</em> and <em>different</em>.
          By randomly splitting data, anomalies are isolated in fewer steps than normal points.
        </p>
        <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 6 }}>Anomaly Score Formula</div>
        <MathBlock>
          {'s(x, n) = 2^(-E[h(x)] / c(n))\n\nwhere:\n  h(x) = path length to isolate point x\n  c(n) = average path length in a BST of n nodes\n       = 2·H(n-1) - 2(n-1)/n   [H = harmonic number]\n  E[h(x)] = average path length over all trees\n\ns → 1.0 = highly anomalous\ns → 0.5 = indeterminate\ns → 0.0 = normal'}
        </MathBlock>
        <Steps items={[
          'Build t isolation trees by randomly selecting a feature and a split value between its min and max.',
          'Repeat until each point is isolated in its own leaf node.',
          'Anomalies typically reach leaf nodes in fewer splits (shorter path length).',
          'Average path length across all t trees gives the anomaly score.',
          'Points with scores above a threshold (set by contamination parameter) are flagged.',
        ]} />
        <Callout color={AMBER} title="WHY IT WORKS">
          Normal points cluster together — they require many recursive splits to isolate.
          Anomalies are sparse and different — they can be isolated quickly with just a few cuts.
          This exploits the geometric nature of anomalies without computing distances.
        </Callout>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginTop: 12 }}>
          <Callout color={GREEN} title="ADVANTAGES">
            • No distance/density computation — very fast<br/>
            • Sub-linear with subsampling (ψ parameter)<br/>
            • Handles high-dimensional data well<br/>
            • No distributional assumptions<br/>
            • Naturally parallelizable across trees
          </Callout>
          <Callout color="#ef4444" title="WATCH OUT FOR">
            • Struggles with clustered anomalies (masking effect)<br/>
            • contamination hyperparameter must be set carefully<br/>
            • Non-deterministic (use random_state for reproducibility)<br/>
            • Less interpretable than Z-Score
          </Callout>
        </div>
      </div>
    ),
  },
  {
    title: 'Local Outlier Factor (LOF)',
    subtitle: 'Density-based local anomaly scoring',
    content: (
      <div>
        <p style={{ fontSize: 13, color: 'var(--text-muted)', lineHeight: 1.7, marginBottom: 12 }}>
          LOF (Breunig et al., 2000) computes how much more sparse a point's neighborhood is compared to its neighbors' neighborhoods.
          It finds anomalies even in datasets with clusters of varying density.
        </p>
        <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 6 }}>Key Definitions</div>
        <MathBlock>
          {'k-distance(x):  distance to the k-th nearest neighbor of x\n\nreach-dist_k(x, o) = max(k-distance(o), dist(x, o))\n  → smoothed distance from x to neighbor o\n\nlrd_k(x) = 1 / (Σ reach-dist_k(x, o) / k)\n  → local reachability density of x\n\nLOF_k(x) = (Σ lrd_k(o) / lrd_k(x)) / k\n  → ratio of neighbor density to x\'s density\n\nLOF > 1.5  →  x is less dense than neighbors  →  anomaly\nLOF ≈ 1.0  →  similar density  →  normal'}
        </MathBlock>
        <Steps items={[
          'For each point x, find its k nearest neighbors.',
          'Compute the reachability distance to smooth out micro-cluster effects.',
          'Compute local reachability density (lrd): inverse of average reachability distance.',
          'Compute LOF as the ratio of average neighbor density to x\'s density.',
          'LOF >> 1 means x is in a much sparser region than its neighbors → anomaly.',
        ]} />
        <Callout color={GREEN} title="KEY INSIGHT: LOCAL vs GLOBAL">
          Unlike Z-Score which uses global statistics, LOF computes a <em>local</em> density score.
          This means it can correctly identify anomalies that appear in denser or sparser regions simultaneously.
          A point far from a cluster is anomalous; so is a point loosely connected to a tight cluster.
        </Callout>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginTop: 12 }}>
          <Callout color={GREEN} title="BEST FOR">
            • Data with multiple clusters of different densities<br/>
            • Spatial/geographic anomaly detection<br/>
            • When global thresholds are insufficient<br/>
            • Moderate-sized datasets (≤ 50K rows)
          </Callout>
          <Callout color="#ef4444" title="LIMITATIONS">
            • O(n²) naive complexity — slow on large data<br/>
            • k (n_neighbors) is sensitive and must be tuned<br/>
            • Cannot score new points after training (no novelty detection by default)<br/>
            • Memory-intensive for large n
          </Callout>
        </div>
      </div>
    ),
  },
  {
    title: 'Method Comparison & Trade-offs',
    subtitle: 'Choosing the right approach for your use case',
    content: (
      <div>
        <div style={{ overflowX: 'auto', marginBottom: 20 }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
            <thead>
              <tr style={{ background: 'var(--surface2)' }}>
                {['Dimension', 'Z-Score', 'Isolation Forest', 'LOF'].map(h => (
                  <th key={h} style={{ padding: '10px 14px', textAlign: 'left', fontWeight: 700, color: 'var(--text-muted)', borderBottom: '1px solid var(--border)', fontSize: 11, textTransform: 'uppercase' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {[
                ['Time Complexity', 'O(n·d) ✓✓✓', 'O(n·t·log ψ) ✓✓', 'O(n²) ✗'],
                ['Space', 'O(n·d)', 'O(t·ψ)', 'O(n²)'],
                ['Distributional Assumption', 'Gaussian required', 'None ✓', 'None ✓'],
                ['Interpretability', 'High ✓✓✓', 'Medium', 'Medium'],
                ['High Dimensions', 'Degrades', 'Handles well ✓', 'Degrades'],
                ['Varying Density', 'Poor ✗', 'Moderate', 'Excellent ✓✓✓'],
                ['Online/Streaming', 'Yes ✓', 'Partial', 'No ✗'],
                ['Labeling Required', 'No', 'No', 'No'],
                ['Hyperparameter Sensitivity', 'Low', 'Moderate', 'High'],
              ].map((row, i) => (
                <tr key={i} style={{ background: i % 2 === 0 ? 'transparent' : 'rgba(255,255,255,0.02)' }}>
                  {row.map((cell, j) => (
                    <td key={j} style={{
                      padding: '9px 14px', borderBottom: '1px solid var(--border)',
                      color: j === 0 ? 'var(--text)' : 'var(--text-muted)',
                      fontWeight: j === 0 ? 600 : 400,
                    }}>{cell}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <h3 style={{ fontSize: 14, fontWeight: 700, marginBottom: 12 }}>Decision Guide</h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {[
            { condition: 'Need real-time scoring with < 1ms latency', recommendation: 'Z-Score', color: BLUE, reason: 'Linear complexity, no model state needed after computing μ and σ' },
            { condition: 'General tabular data with unknown distribution', recommendation: 'Isolation Forest', color: AMBER, reason: 'Robust, fast, no assumptions — industry default for production anomaly detection' },
            { condition: 'Clustered data where normal points form groups', recommendation: 'LOF', color: GREEN, reason: 'Local density comparison correctly handles intra-cluster vs inter-cluster anomalies' },
            { condition: 'Dataset has > 100K rows', recommendation: 'Z-Score or Isolation Forest', color: BLUE, reason: 'LOF\'s quadratic complexity becomes prohibitive; IF with subsampling scales well' },
            { condition: 'Need to explain anomaly to stakeholders', recommendation: 'Z-Score', color: BLUE, reason: '"This transaction is 4.2 standard deviations above the mean amount" is instantly understandable' },
          ].map((item, i) => (
            <div key={i} style={{ display: 'flex', gap: 12, padding: '12px 14px', background: 'var(--surface2)', border: '1px solid var(--border)', borderRadius: 8 }}>
              <div style={{ flexShrink: 0, paddingTop: 2 }}>
                <div style={{ width: 8, height: 8, borderRadius: '50%', background: item.color }} />
              </div>
              <div>
                <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 4 }}>
                  IF: <em style={{ color: 'var(--text-muted)', fontWeight: 400 }}>{item.condition}</em>
                  {' → '}<Tag label={item.recommendation} color={item.color} />
                </div>
                <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>{item.reason}</div>
              </div>
            </div>
          ))}
        </div>
      </div>
    ),
  },
  {
    title: 'Architecture & Data Flow',
    subtitle: 'How the framework is structured end-to-end',
    content: (
      <div>
        <p style={{ fontSize: 13, color: 'var(--text-muted)', lineHeight: 1.7, marginBottom: 16 }}>
          The framework uses a React frontend communicating with a FastAPI backend. Each detector runs independently and results are aggregated for comparison.
        </p>
        <div style={{ fontFamily: 'monospace', fontSize: 12, background: 'rgba(255,255,255,0.03)', border: '1px solid var(--border)', borderRadius: 8, padding: 20, lineHeight: 2, color: 'var(--text-muted)', marginBottom: 16 }}>
          <div style={{ color: '#818cf8', fontWeight: 700 }}>┌─── USER INTERFACE (React + Vite) ──────────────────────────┐</div>
          <div>│  Dashboard → Analyze → Compare → Upload → Tutorial        │</div>
          <div>│  Plotly.js charts · Parameter sliders · Real-time updates  │</div>
          <div style={{ color: '#818cf8', fontWeight: 700 }}>└──────────────────────┬─────────────────────────────────────┘</div>
          <div style={{ textAlign: 'center' }}>                       │ HTTP/REST (JSON)</div>
          <div style={{ color: '#f59e0b', fontWeight: 700 }}>┌──────────────────────▼─────────────────────────────────────┐</div>
          <div>│  FastAPI Backend                                            │</div>
          <div>│  POST /analyze · POST /analyze/upload · GET /datasets      │</div>
          <div style={{ color: '#f59e0b', fontWeight: 700 }}>└──────┬────────────────┬──────────────────┬──────────────────┘</div>
          <div>       │                │                  │</div>
          <div style={{ color: '#10b981' }}>  ┌────▼────┐    ┌────▼──────┐   ┌────▼────┐</div>
          <div style={{ color: '#10b981' }}>  │ Z-Score │    │ Isolation │   │  LOF    │</div>
          <div style={{ color: '#10b981' }}>  │detector │    │  Forest   │   │detector │</div>
          <div style={{ color: '#10b981' }}>  └─────────┘    └───────────┘   └─────────┘</div>
          <div>              ↓                ↓                  ↓</div>
          <div>       sklearn.preprocessing · sklearn.ensemble · sklearn.neighbors</div>
        </div>

        <h3 style={{ fontSize: 13, fontWeight: 700, marginBottom: 10 }}>Technology Stack</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
          {[
            { layer: 'Frontend', items: ['React 18 + TypeScript', 'Vite (build & dev server)', 'Plotly.js (interactive charts)', 'Lucide React (icons)'] },
            { layer: 'Backend', items: ['Python 3.11+', 'FastAPI + Uvicorn', 'scikit-learn (ML algorithms)', 'pandas + numpy (data processing)'] },
            { layer: 'Deployment (Local)', items: ['npm run dev (port 5173)', 'uvicorn main:app (port 8000)', 'Vite proxy /api → 8000', 'No database required'] },
            { layer: 'Deployment (Cloud)', items: ['Frontend: Vercel / CloudFront', 'Backend: AWS Lambda / Cloud Run', 'Container: Docker (single image)', 'Scaling: horizontal (stateless)'] },
          ].map(s => (
            <div key={s.layer} style={{ padding: 14, background: 'var(--surface2)', border: '1px solid var(--border)', borderRadius: 8 }}>
              <div style={{ fontWeight: 700, fontSize: 12, color: 'var(--primary-light)', marginBottom: 8 }}>{s.layer}</div>
              {s.items.map((item, i) => <div key={i} style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 4 }}>→ {item}</div>)}
            </div>
          ))}
        </div>
      </div>
    ),
  },
  {
    title: 'Hands-On Practice',
    subtitle: 'Apply what you\'ve learned',
    content: (
      <div>
        <p style={{ fontSize: 13, color: 'var(--text-muted)', lineHeight: 1.7, marginBottom: 20 }}>
          Now that you understand the algorithms, here are practical exercises to deepen your understanding.
        </p>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          {[
            {
              level: 'Beginner', color: GREEN,
              title: 'Parameter Sensitivity Analysis',
              tasks: [
                'Go to Analyze → Z-Score and adjust the threshold from 2.0 to 5.0. Observe how anomaly count changes.',
                'Increase LOF n_neighbors from 5 to 50. Notice how local context changes detections.',
                'Set IF contamination to 0.01 vs 0.3. What happens to precision vs recall?',
              ],
              insight: 'Lower thresholds = more anomalies detected but more false positives (higher recall, lower precision).',
            },
            {
              level: 'Intermediate', color: AMBER,
              title: 'Dataset Comparison',
              tasks: [
                'Run all 3 methods on "Synthetic 2D" and note which performs best.',
                'Switch to "Credit Card Fraud" — does the ranking of methods change?',
                'Compare radar charts across datasets. Which method is most consistent?',
              ],
              insight: 'No single algorithm wins every dataset. Domain knowledge and data properties determine the best choice.',
            },
            {
              level: 'Advanced', color: '#8b5cf6',
              title: 'Build Your Own Dataset',
              tasks: [
                'Create a CSV with columns: value_1, value_2, true_label (0 or 1).',
                'Add ~95% rows from N(0,1) and ~5% rows from N(10,1) as anomalies.',
                'Upload to "Upload Data" and run all three methods.',
                'Which method achieves the highest F1 score? Why?',
              ],
              insight: 'Well-separated Gaussian anomalies favor Z-Score. Clustered or contextual anomalies favor LOF or Isolation Forest.',
            },
          ].map(ex => (
            <div key={ex.title} style={{ padding: 16, background: 'var(--surface2)', border: `1px solid ${ex.color}44`, borderRadius: 10 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
                <Tag label={ex.level} color={ex.color} />
                <span style={{ fontWeight: 700, fontSize: 13 }}>{ex.title}</span>
              </div>
              <div style={{ marginBottom: 10 }}>
                {ex.tasks.map((t, i) => (
                  <div key={i} style={{ display: 'flex', gap: 8, marginBottom: 6, fontSize: 12, color: 'var(--text-muted)' }}>
                    <span style={{ color: ex.color, fontWeight: 700, flexShrink: 0 }}>□</span>
                    {t}
                  </div>
                ))}
              </div>
              <div style={{ fontSize: 12, color: ex.color, padding: '8px 12px', background: ex.color + '11', borderRadius: 6 }}>
                💡 {ex.insight}
              </div>
            </div>
          ))}
        </div>
      </div>
    ),
  },
];

export default function TutorialPage() {
  const [step, setStep] = useState(0);

  return (
    <div style={{ display: 'flex', height: '100%', overflow: 'hidden' }}>
      {/* Step nav */}
      <div style={{ width: 220, background: 'var(--surface)', borderRight: '1px solid var(--border)', padding: 16, overflowY: 'auto', flexShrink: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16, color: 'var(--primary-light)', fontWeight: 700, fontSize: 12 }}>
          <BookOpen size={14} /> LEARNING PATH
        </div>
        {STEPS.map((s, i) => (
          <button key={i} onClick={() => setStep(i)} style={{
            width: '100%', textAlign: 'left', padding: '9px 12px', borderRadius: 7, marginBottom: 4,
            background: step === i ? 'rgba(99,102,241,0.15)' : 'transparent',
            color: step === i ? 'var(--primary-light)' : 'var(--text-muted)',
            fontWeight: step === i ? 600 : 400, fontSize: 12,
            border: step === i ? '1px solid rgba(99,102,241,0.3)' : '1px solid transparent',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <div style={{
                width: 20, height: 20, borderRadius: '50%', flexShrink: 0,
                background: i < step ? 'var(--green)' : step === i ? 'var(--primary)' : 'var(--border)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 10, color: 'white', fontWeight: 700,
              }}>{i < step ? '✓' : i + 1}</div>
              <span style={{ lineHeight: 1.3 }}>{s.title}</span>
            </div>
          </button>
        ))}
        <div style={{ marginTop: 16, padding: '10px 12px', background: 'rgba(16,185,129,0.08)', borderRadius: 7, fontSize: 11, color: 'var(--green)' }}>
          Progress: {step + 1}/{STEPS.length} ({Math.round(((step + 1) / STEPS.length) * 100)}%)
          <div style={{ marginTop: 6, height: 4, background: 'var(--border)', borderRadius: 2 }}>
            <div style={{ height: '100%', width: `${((step + 1) / STEPS.length) * 100}%`, background: 'var(--green)', borderRadius: 2, transition: 'width 0.3s' }} />
          </div>
        </div>
      </div>

      {/* Content */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <div style={{ padding: '20px 28px', borderBottom: '1px solid var(--border)', background: 'var(--surface)' }}>
          <div style={{ fontSize: 11, color: 'var(--primary-light)', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 4 }}>
            Step {step + 1} of {STEPS.length}
          </div>
          <h2 style={{ fontSize: 20, fontWeight: 800 }}>{STEPS[step].title}</h2>
          <p style={{ fontSize: 13, color: 'var(--text-muted)', marginTop: 3 }}>{STEPS[step].subtitle}</p>
        </div>

        <div style={{ flex: 1, overflowY: 'auto', padding: '24px 28px' }}>
          {STEPS[step].content}
        </div>

        {/* Navigation */}
        <div style={{
          padding: '14px 28px', borderTop: '1px solid var(--border)', background: 'var(--surface)',
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        }}>
          <button onClick={() => setStep(s => Math.max(0, s - 1))} disabled={step === 0} style={{
            display: 'flex', alignItems: 'center', gap: 6, padding: '8px 16px', borderRadius: 7,
            background: 'var(--surface2)', color: step === 0 ? 'var(--border)' : 'var(--text)',
            border: '1px solid var(--border)', fontSize: 13,
          }}>
            <ChevronLeft size={14} /> Previous
          </button>
          <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
            {STEPS[step].title}
          </div>
          <button onClick={() => setStep(s => Math.min(STEPS.length - 1, s + 1))} disabled={step === STEPS.length - 1} style={{
            display: 'flex', alignItems: 'center', gap: 6, padding: '8px 16px', borderRadius: 7,
            background: step === STEPS.length - 1 ? 'var(--surface2)' : 'var(--primary)', color: step === STEPS.length - 1 ? 'var(--border)' : 'white',
            fontWeight: 600, fontSize: 13,
          }}>
            Next <ChevronRight size={14} />
          </button>
        </div>
      </div>
    </div>
  );
}
