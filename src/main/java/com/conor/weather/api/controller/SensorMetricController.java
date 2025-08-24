package com.conor.weather.api.controller;

import com.conor.weather.api.model.SensorMetric;
import com.conor.weather.api.repository.SensorMetricRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;


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
}
