"""
Stage 1 — Roll-up WoW Z-Score
Roll up to (decline_code × channel), then compare current window's decline rate
to the SAME window (same day-of-week, same hour) across the prior 4 weeks.
Catches high-volume rate spikes that are statistically significant at roll-up level.
"""
import time
import pandas as pd
import numpy as np

ROLLUP_DIMS = ['decline_code', 'channel']


def run(df: pd.DataFrame, current_day: int = 0, current_hour: int = 14, threshold: float = 3.0) -> dict:
    start = time.perf_counter()

    curr = df[(df['week'] == 0) & (df['day_of_week'] == current_day) & (df['hour'] == current_hour)]
    hist = df[(df['week'] > 0) & (df['day_of_week'] == current_day) & (df['hour'] == current_hour)]

    # Roll up to (decline_code, channel)
    def rollup(frame, extra_group=None):
        groups = ([extra_group] if extra_group else []) + ROLLUP_DIMS
        g = frame.groupby(groups).agg(total=('total_count', 'sum'), declines=('decline_count', 'sum')).reset_index()
        g['rate'] = g['declines'] / g['total'].clip(lower=1)
        return g

    curr_r = rollup(curr)
    hist_r = rollup(hist, extra_group='week')

    # Historical stats per roll-up cell
    stats = hist_r.groupby(ROLLUP_DIMS).agg(
        wow_mean=('rate', 'mean'),
        wow_std=('rate', 'std'),
        n_weeks=('week', 'count'),
    ).reset_index()
    stats['wow_std'] = stats['wow_std'].fillna(0)

    merged = curr_r.merge(stats, on=ROLLUP_DIMS, how='left')
    merged['is_new'] = merged['wow_mean'].isna()
    merged['wow_mean'] = merged['wow_mean'].fillna(0)
    merged['z_score'] = (merged['rate'] - merged['wow_mean']) / merged['wow_std'].clip(lower=0.0005)
    merged.loc[merged['is_new'], 'z_score'] = 99.0
    merged['alerted'] = (merged['z_score'].abs() >= threshold) | merged['is_new']

    alerts = merged[merged['alerted']].sort_values('z_score', ascending=False)

    # Chart data: all monitors sorted by z-score (for bar chart)
    chart = merged.sort_values('z_score', ascending=False).head(30)
    chart_data = [
        {
            'label': f"{r['decline_code']}×{r['channel']}",
            'z_score': round(float(r['z_score']), 2),
            'rate': round(float(r['rate']), 4),
            'wow_mean': round(float(r['wow_mean']), 4),
            'alerted': bool(r['alerted']),
        }
        for _, r in chart.iterrows()
    ]

    exec_ms = (time.perf_counter() - start) * 1000
    return {
        'stage': 1,
        'name': 'Roll-up WoW Z-Score',
        'description': (
            f'Rolled to ({", ".join(ROLLUP_DIMS)}). '
            f'Current window vs same Mon 14:00 in weeks −1 through −4.'
        ),
        'n_monitors': int(len(merged)),
        'n_alerts': int(len(alerts)),
        'threshold': threshold,
        'execution_ms': round(exec_ms, 2),
        'chart_data': chart_data,
        'alerts': _to_records(alerts[ROLLUP_DIMS + ['total', 'declines', 'rate', 'wow_mean', 'wow_std', 'z_score', 'alerted']]),
    }


def _to_records(df: pd.DataFrame) -> list:
    records = []
    for _, r in df.iterrows():
        d = {}
        for k, v in r.items():
            if isinstance(v, (np.integer,)): d[k] = int(v)
            elif isinstance(v, (np.floating,)): d[k] = round(float(v), 4)
            elif isinstance(v, (np.bool_,)): d[k] = bool(v)
            else: d[k] = v
        records.append(d)
    return records
