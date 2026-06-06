package com.shopee.monolith.modules.user.entity;

import com.shopee.monolith.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "addresses")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Address extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "recipient_name", nullable = false, length = 255)
    private String recipientName;

    @Column(name = "phone", nullable = false, length = 50)
    private String phone;

    @Column(name = "address_line", nullable = false, length = 255)
    private String addressLine;

    @Column(name = "ward_code", nullable = false, length = 100)
    private String wardCode;

    @Column(name = "ward_name", nullable = false, length = 100)
    private String wardName;

    @Column(name = "district_code", nullable = false, length = 100)
    private String districtCode;

    @Column(name = "district_name", nullable = false, length = 100)
    private String districtName;

    @Column(name = "province_code", nullable = false, length = 100)
    private String provinceCode;

    @Column(name = "province_name", nullable = false, length = 100)
    private String provinceName;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    public void update(String recipientName, String phone, String addressLine,
                       String wardCode, String wardName, String districtCode, String districtName,
                       String provinceCode, String provinceName, boolean isDefault) {
        this.recipientName = recipientName;
        this.phone = phone;
        this.addressLine = addressLine;
        this.wardCode = wardCode;
        this.wardName = wardName;
        this.districtCode = districtCode;
        this.districtName = districtName;
        this.provinceCode = provinceCode;
        this.provinceName = provinceName;
        this.isDefault = isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
}
