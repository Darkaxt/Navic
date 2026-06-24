# Artist Credit Resolution POC

This POC emulates the Navic artist-credit pipeline for multi-artist display names.
It renders immediately from a translation cache, resolves unresolved credits in a
separate pass, and writes the updated cache plus a report.

Run the deterministic sample:

```powershell
python tools\artist-credit-poc\artist_credit_resolution_poc.py
```

Run with live Navidrome search contexts from the local Firefox session:

```powershell
python tools\artist-credit-poc\artist_credit_resolution_poc.py --live-navidrome --report tools\artist-credit-poc\work\artist-credit-live-report.json --cache tools\artist-credit-poc\work\artist-credit-live-cache.json
```

Run tests:

```powershell
python tools\artist-credit-poc\test_artist_credit_resolution_poc.py
```

Aurral validation can be layered in with environment variables:

- `AURRAL_BASE_URL`
- `AURRAL_BEARER`, or `AURRAL_USERNAME` + `AURRAL_PASSWORD`

Generated cache and report files are written under `tools/artist-credit-poc/work/`
and are intentionally ignored.
