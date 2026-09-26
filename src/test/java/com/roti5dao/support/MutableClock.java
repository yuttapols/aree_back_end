package com.roti5dao.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Clock ที่เลื่อนเวลาได้ — ใช้ทดสอบ token หมดอายุ / แต้มหมดอายุ / โปรตามวัน */
public class MutableClock extends Clock {

    private volatile Instant now;

    public MutableClock(Instant start) {
        this.now = start;
    }

    public void set(Instant instant) {
        this.now = instant;
    }

    public void advance(Duration d) {
        this.now = now.plus(d);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        MutableClock self = this;
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return zone;
            }

            @Override
            public Clock withZone(ZoneId z) {
                return self.withZone(z);
            }

            @Override
            public Instant instant() {
                return self.instant();
            }
        };
    }

    @Override
    public Instant instant() {
        return now;
    }
}
