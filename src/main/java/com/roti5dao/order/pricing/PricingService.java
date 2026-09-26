package com.roti5dao.order.pricing;

import java.util.List;
import org.springframework.stereotype.Service;

/** ใช้ทั้งตอน quote และ create → ราคาที่ FE แสดงตรงกับที่บันทึกเสมอ (BE คำนวณใหม่ทุกครั้ง ไม่เชื่อราคาจาก FE) */
@Service
public class PricingService {

    private final List<PricingStep> steps;

    /** Spring inject ตามลำดับ @Order */
    public PricingService(List<PricingStep> steps) {
        this.steps = steps;
    }

    public PricingContext calculate(PricingContext ctx) {
        for (PricingStep step : steps) {
            step.apply(ctx);
        }
        return ctx;
    }
}
