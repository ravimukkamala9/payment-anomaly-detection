import { AnalysisParams, AnalysisResponse, DatasetInfo } from '../types';

const BASE = '/api';

export async function fetchDatasets(): Promise<DatasetInfo[]> {
  const res = await fetch(`${BASE}/datasets`);
  if (!res.ok) throw new Error('Failed to fetch datasets');
  const data = await res.json();
  return data.datasets;
}

export async function runAnalysis(params: AnalysisParams): Promise<AnalysisResponse> {
  const res = await fetch(`${BASE}/analyze`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.detail || 'Analysis failed');
  }
  return res.json();
}

export async function runUploadAnalysis(
  file: File,
  params: { zscore_threshold: number; if_contamination: number; if_n_estimators: number; lof_n_neighbors: number; lof_contamination: number }
): Promise<AnalysisResponse> {
  const form = new FormData();
  form.append('file', file);
  const qs = new URLSearchParams(params as unknown as Record<string, string>).toString();
  const res = await fetch(`${BASE}/analyze/upload?${qs}`, { method: 'POST', body: form });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.detail || 'Upload analysis failed');
  }
  return res.json();
}
