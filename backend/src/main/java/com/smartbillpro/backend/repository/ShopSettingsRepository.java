package com.smartbillpro.backend.repository;

import com.smartbillpro.backend.entity.ShopSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopSettingsRepository extends JpaRepository<ShopSettings, Long> {
}
