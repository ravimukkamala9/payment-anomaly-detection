import React from 'react';
import { RefreshCw, Download } from 'lucide-react';

interface Props {
  title: string;
  subtitle?: string;
  onRun?: () => void;
  onDownload?: () => void;
  loading?: boolean;
}

export default function Header({ title, subtitle, onRun, onDownload, loading }: Props) {
  return (
    <div style={{
      padding: '20px 28px',
      borderBottom: '1px solid var(--border)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      background: 'var(--surface)',
    }}>
      <div>
        <h1 style={{ fontSize: 18, fontWeight: 700, color: 'var(--text)' }}>{title}</h1>
        {subtitle && <p style={{ fontSize: 13, color: 'var(--text-muted)', marginTop: 2 }}>{subtitle}</p>}
      </div>
      <div style={{ display: 'flex', gap: 10 }}>
        {onDownload && (
          <button onClick={onDownload} style={{
            display: 'flex', alignItems: 'center', gap: 6, padding: '8px 14px',
            borderRadius: 7, background: 'var(--surface2)', color: 'var(--text-muted)',
            border: '1px solid var(--border)', fontSize: 13,
          }}>
            <Download size={14} /> Export
          </button>
        )}
        {onRun && (
          <button onClick={onRun} disabled={loading} style={{
            display: 'flex', alignItems: 'center', gap: 6, padding: '8px 18px',
            borderRadius: 7, background: loading ? 'var(--border)' : 'var(--primary)',
            color: 'white', fontWeight: 600, fontSize: 13,
          }}>
            <RefreshCw size={14} style={{ animation: loading ? 'spin 1s linear infinite' : 'none' }} />
            {loading ? 'Running…' : 'Run Analysis'}
          </button>
        )}
      </div>
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}
