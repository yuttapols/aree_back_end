package com.roti5dao.point.service;

import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** ตัดแต้มหมดอายุทุกวัน 00:05 (เวลาไทย) — ShedLock กันรันซ้ำเมื่อมีหลาย instance */
@Component
public class PointExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(PointExpiryJob.class);
    private static final int BATCH = 200;

    private final PointService pointService;

    public PointExpiryJob(PointService pointService) {
        this.pointService = pointService;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Bangkok")
    @SchedulerLock(name = "pointExpiryJob", lockAtMostFor = "PT30M")
    public void run() {
        int customers = 0;
        int points = 0;
        List<Long> batch;
        do {
            batch = pointService.customersWithExpiredLots(BATCH);
            for (Long customerId : batch) {
                // transaction แยกต่อคน — คนหนึ่งล้มเหลวไม่กระทบคนอื่น
                try {
                    points += pointService.expireForCustomer(customerId);
                    customers++;
                } catch (RuntimeException e) {
                    log.error("Point expiry failed for customer {}", customerId, e);
                    return;
                }
            }
        } while (batch.size() == BATCH);
        if (customers > 0) {
            log.info("Expired {} points from {} customers", points, customers);
        }
    }
}
