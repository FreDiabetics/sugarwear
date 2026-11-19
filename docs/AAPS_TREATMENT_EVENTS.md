# AAPS treatment event status extension

Sugarlicious reads an optional `therapyEvents` JSON-array string from the existing
`info.nightscout.androidaps.status` broadcast. The extension is read-only and does not expose or
invoke therapy actions.

Each item contains:

- `id`: stable AAPS treatment identity
- `type`: `SMB`, `MANUAL_CORRECTION`, `MEAL_BOLUS`, `MEAL_CARBS`, or `ECARBS`
- `timestamp`: treatment time in epoch milliseconds
- `amount`: insulin units for boluses or grams for carbs

The AAPS sender must derive the classification from persisted treatment semantics:

- `BS.Type.SMB` becomes `SMB`.
- A normal bolus associated with a Bolus Wizard result that used carbs becomes `MEAL_BOLUS`.
- Other normal boluses become `MANUAL_CORRECTION`.
- A positive carb entry without duration becomes `MEAL_CARBS`.
- A positive carb entry with duration becomes `ECARBS`.

Sugarlicious never infers events from IOB or COB curve changes. Unknown, zero, malformed, and
duplicate events are discarded.
