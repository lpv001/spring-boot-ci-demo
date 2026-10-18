package com.example.demo.controller;


import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
public class HeavyProcessController {

    private final Counter heavyProcessCounter;
    private final Timer heavyProcessTimer;

    public HeavyProcessController(MeterRegistry registry) {
        this.heavyProcessCounter = Counter.builder("test_heavy_process_total")
                .description("Total heavy-process test executions")
                .register(registry);

        this.heavyProcessTimer = Timer.builder("test_heavy_process_duration")
                .description("Heavy-process execution duration")
                .register(registry);
    }

    @GetMapping("/api/test/heavy")
    public Map<String, Object> heavyProcess(
            @RequestParam(defaultValue = "3000") long durationMs) {

        // Limit the duration to prevent accidental long-running requests.
        durationMs = Math.max(100, Math.min(durationMs, 5000));

        final long requestedDuration = durationMs;

        double checksum = heavyProcessTimer.record(() -> {
            long endTime = System.nanoTime()
                    + TimeUnit.MILLISECONDS.toNanos(requestedDuration);

            double result = 0.0;
            long i = 1;

            // CPU-intensive calculations until the deadline.
            while (System.nanoTime() < endTime) {
                result += Math.sqrt(i) * Math.sin(i);
                i++;
            }

            return result;
        });

        heavyProcessCounter.increment();

        return Map.of(
                "status", "completed",
                "durationMs", requestedDuration,
                "checksum", checksum,
                "iterationsPerRequest", "CPU-intensive calculation completed"
        );
    }
}

