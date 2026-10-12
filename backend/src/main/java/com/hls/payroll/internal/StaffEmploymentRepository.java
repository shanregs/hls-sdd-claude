package com.hls.payroll.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffEmploymentRepository extends JpaRepository<StaffEmployment, UUID> {}
