package com.hls.substitution.api;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The live substitute credits, for the payout reconciliation of spec 017b and for reports. Only a day with an assigned
 * substitute is a credit: a cancelled absence, a released requirement and a requirement the Manager covers are not.
 * Server-side only; nothing here is exposed over HTTP.
 */
public interface SubstituteCredits {

    /** The Teacher's credits for days in the month, oldest date first. */
    List<SubstituteCredit> creditsOf(UUID substituteTeacherId, YearMonth month);

    /** Bulk form for {@code from..to} inclusive; a Teacher with none is absent from the map. A fixed number of queries. */
    Map<UUID, List<SubstituteCredit>> creditsOf(Collection<UUID> substituteTeacherIds, LocalDate from, LocalDate to);
}
