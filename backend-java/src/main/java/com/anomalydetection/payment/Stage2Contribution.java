package com.anomalydetection.payment;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static com.anomalydetection.payment.Dim.*;

/** Port of stages/stage2_contribution.py -- the original always-on monitor,
 * keyed on all 7 dimensions. Just ContributionHead with every dimension
 * selected; see Stage2Heads for the same math split across smaller,
 * independently owned dimension subsets. */
public class Stage2Contribution {

    private static final List<Dim> DIMS =
            List.of(NETWORK, GEOGRAPHY, ENTRY_MODE, PURCHASE_TYPE, AUTH_TYPE, CHANNEL, DECLINE_CODE);

    public static Map<String, Object> run(JdbcTemplate jdbc, int currentDay, int currentHour, double threshold) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stage", 2);
        result.put("name", "Contribution Shift Monitor");
        result.put("description", "Single computation — no pre-defined monitors. Each active cell's share of total declines vs same window weeks −1..−4. Self-discovers new cell combinations.");
        result.putAll(ContributionHead.run(jdbc, currentDay, currentHour, threshold, DIMS));
        return result;
    }
}
