package com.hls.settings.internal;

import org.springframework.data.jpa.repository.JpaRepository;

interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {}
