/**
 * The Manager part of the {@code leave} module (spec 032): a Manager's leave requests and the Director's or Admin's
 * decisions. It lives in {@code leave} because {@code attendance} must not depend on {@code leave}; approved days reach
 * attendance through {@code com.hls.attendance.api.ManagerLeaveAttendance}.
 */
package com.hls.leave.manager;
