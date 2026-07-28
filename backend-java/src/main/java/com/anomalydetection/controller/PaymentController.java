package com.anomalydetection.controller;

import com.anomalydetection.payment.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private volatile List<PaymentRow> paymentDf = null;

    @PostMapping("/generate")
    public Map<String, Object> generate() {
        paymentDf = PaymentDataGenerator.generate(42);

        Set<String> distinctCells = new HashSet<>();
        for (PaymentRow r : paymentDf) distinctCells.add(r.cellKey());

        Map<String, Object> currentWindow = new LinkedHashMap<>();
        currentWindow.put("week", 0);
        currentWindow.put("day_of_week", PaymentDataGenerator.CURRENT_DAY);
        currentWindow.put("hour", PaymentDataGenerator.CURRENT_HOUR);
        currentWindow.put("label", "Monday 14:00");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("n_rows", paymentDf.size());
        resp.put("n_cells", distinctCells.size());
        resp.put("weeks", 5);
        resp.put("current_window", currentWindow);
        resp.put("anomalies_injected", PaymentDataGenerator.getAnomalyInfo());
        resp.put("decline_codes_total", 40);
        return resp;
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> download() {
        if (paymentDf == null) {
            return ResponseEntity.status(404).body("Generate data first via POST /payment/generate".getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("week,day_of_week,hour,network,geography,entry_mode,purchase_type,auth_type,channel,decline_code,total_count,decline_count,decline_rate\n");
        for (PaymentRow r : paymentDf) {
            sb.append(r.week).append(',').append(r.dayOfWeek).append(',').append(r.hour).append(',')
                    .append(r.network).append(',').append(r.geography).append(',').append(r.entryMode).append(',')
                    .append(r.purchaseType).append(',').append(r.authType).append(',').append(r.channel).append(',')
                    .append(r.declineCode).append(',').append(r.totalCount).append(',').append(r.declineCount).append(',')
                    .append(r.declineRate).append('\n');
        }
        byte[] csv = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payment_decline_data.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @PostMapping("/stage/{stageNum}")
    public ResponseEntity<?> stage(@PathVariable int stageNum, @RequestParam(defaultValue = "3.0") double threshold) {
        if (paymentDf == null) {
            return ResponseEntity.status(400).body(Map.of("detail", "Generate data first"));
        }
        int cd = PaymentDataGenerator.CURRENT_DAY;
        int ch = PaymentDataGenerator.CURRENT_HOUR;
        switch (stageNum) {
            case 1: return ResponseEntity.ok(Stage1RollupWow.run(paymentDf, cd, ch, threshold));
            case 2: return ResponseEntity.ok(Stage2Contribution.run(paymentDf, cd, ch, threshold));
            case 3: return ResponseEntity.ok(Stage3WowGranular.run(paymentDf, cd, ch, threshold));
            default: return ResponseEntity.status(400).body(Map.of("detail", "Stage must be 1, 2 or 3"));
        }
    }
}
