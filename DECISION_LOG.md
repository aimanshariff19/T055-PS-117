# Decision Log

- The booking window rule is interpreted as earliest next working day + 1, with holidays and weekends excluded.
- Allocation precedence is document validation, category, booking window, quota availability, released pool, preferred time, earliest eligible slot.
- Release quotas are idempotent and are never re-released.
