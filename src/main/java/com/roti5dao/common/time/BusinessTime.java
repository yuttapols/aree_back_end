package com.roti5dao.common.time;

import com.roti5dao.common.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * เวลาตามเขตเวลาของร้าน (app.timezone) — รวมการคำนวณ "วันนี้" / ช่วงวัน ไว้ที่เดียว
 * ใช้แทน {@code LocalDate.now(clock.withZone(zone))} และ {@code date.atStartOfDay(zone)} ที่กระจายอยู่หลาย service
 */
@Component
public class BusinessTime {

    private final Clock clock;
    private final ZoneId zone;

    public BusinessTime(Clock clock, AppProperties props) {
        this.clock = clock;
        this.zone = props.timezone();
    }

    public ZoneId zone() {
        return zone;
    }

    public Instant now() {
        return Instant.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    public LocalTime timeNow() {
        return LocalTime.now(clock.withZone(zone));
    }

    /** เวลาเริ่มวัน (00:00 ตามเขตเวลาร้าน) */
    public Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(zone).toInstant();
    }

    /** เวลาเริ่มวันถัดไป — ใช้เป็นขอบบนแบบ exclusive ({@code >= startOfDay AND < endOfDay}) */
    public Instant endOfDay(LocalDate date) {
        return startOfDay(date.plusDays(1));
    }
}
