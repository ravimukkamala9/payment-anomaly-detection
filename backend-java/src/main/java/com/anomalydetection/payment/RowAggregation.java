package com.anomalydetection.payment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/** The two queries every stage runs, as plain grouped sums -- no composite
 * string keys, no parallel bookkeeping maps. Rate/share formulas stay in
 * each stage, since that's the one thing that differs between them. */
final class RowAggregation {
    private RowAggregation() {}

    static class Agg {
        long total;
        long declines;
        void add(PaymentRow r) { total += r.totalCount; declines += r.declineCount; }
        double rate() { return declines / (double) Math.max(total, 1); }
    }

    /** Query 1 -- current window, grouped by key. */
    static <K> Map<K, Agg> groupByKey(Stream<PaymentRow> rows, Function<PaymentRow, K> keyFn) {
        Map<K, Agg> out = new LinkedHashMap<>();
        rows.forEach(r -> out.computeIfAbsent(keyFn.apply(r), k -> new Agg()).add(r));
        return out;
    }

    /** Query 2 -- prior weeks, same slot, grouped by (week, key). */
    static <K> Map<Integer, Map<K, Agg>> groupByWeekThenKey(Stream<PaymentRow> rows, Function<PaymentRow, K> keyFn) {
        Map<Integer, Map<K, Agg>> out = new LinkedHashMap<>();
        rows.forEach(r -> out
                .computeIfAbsent(r.week, w -> new LinkedHashMap<>())
                .computeIfAbsent(keyFn.apply(r), k -> new Agg())
                .add(r));
        return out;
    }

    static Stream<PaymentRow> currentWindow(List<PaymentRow> df, int day, int hour) {
        return df.stream().filter(r -> r.week == 0 && r.dayOfWeek == day && r.hour == hour);
    }

    static Stream<PaymentRow> historicalWindow(List<PaymentRow> df, int day, int hour) {
        return df.stream().filter(r -> r.week > 0 && r.dayOfWeek == day && r.hour == hour);
    }
}
