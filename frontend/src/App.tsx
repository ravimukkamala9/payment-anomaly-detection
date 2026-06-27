import React, { useEffect, useState } from 'react';
import Sidebar from './components/layout/Sidebar';
import Header from './components/layout/Header';
import DashboardPage from './pages/DashboardPage';
import AnalyzePage from './pages/AnalyzePage';
import ComparePage from './pages/ComparePage';
import UploadPage from './pages/UploadPage';
import TutorialPage from './pages/TutorialPage';
import PaymentPipelinePage from './pages/PaymentPipelinePage';
import ParameterPanel from './components/ParameterPanel';
import { AnalysisParams, AnalysisResponse, DatasetInfo } from './types';
import { fetchDatasets, runAnalysis } from './utils/api';

const DEFAULT_PARAMS: AnalysisParams = {
  dataset: 'synthetic_2d',
  n_samples: 500,
  methods: ['zscore', 'isolation_forest', 'lof'],
  zscore_params: { threshold: 3.0 },
  if_params: { contamination: 0.05, n_estimators: 100 },
  lof_params: { n_neighbors: 20, contamination: 0.05, metric: 'euclidean' },
};

export default function App() {
  const [page, setPage] = useState('dashboard');
  const [params, setParams] = useState<AnalysisParams>(DEFAULT_PARAMS);
  const [response, setResponse] = useState<AnalysisResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [datasets, setDatasets] = useState<DatasetInfo[]>([]);

  useEffect(() => {
    fetchDatasets().then(setDatasets).catch(() => {});
  }, []);

  const handleRun = async () => {
    setLoading(true);
    setError('');
    try {
      const res = await runAnalysis(params);
      setResponse(res);
      if (page === 'dashboard') setPage('analyze');
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Analysis failed');
    } finally {
      setLoading(false);
    }
  };

  const handleDownload = () => {
    if (!response) return;
    const rows: string[][] = [['sample_index', ...response.raw_columns, 'true_label', ...Object.keys(response.results).map(k => `${k}_pred`)]];
    for (let i = 0; i < response.data.length; i++) {
      const row = [String(i), ...response.data[i].map(String), String(response.true_labels?.[i] ?? ''),
        ...Object.values(response.results).map(r => String(r.predictions[i]))];
      rows.push(row);
    }
    const csv = rows.map(r => r.join(',')).join('\n');
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
    a.download = `anomaly_results_${response.dataset}.csv`;
    a.click();
  };

  const showParams = page === 'analyze' || page === 'compare';
  const showHeader = page !== 'tutorial' && page !== 'payment';

  const pageTitle: Record<string, string> = {
    dashboard: 'Anomaly Detection Framework',
    analyze: 'Analyze',
    compare: 'Compare Methods',
    upload: 'Upload Dataset',
    payment: 'Payment Decline Pipeline',
    tutorial: 'Learning Mode',
  };

  return (
    <div style={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <Sidebar active={page} onChange={setPage} />

      {/* Parameter panel for analysis pages */}
      {showParams && (
        <div style={{ width: 240, background: 'var(--surface)', borderRight: '1px solid var(--border)', overflowY: 'auto', flexShrink: 0 }}>
          <div style={{ padding: '16px 18px', borderBottom: '1px solid var(--border)', fontSize: 12, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
            Configuration
          </div>
          <ParameterPanel params={params} datasets={datasets} onChange={setParams} />
        </div>
      )}

      {/* Main content */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {showHeader && (
          <Header
            title={pageTitle[page]}
            subtitle={page === 'analyze' && response ? `${response.dataset.replace(/_/g, ' ')} · ${response.n_samples} samples · ${response.feature_names.length} features` : undefined}
            onRun={showParams ? handleRun : undefined}
            onDownload={response && page !== 'upload' ? handleDownload : undefined}
            loading={loading}
          />
        )}

        {error && (
          <div style={{ padding: '10px 20px', background: 'rgba(239,68,68,0.1)', borderBottom: '1px solid rgba(239,68,68,0.3)', fontSize: 13, color: '#ef4444' }}>
            ⚠ {error} — Make sure the backend is running: <code>cd backend && uvicorn main:app --reload</code>
          </div>
        )}

        <div style={{ flex: 1, overflow: 'hidden' }}>
          {page === 'dashboard' && <DashboardPage response={response} onNavigate={setPage} />}
          {page === 'analyze' && <AnalyzePage response={response} />}
          {page === 'compare' && <ComparePage response={response} />}
          {page === 'upload' && <UploadPage onResult={r => { setResponse(r); setPage('analyze'); }} onNavigate={setPage} />}
          {page === 'payment' && <PaymentPipelinePage />}
          {page === 'tutorial' && <TutorialPage />}
        </div>
      </div>
    </div>
  );
}
