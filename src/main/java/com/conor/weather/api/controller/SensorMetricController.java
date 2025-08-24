package com.conor.weather.api.controller;

import com.conor.weather.api.model.SensorMetric;
import com.conor.weather.api.repository.SensorMetricRepository;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.validation.annotation.Validated;



@RestController
@Validated
@RequestMapping("/api/metrics")
public class SensorMetricController {

    private final SensorMetricRepository repo;

    public SensorMetricController(SensorMetricRepository repo) {
        this.repo = repo;
    }

    private static final Set<String> ALLOWED_METRICS =
            Set.of("temperature","humidity","wind_speed");

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody SensorMetric metric) {
        if (metric.getTimestamp() == null) metric.setTimestamp(LocalDateTime.now());
        if (metric.getMetricType() != null) {
            String normalized = metric.getMetricType().trim().toLowerCase();
            if (!ALLOWED_METRICS.contains(normalized)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error","metricType must be one of " + ALLOWED_METRICS));
            }
            metric.setMetricType(normalized);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(metric));
    }


    @GetMapping
    public List<SensorMetric> all() {
        return repo.findAll();
    }


    @GetMapping("/query")
    public List<SensorMetric> query(
            @RequestParam Long sensorId,
            @RequestParam String metricType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        return repo.findBySensorIdAndMetricTypeAndTimestampBetween(
                sensorId,
                metricType.trim().toLowerCase(),
                start,
                end
        );
    }


    @GetMapping("/stats")
    public ResponseEntity<?> stats(
            @RequestParam(required = false) String sensors,
            @RequestParam(required = false) String metrics,
            @RequestParam(required = false, defaultValue = "avg") String stat,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        // default window: last 24h
        var now = LocalDateTime.now();
        if (start == null || end == null) { end = now; start = now.minusDays(1); }

        var hours = java.time.Duration.between(start, end).toHours();
        if (hours < 24 || hours > 31 * 24) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "date window must be between 1 and 31 days")
            );
        }


        String statKey = stat.toLowerCase(Locale.ROOT);
        if (!Set.of("min","max","sum","avg").contains(statKey)) {
            return ResponseEntity.badRequest().body(Map.of("error", "stat must be one of min,max,sum,avg"));
        }

        final Set<Long> sensorSet;
        try {
            sensorSet = parseLongCsv(sensors);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }

        Set<String> metricSet = parseLowerCsv(metrics);
        if (metricSet != null && !ALLOWED_METRICS.containsAll(metricSet)) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "metrics must be within " + ALLOWED_METRICS)
            );
        }


        var rows = repo.findByTimestampBetween(start, end).stream()
                .filter(r -> sensorSet == null || sensorSet.contains(r.getSensorId()))
                .filter(r -> metricSet == null || metricSet.contains(r.getMetricType()))
                .toList();

        // group by (sensorId, metricType) and compute selected stat
        record Key(Long sensorId, String metricType) {}
        var grouped = rows.stream().collect(Collectors.groupingBy(
                r -> new Key(r.getSensorId(), r.getMetricType()),
                Collectors.summarizingDouble(SensorMetric::getMetricValue)
        ));

        var results = grouped.entrySet().stream().map(e -> {
                    var s = e.getValue();
                    double value = switch (statKey) {
                        case "min" -> s.getMin();
                        case "max" -> s.getMax();
                        case "sum" -> s.getSum();
                        default     -> s.getAverage(); // "avg"
                    };
                    return Map.<String,Object>of(
                            "sensorId", e.getKey().sensorId(),
                            "metricType", e.getKey().metricType(),
                            "value", value,
                            "count", s.getCount()
                    );
                }).sorted(Comparator
                        .comparing((Map<String,Object> m) -> (Long)m.get("sensorId"))
                        .thenComparing(m -> (String)m.get("metricType")))
                .toList();

        return ResponseEntity.ok(Map.of(
                "window", Map.of("start", start, "end", end),
                "stat", statKey,
                "results", results
        ));
    }

    private static Set<String> parseLowerCsv(String csv) {
        if (csv == null || csv.isBlank()) return null;
        return Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private static Set<Long> parseLongCsv(String csv) {
        if (csv == null || csv.isBlank()) return null;
        try {
            return Arrays.stream(csv.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(Long::valueOf)
                    .collect(Collectors.toSet());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("sensors must be comma-separated integers");
        }
    }

}
