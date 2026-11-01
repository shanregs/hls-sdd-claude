package com.hls.substitution.payout;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DayEventRepository extends JpaRepository<DayEvent, UUID> {

    List<DayEvent> findByDayIdOrderByAtAscIdAsc(UUID dayId);
}
