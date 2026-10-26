package com.hls.schoolbilling.internal;

import com.hls.organization.api.ScopeQueries;
import com.hls.organization.api.ScopeView;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.schoolbilling.internal.ReceivablesDtos.BillingCaller;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Which Schools a caller may see or act on (Constitution Principle III, spec 022 FR-020), derived on every call. */
@Component
public class BillingScope {

    private final ScopeQueries scopes;
    private final SchoolDirectory schools;

    BillingScope(ScopeQueries scopes, SchoolDirectory schools) {
        this.scopes = scopes;
        this.schools = schools;
    }

    public ScopeView of(BillingCaller caller) {
        return scopes.scopeOf(caller.userId(), caller.roles());
    }

    /** The School, or a 404 when it does not exist or is outside the caller's scope. */
    public SchoolInfo requireSchool(BillingCaller caller, UUID schoolId) {
        SchoolInfo school = schools.school(schoolId).orElseThrow(() -> new NotFoundException("School not found."));
        if (!of(caller).allowsSchool(schoolId)) {
            throw new NotFoundException("School not found.");
        }
        return school;
    }

    /** Every School in scope, active or not. */
    public List<SchoolInfo> schoolsInScope(BillingCaller caller) {
        ScopeView scope = of(caller);
        return schools.allSchools().stream().filter(s -> scope.allowsSchool(s.id())).toList();
    }

    public Set<UUID> schoolIdsInScope(BillingCaller caller) {
        return schoolsInScope(caller).stream().map(SchoolInfo::id).collect(Collectors.toSet());
    }
}
