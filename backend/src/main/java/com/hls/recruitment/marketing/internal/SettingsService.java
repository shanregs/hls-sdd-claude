package com.hls.recruitment.marketing.internal;

import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.StaleVersion;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The one marketing setting: how many days a won prospect may wait for its MoU before it is flagged. */
@Service
public class SettingsService {

    public static final int DEFAULT_OVERDUE_DAYS = 14;

    public record SettingsDto(int mouOverdueDays, Long version) {}

    public record SettingsRequest(Integer mouOverdueDays, Long version) {}

    private final MarketingSettingRepository settings;
    private final ChangeRecorder changes;
    private final Clock clock;

    public SettingsService(MarketingSettingRepository settings, ChangeRecorder changes, Clock clock) {
        this.settings = settings;
        this.changes = changes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SettingsDto get() {
        MarketingSetting s = find();
        return new SettingsDto(s.getIntValue() == null ? DEFAULT_OVERDUE_DAYS : s.getIntValue(), s.getVersion());
    }

    @Transactional(readOnly = true)
    public int overdueDays() {
        return get().mouOverdueDays();
    }

    @Transactional
    public SettingsDto change(UUID actor, SettingsRequest request) {
        MarketingSetting s = find();
        StaleVersion.check(MarketingSetting.class, s.getKey(), s.getVersion(), request.version());
        Integer days = request.mouOverdueDays();
        if (days == null || days < 1 || days > 90) {
            throw new InvalidInputException("The number of days must be between 1 and 90.");
        }
        Integer before = s.getIntValue();
        s.set(days, actor, clock.instant());
        settings.saveAndFlush(s);
        changes.record(actor, "MARKETING_SETTING", MarketingSetting.MOU_OVERDUE_DAYS, "value", before, days);
        return get();
    }

    private MarketingSetting find() {
        return settings.findById(MarketingSetting.MOU_OVERDUE_DAYS).orElseThrow(() -> new NotFoundException("Setting not found."));
    }
}
