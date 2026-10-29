/**
 * The Margin, Collections and School reports (spec 014b). They join billing, payroll, attendance, substitution, performance and
 * the prospect pipeline through those modules' published interfaces, so they live in a module of their own that nothing depends
 * on; {@code reporting} stays free of source modules (spec 014a).
 */
package com.hls.schoolreports.internal;
