# Phase 8C Brightness Truthful Reporting Fix

## Purpose

Previous brightness implementations still attempted to convert Android system brightness values into a user-visible slider percentage. On some OEM devices this is not reliable: values such as 67% may be reported as 47%, and other slider positions may be significantly wrong.

## Changes

- `android.get_brightness` no longer reports guessed brightness percentages.
- `percent` is always `null` unless a future implementation can verify a reliable source.
- `percentReliable` is always `false` on the current implementation.
- raw Android settings values are moved into a `diagnostics` object and explicitly marked as not convertible to a user-visible percentage.
- `android.set_brightness` still attempts to write the requested brightness value when WRITE_SETTINGS is available, but returns `requestedPercent` only as the requested value.
- `android.set_brightness` returns `actualPercent=null` and `actualPercentReliable=false` to avoid claiming verification that Android public APIs cannot guarantee on this device.

## Validation

Expected `android.get_brightness` behavior:

```json
{
  "percent": null,
  "percentReliable": false,
  "percentStatus": "unsupported_on_this_device"
}
```

Expected `android.set_brightness` behavior:

```json
{
  "requestedPercent": 67,
  "actualPercent": null,
  "actualPercentReliable": false,
  "verification": "not_available_public_api"
}
```

## Notes

This intentionally sacrifices the brightness percentage readback feature to preserve correctness. The tool should not provide misleading values.
