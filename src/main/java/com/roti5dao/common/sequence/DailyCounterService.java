package com.roti5dao.common.sequence;

import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * running number ต่อวัน (order_no, queue_no) — ใช้ UPSERT ... RETURNING ซึ่ง atomic ใน PostgreSQL
 * จึงไม่ได้เลขซ้ำแม้สั่งพร้อมกันหลาย request
 */
@Service
public class DailyCounterService {

    public static final String ORDER = "ORDER";
    public static final String QUEUE = "QUEUE";

    private final JdbcClient jdbc;

    public DailyCounterService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int next(LocalDate date, String counterName) {
        return jdbc.sql("""
                        INSERT INTO daily_counter (counter_date, counter_name, last_value)
                        VALUES (:date, :name, 1)
                        ON CONFLICT (counter_date, counter_name)
                        DO UPDATE SET last_value = daily_counter.last_value + 1
                        RETURNING last_value
                        """)
                .param("date", date)
                .param("name", counterName)
                .query(Integer.class)
                .single();
    }
}
