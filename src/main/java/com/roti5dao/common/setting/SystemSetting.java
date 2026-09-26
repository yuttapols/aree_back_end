package com.roti5dao.common.setting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "system_setting")
public class SystemSetting {

    @Id
    @Column(name = "setting_key", length = 100)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 1000)
    private String settingValue;

    @Column(name = "value_type", nullable = false, length = 20)
    private String valueType;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;
}
