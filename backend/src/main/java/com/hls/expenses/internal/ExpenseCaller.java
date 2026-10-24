package com.hls.expenses.internal;

import com.hls.identity.user.Role;
import java.util.Set;
import java.util.UUID;

/** The person doing something: their user id and roles, taken from the access token only. */
public record ExpenseCaller(UUID userId, Set<Role> roles) {}
