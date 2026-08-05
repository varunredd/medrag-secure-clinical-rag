package com.medrag.api.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSettingRepository extends JpaRepository<TenantSetting, String> {}
