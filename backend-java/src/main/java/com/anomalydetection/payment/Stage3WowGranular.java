package com.anomalydetection.payment;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static com.anomalydetection.payment.Dim.*;

/** Port of stages/stage3_wow_granular.py -- the original always-on monitor,
 * keyed on all 7 dimensions. Just RateHead with every dimension selected;
 * see Stage2Heads for the same math split across smaller, independently
 * owned dimension subsets. */
public class Stage3WowGranular {

    private static final List<Dim> DIMS =
            List.of(NETWORK, GEOGRAPHY, ENTRY_MODE, PURCHASE_TYPE, AUTH_TYPE, CHANNEL, DECLINE_CODE);

    public static Map<String, Object> run(JdbcTemplate jdbc, int currentDay, int currentHour, double threshold) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stage", 3);
        result.put("name", "WoW Z-Score — Full Granularity");
        result.put("description", "No roll-up. Per-cell decline rate vs same Mon 14:00 in weeks −1..−4. Catches low-volume cells invisible to Stage 1, and rate shifts invisible to Stage 2.");
        result.putAll(RateHead.run(jdbc, currentDay, currentHour, threshold, DIMS));
        return result;
    }
}
