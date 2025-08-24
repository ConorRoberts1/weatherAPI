package com.conor.weather.api.controller;

import com.conor.weather.api.model.SensorMetric;
import com.conor.weather.api.repository.SensorMetricRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

import java.util.*;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/metrics")
public class SensorMetricController {

    private final SensorMetricRepository repo;

    public SensorMetricController(SensorMetricRepository repo) {
        this.repo = repo;
    }

    @PostMapping
    public ResponseEntity<SensorMetric> create(@Valid @RequestBody SensorMetric metric) {
        // defaults + normalization
        if (metric.getTimestamp() == null) {
            metric.setTimestamp(LocalDateTime.now());
        }
        if (metric.getMetricType() != null) {
            metric.setMetricType(metric.getMetricType().trim().toLowerCase());
        }
        SensorMetric saved = repo.save(metric);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public List<SensorMetric> all() {
        return repo.findAll();
    }


    @GetMapping("/query")
    public List<SensorMetric> query(
            @RequestParam Long sensorId,
            @RequestParam String metricType,
            @RequestParam LocalDateTime start,
            @RequestParam LocalDateTime end
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
            @RequestParam(required = false) String sensors,   // "1,2,3" or omit = all
            @RequestParam(required = false) String metrics,   // "temperature,humidity" or omit = all
            @RequestParam(required = false, defaultValue = "avg") String stat, // min|max|sum|avg
            @RequestParam(required = false) LocalDateTime start,
            @RequestParam(required = false) LocalDateTime end
    ) {
        // default window: last 24h
        var now = LocalDateTime.now();
        if (start == null || end == null) { end = now; start = now.minusDays(1); }

        String statKey = stat.toLowerCase(Locale.ROOT);
        if (!Set.of("min","max","sum","avg").contains(statKey)) {
            return ResponseEntity.badRequest().body(Map.of("error", "stat must be one of min,max,sum,avg"));
        }

        Set<Long> sensorSet  = parseLongCsv(sensors);
        Set<String> metricSet = parseLowerCsv(metrics);

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
