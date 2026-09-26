package com.roti5dao.order.pricing;

import com.roti5dao.catalog.dto.OrderableProduct;
import com.roti5dao.catalog.service.ProductQueryService;
import com.roti5dao.common.config.AppProperties;
import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.util.MoneyUtils;
import com.roti5dao.order.pricing.PricingContext.CartLine;
import com.roti5dao.order.pricing.PricingContext.PricedLine;
import com.roti5dao.order.pricing.PricingContext.PricedOption;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** ราคาสินค้า + ตัวเลือก จาก DB (ไม่ใช้ราคาจาก client) และตรวจว่าสินค้า/ตัวเลือกพร้อมขาย */
@Component
@Order(PricingStep.SUBTOTAL)
public class SubtotalStep implements PricingStep {

    private final ProductQueryService productQueryService;
    private final AppProperties.OrderLimits limits;

    public SubtotalStep(ProductQueryService productQueryService, AppProperties props) {
        this.productQueryService = productQueryService;
        this.limits = props.order();
    }

    @Override
    public void apply(PricingContext ctx) {
        List<CartLine> cart = ctx.getCart();
        if (cart.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ตะกร้าว่าง");
        }
        if (cart.size() > limits.maxItemsPerOrder()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "จำนวนรายการในออเดอร์มากเกินไป");
        }
        Set<Long> ids = cart.stream().map(CartLine::productId).collect(Collectors.toSet());
        Map<Long, OrderableProduct> products = productQueryService.getForOrder(ids);

        List<PricedLine> lines = new ArrayList<>();
        BigDecimal subtotal = MoneyUtils.ZERO;
        for (CartLine c : cart) {
            if (c.quantity() < 1 || c.quantity() > limits.maxQuantityPerItem()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "จำนวนสินค้าไม่ถูกต้อง");
            }
            OrderableProduct p = products.get(c.productId());
            if (p == null || !p.orderable()) {
                throw new BusinessException(ErrorCode.PRODUCT_UNAVAILABLE,
                        p == null ? ErrorCode.PRODUCT_UNAVAILABLE.defaultMessage() : "สินค้า \"" + p.name() + "\" ไม่พร้อมขาย");
            }
            PricedLine line = new PricedLine(p.id(), p.categoryId(), p.name(), p.price(),
                    resolveOptions(p, c.optionItemIds() == null ? List.of() : c.optionItemIds()), c.quantity(),
                    c.note() == null || c.note().isBlank() ? null : c.note().trim());
            lines.add(line);
            subtotal = subtotal.add(line.lineTotal());
        }
        ctx.setLines(lines);
        ctx.setSubtotal(MoneyUtils.scale(subtotal));
    }

    private static List<PricedOption> resolveOptions(OrderableProduct p, List<Long> optionIds) {
        if (new HashSet<>(optionIds).size() != optionIds.size()) {
            throw new BusinessException(ErrorCode.OPTION_INVALID, "เลือกตัวเลือกซ้ำ");
        }
        Map<Long, Integer> countByGroup = new HashMap<>();
        List<PricedOption> result = new ArrayList<>();
        for (Long optionId : optionIds) {
            OrderableProduct.Group group = null;
            OrderableProduct.Option option = null;
            for (OrderableProduct.Group g : p.optionGroups()) {
                OrderableProduct.Option o = g.options().get(optionId);
                if (o != null) {
                    group = g;
                    option = o;
                    break;
                }
            }
            if (option == null) {
                throw new BusinessException(ErrorCode.OPTION_INVALID, "ตัวเลือกไม่ตรงกับสินค้า \"" + p.name() + "\"");
            }
            if (!option.available()) {
                throw new BusinessException(ErrorCode.OPTION_INVALID, "ตัวเลือก \"" + option.name() + "\" หมด");
            }
            countByGroup.merge(group.id(), 1, Integer::sum);
            result.add(new PricedOption(option.id(), group.name(), option.name(), option.extraPrice()));
        }
        for (OrderableProduct.Group g : p.optionGroups()) {
            int n = countByGroup.getOrDefault(g.id(), 0);
            if (n < g.minSelect() || n > g.maxSelect()) {
                throw new BusinessException(ErrorCode.OPTION_INVALID,
                        "\"" + g.name() + "\" ต้องเลือก " + g.minSelect() + "–" + g.maxSelect() + " รายการ");
            }
        }
        return result;
    }
}
