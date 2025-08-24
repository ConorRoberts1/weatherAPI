package com.conor.weather.api.repository;

import com.conor.weather.api.model.SensorMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SensorMetricRepository extends JpaRepository<SensorMetric, Long> {
    // Used later for filtered queries/aggregates
    List<SensorMetric> findBySensorIdAndMetricTypeAndTimestampBetween(
            Long sensorId, String metricType, LocalDateTime start, LocalDateTime end
    );

    List<SensorMetric> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
}
