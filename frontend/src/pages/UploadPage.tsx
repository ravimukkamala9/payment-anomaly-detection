import React, { useRef, useState } from 'react';
import { AnalysisResponse } from '../types';
import { runUploadAnalysis } from '../utils/api';
import { Upload, FileText, AlertCircle } from 'lucide-react';

interface Props {
  onResult: (r: AnalysisResponse) => void;
  onNavigate: (p: string) => void;
}

export default function UploadPage({ onResult, onNavigate }: Props) {
  const [file, setFile] = useState<File | null>(null);
  const [dragging, setDragging] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);

  const [params, setParams] = useState({
    zscore_threshold: 3.0,
    if_contamination: 0.05,
    if_n_estimators: 100,
    lof_n_neighbors: 20,
    lof_contamination: 0.05,
  });

  const handleFile = (f: File) => {
    if (!f.name.endsWith('.csv')) { setError('Only CSV files are supported.'); return; }
    setFile(f);
    setError('');
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragging(false);
    const f = e.dataTransfer.files[0];
    if (f) handleFile(f);
  };

  const handleRun = async () => {
    if (!file) return;
    setLoading(true);
    setError('');
    try {
      const result = await runUploadAnalysis(file, params);
      onResult(result);
      onNavigate('analyze');
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Upload failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ padding: 28, overflowY: 'auto', height: '100%', maxWidth: 700, margin: '0 auto' }}>
      <h2 style={{ fontSize: 18, fontWeight: 700, marginBottom: 6 }}>Upload Dataset</h2>
      <p style={{ fontSize: 13, color: 'var(--text-muted)', marginBottom: 24 }}>
        Upload a CSV file to run anomaly detection on your own data. Include numeric features and optionally a <code>true_label</code> column (0=normal, 1=anomaly) for metrics.
      </p>

      {/* Drop zone */}
      <div
        onClick={() => inputRef.current?.click()}
        onDragOver={e => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={handleDrop}
        style={{
          border: `2px dashed ${dragging ? 'var(--primary)' : file ? 'var(--green)' : 'var(--border)'}`,
          borderRadius: 12, padding: 40, textAlign: 'center', cursor: 'pointer',
          background: dragging ? 'rgba(99,102,241,0.06)' : 'var(--surface2)',
          marginBottom: 24, transition: 'all 0.15s',
        }}>
        <input ref={inputRef} type="file" accept=".csv" style={{ display: 'none' }}
          onChange={e => e.target.files?.[0] && handleFile(e.target.files[0])} />
        {file ? (
          <div>
            <FileText size={36} color="var(--green)" style={{ margin: '0 auto 12px' }} />
            <div style={{ fontWeight: 600, color: 'var(--green)' }}>{file.name}</div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 4 }}>{(file.size / 1024).toFixed(1)} KB — click to change</div>
          </div>
        ) : (
          <div>
            <Upload size={36} color="var(--text-muted)" style={{ margin: '0 auto 12px' }} />
            <div style={{ fontWeight: 600 }}>Drop CSV here or click to browse</div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 4 }}>Numeric columns only · max 10MB</div>
          </div>
        )}
      </div>

      {error && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '10px 14px', background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.3)', borderRadius: 7, marginBottom: 20, fontSize: 13, color: 'var(--red)' }}>
          <AlertCircle size={14} /> {error}
        </div>
      )}

      {/* Params */}
      <div style={{ background: 'var(--surface2)', border: '1px solid var(--border)', borderRadius: 10, padding: 20, marginBottom: 24 }}>
        <h3 style={{ fontSize: 13, fontWeight: 700, marginBottom: 16 }}>Detection Parameters</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
          {[
            { label: 'Z-Score Threshold', key: 'zscore_threshold', min: 1, max: 6, step: 0.1 },
            { label: 'IF Contamination', key: 'if_contamination', min: 0.01, max: 0.4, step: 0.01 },
            { label: 'IF N Estimators', key: 'if_n_estimators', min: 10, max: 300, step: 10 },
            { label: 'LOF N Neighbors', key: 'lof_n_neighbors', min: 5, max: 50, step: 1 },
            { label: 'LOF Contamination', key: 'lof_contamination', min: 0.01, max: 0.4, step: 0.01 },
          ].map(f => (
            <div key={f.key}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                <label style={{ fontSize: 12, color: 'var(--text-muted)' }}>{f.label}</label>
                <span style={{ fontSize: 12, fontWeight: 600, fontFamily: 'monospace' }}>{params[f.key as keyof typeof params]}</span>
              </div>
              <input type="range" min={f.min} max={f.max} step={f.step}
                value={params[f.key as keyof typeof params]}
                onChange={e => setParams(p => ({ ...p, [f.key]: Number(e.target.value) }))}
                style={{ width: '100%', accentColor: 'var(--primary)' }} />
            </div>
          ))}
        </div>
      </div>

      <button onClick={handleRun} disabled={!file || loading} style={{
        width: '100%', padding: '12px 20px', borderRadius: 8, fontWeight: 700, fontSize: 14,
        background: !file || loading ? 'var(--border)' : 'var(--primary)', color: 'white',
      }}>
        {loading ? 'Analyzing…' : 'Run Analysis on Uploaded File'}
      </button>

      {/* Format guide */}
      <div style={{ marginTop: 24, padding: 16, background: 'rgba(99,102,241,0.06)', border: '1px solid rgba(99,102,241,0.2)', borderRadius: 8, fontSize: 12 }}>
        <div style={{ fontWeight: 700, marginBottom: 8, color: 'var(--primary-light)' }}>Expected CSV Format</div>
        <pre style={{ color: 'var(--text-muted)', overflowX: 'auto', lineHeight: 1.7 }}>{`feature_1,feature_2,feature_3,true_label
1.23,4.56,7.89,0
-5.1,12.3,0.4,1
...`}</pre>
      </div>
    </div>
  );
}
