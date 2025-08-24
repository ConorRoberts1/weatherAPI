package com.conor.weather;

import com.conor.weather.api.repository.SensorMetricRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WeatherApiApplicationTests {

    @Autowired MockMvc mvc;
    @Autowired SensorMetricRepository repo;

    @BeforeEach
    void cleanDb() {
        repo.deleteAll();
    }

    // Verifies POST creates a reading, lowercases metricType, and auto-fills timestamp when missing.
    @Test
    void post_creates_reading_normalizes_metric_and_defaults_timestamp() throws Exception {
        String body = """
          {"sensorId":1,"metricType":"Temperature","metricValue":21.3}
        """;

        mvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.metricType", is("temperature")))
                .andExpect(jsonPath("$.timestamp", not(blankOrNullString())));
    }

    // Ensures /query returns only the requested sensor+metric within the provided time window.
    @Test
    void query_returns_only_requested_sensor_metric_in_window() throws Exception {
        postReading(1, "temperature", 21.3, LocalDateTime.now().minusHours(3));
        postReading(1, "temperature", 19.7, LocalDateTime.now().minusHours(2));
        postReading(1, "humidity",     0.55, LocalDateTime.now().minusHours(2));

        var start = LocalDateTime.now().minusDays(1).toString();
        var end   = LocalDateTime.now().plusDays(1).toString();

        mvc.perform(get("/api/metrics/query")
                        .param("sensorId", "1")
                        .param("metricType", "temperature")
                        .param("start", start)
                        .param("end", end))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].metricType", everyItem(is("temperature"))));
    }

    // Checks /stats computes the average for sensor 1 temperature over the default last 24h window.
    @Test
    void stats_avg_for_sensor1_temperature_last_24h() throws Exception {
        postReading(1, "temperature", 21.3, LocalDateTime.now().minusHours(1));
        postReading(1, "temperature", 19.7, LocalDateTime.now().minusHours(2));

        mvc.perform(get("/api/metrics/stats")
                        .param("sensors", "1")
                        .param("metrics", "temperature")
                        .param("stat", "avg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stat", is("avg")))
                .andExpect(jsonPath("$.results[0].sensorId", is(1)))
                .andExpect(jsonPath("$.results[0].metricType", is("temperature")))
                .andExpect(jsonPath("$.results[0].count", is(2)))
                .andExpect(jsonPath("$.results[0].value",
                        closeTo((21.3 + 19.7) / 2, 1e-6)));
    }

    // Validates that /stats rejects an unsupported stat with HTTP 400.
    @Test
    void stats_invalid_stat_returns_400() throws Exception {
        mvc.perform(get("/api/metrics/stats").param("stat","median"))
                .andExpect(status().isBadRequest());
    }

    // Computes MIN temperature per sensor for "today" (exact 24h window).
    @Test
    void stats_min_temperature_today_per_sensor() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        postReading(1, "temperature", 21.3, now.minusHours(3));
        postReading(1, "temperature", 19.7, now.minusHours(2));
        postReading(2, "temperature", 25.0, now.minusHours(1));

        String start = now.toLocalDate().atStartOfDay().toString();
        String end   = now.toLocalDate().plusDays(1).atStartOfDay().toString();

        mvc.perform(get("/api/metrics/stats")
                        .param("metrics", "temperature")
                        .param("stat", "min")
                        .param("start", start)
                        .param("end", end))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stat", is("min")))
                .andExpect(jsonPath("$.results", hasSize(2)))

                // First row: sensorId 1, min 19.7 (2 readings)
                .andExpect(jsonPath("$.results[0].sensorId", is(1)))
                .andExpect(jsonPath("$.results[0].metricType", is("temperature")))
                .andExpect(jsonPath("$.results[0].count", is(2)))
                .andExpect(jsonPath("$.results[0].value", closeTo(19.7, 1e-6)))

                // Second row: sensorId 2, min 25.0 (1 reading)
                .andExpect(jsonPath("$.results[1].sensorId", is(2)))
                .andExpect(jsonPath("$.results[1].metricType", is("temperature")))
                .andExpect(jsonPath("$.results[1].count", is(1)))
                .andExpect(jsonPath("$.results[1].value", closeTo(25.0, 1e-6)));
    }


    // Computes MAX temperature per sensor for "today" (exact 24h window).
    @Test
    void stats_max_temperature_today_per_sensor() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        postReading(1, "temperature", 21.3, now.minusHours(3));
        postReading(1, "temperature", 19.7, now.minusHours(2));
        postReading(2, "temperature", 25.0, now.minusHours(1));

        String start = now.toLocalDate().atStartOfDay().toString();
        String end   = now.toLocalDate().plusDays(1).atStartOfDay().toString();

        mvc.perform(get("/api/metrics/stats")
                        .param("metrics", "temperature")
                        .param("stat", "max")
                        .param("start", start)
                        .param("end", end))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stat", is("max")))
                .andExpect(jsonPath("$.results", hasSize(2)))

                // First row: sensorId 1, max 21.3
                .andExpect(jsonPath("$.results[0].sensorId", is(1)))
                .andExpect(jsonPath("$.results[0].metricType", is("temperature")))
                .andExpect(jsonPath("$.results[0].value", closeTo(21.3, 1e-6)))

                // Second row: sensorId 2, max 25.0
                .andExpect(jsonPath("$.results[1].sensorId", is(2)))
                .andExpect(jsonPath("$.results[1].metricType", is("temperature")))
                .andExpect(jsonPath("$.results[1].value", closeTo(25.0, 1e-6)));
    }


    // Computes SUM humidity for sensor 1 "today" (exact 24h window).
    @Test
    void stats_sum_humidity_today_sensor1() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        postReading(1, "humidity", 0.55, now.minusHours(2));
        postReading(1, "humidity", 0.60, now.minusHours(1));

        String start = now.toLocalDate().atStartOfDay().toString();
        String end   = now.toLocalDate().plusDays(1).atStartOfDay().toString();

        mvc.perform(get("/api/metrics/stats")
                        .param("sensors", "1")
                        .param("metrics", "humidity")
                        .param("stat", "sum")
                        .param("start", start)
                        .param("end", end))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stat", is("sum")))
                .andExpect(jsonPath("$.results[0].count", is(2)))
                .andExpect(jsonPath("$.results[0].value", closeTo(0.55 + 0.60, 1e-6)));
    }

    // Default window (no start/end): only last 24h is included; older data is ignored.
    @Test
    void stats_default_window_last24h_only() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        postReading(1, "temperature", 10.0, now.minusDays(2)); // outside window
        postReading(1, "temperature", 20.0, now.minusHours(1)); // inside window

        mvc.perform(get("/api/metrics/stats")
                        .param("sensors", "1")
                        .param("metrics", "temperature")
                        .param("stat", "sum"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.window.start", not(blankOrNullString())))
                .andExpect(jsonPath("$.window.end", not(blankOrNullString())))
                .andExpect(jsonPath("$.results[0].count", is(1)))
                .andExpect(jsonPath("$.results[0].value", closeTo(20.0, 1e-6)));
    }

    // Invalid date range: window shorter than 1 day should return 400.
    @Test
    void stats_rejects_window_shorter_than_one_day() throws Exception {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(12); // < 24h

        mvc.perform(get("/api/metrics/stats")
                        .param("metrics", "temperature")
                        .param("stat", "avg")
                        .param("start", start.toString())
                        .param("end", end.toString()))
                .andExpect(status().isBadRequest());
    }

    // ---- helper ----
    private void postReading(long sensorId, String type, double value, LocalDateTime ts) throws Exception {
        String body = """
          {"sensorId":%d,"metricType":"%s","metricValue":%s,"timestamp":"%s"}
        """.formatted(sensorId, type, value, ts.toString());

        mvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
