package com.roti5dao.catalog.service;

import com.roti5dao.catalog.dto.CatalogDtos.OptionGroupResponse;
import com.roti5dao.catalog.dto.CatalogDtos.OptionGroupUpsertRequest;
import com.roti5dao.catalog.dto.CatalogDtos.OptionItemUpsert;
import com.roti5dao.catalog.entity.OptionGroup;
import com.roti5dao.catalog.entity.OptionItem;
import com.roti5dao.catalog.repository.OptionGroupRepository;
import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.exception.NotFoundException;
import com.roti5dao.common.util.MoneyUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OptionGroupAdminService {

    private final OptionGroupRepository repository;

    public OptionGroupAdminService(OptionGroupRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<OptionGroupResponse> list() {
        return repository.findAllWithItems().stream().map(OptionGroupResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public OptionGroupResponse get(Long id) {
        return OptionGroupResponse.of(group(id));
    }

    @Transactional
    public OptionGroupResponse create(OptionGroupUpsertRequest req) {
        OptionGroup g = new OptionGroup();
        apply(g, req);
        return OptionGroupResponse.of(repository.saveAndFlush(g));
    }

    @Transactional
    public OptionGroupResponse update(Long id, OptionGroupUpsertRequest req) {
        OptionGroup g = group(id);
        apply(g, req);
        return OptionGroupResponse.of(repository.saveAndFlush(g));
    }

    /** soft delete — ปิดใช้งานกลุ่ม (สินค้าที่ผูกไว้จะไม่แสดงกลุ่มนี้) */
    @Transactional
    public void delete(Long id) {
        group(id).setActive(false);
    }

    private OptionGroup group(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("กลุ่มตัวเลือก"));
    }

    /** items แบบ nested: มี id = แก้ไข, ไม่มี id = เพิ่มใหม่, ที่ไม่ส่งมา = ลบ (ออเดอร์เก่าเก็บ snapshot ไว้แล้ว) */
    private static void apply(OptionGroup g, OptionGroupUpsertRequest req) {
        if (req.maxSelect() < req.minSelect()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "maxSelect ต้องไม่น้อยกว่า minSelect");
        }
        g.setName(req.name().trim());
        g.setMinSelect(req.minSelect());
        g.setMaxSelect(req.maxSelect());
        g.setActive(req.active());
        g.setSortOrder(req.sortOrder());

        Map<Long, OptionItem> existing = g.getItems().stream()
                .filter(i -> i.getId() != null)
                .collect(Collectors.toMap(OptionItem::getId, Function.identity()));
        Set<Long> keep = new HashSet<>();
        for (OptionItemUpsert u : req.items()) {
            OptionItem item;
            if (u.id() != null) {
                item = existing.get(u.id());
                if (item == null) {
                    throw new NotFoundException("ตัวเลือก id=" + u.id());
                }
                keep.add(u.id());
            } else {
                item = new OptionItem();
                item.setGroup(g);
                g.getItems().add(item);
            }
            item.setName(u.name().trim());
            item.setExtraPrice(MoneyUtils.scale(u.extraPrice()));
            item.setAvailable(u.available());
            item.setSortOrder(u.sortOrder());
        }
        g.getItems().removeIf(i -> i.getId() != null && !keep.contains(i.getId()));
    }
}
