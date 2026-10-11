/**
 * Public API of the {@code payroll} module (spec 013a): {@link com.hls.payroll.api.PayRules}, the one place the
 * monthly payroll (spec 013b) and reports ask for the salary in effect, the value of a day of loss of pay, the payable
 * days and the unpaid days. Server-side only: nothing here is exposed over HTTP.
 */
@org.springframework.modulith.NamedInterface
package com.hls.payroll.api;
