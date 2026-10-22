package com.lexor.admin.repository;

import com.lexor.admin.domain.entity.AdminDashboardSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminDashboardSettingRepository extends JpaRepository<AdminDashboardSetting, Long> {

    Optional<AdminDashboardSetting> findBySettingKey(String settingKey);
}
