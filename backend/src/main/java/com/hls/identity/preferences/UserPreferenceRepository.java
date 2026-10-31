package com.hls.identity.preferences;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserPreferenceRepository extends JpaRepository<UserPreference, UUID> {}
