# Contract: `substitution.api.SubstituteCredits` (server-side only, for 017b)

```java
record SubstituteCredit(UUID requirementId, UUID substituteTeacherId, UUID absentTeacherId, UUID schoolId,
                        LocalDate date, BigDecimal dayValue) {}
interface SubstituteCredits {
  List<SubstituteCredit> creditsOf(UUID substituteTeacherId, YearMonth month);          // oldest date first
  Map<UUID, List<SubstituteCredit>> creditsOf(Collection<UUID> substituteTeacherIds, LocalDate from, LocalDate to); // fixed query count
}
```

Only live credits: status ASSIGNED. The absent Teacher never has a credit through the absence. The day value is 0.50 or 1.00.

# Contract: `attendance.api` additions (008 A2)

```java
record TeacherAbsenceRecorded(UUID teacherId, UUID schoolId, LocalDate date, BigDecimal dayValue, String kind /*ABSENT|LEAVE*/,
                              boolean lossOfPay, UUID actorUserId) {}
record TeacherAbsenceWithdrawn(UUID teacherId, LocalDate date, UUID actorUserId) {}
record LossOfPayDay(LocalDate date, BigDecimal dayValue, UUID schoolId) {}
interface LossOfPayDays {
  List<LossOfPayDay> lossOfPayDaysOf(UUID teacherId, YearMonth month);
  Map<UUID, List<LossOfPayDay>> lossOfPayDaysOf(Collection<UUID> teacherIds, LocalDate from, LocalDate to);
}
```

`TeacherAbsenceRecorded` is published on every write that leaves a leave-category mark (consumers must be idempotent).
`TeacherAbsenceWithdrawn` when such a mark is cleared or becomes another category.
