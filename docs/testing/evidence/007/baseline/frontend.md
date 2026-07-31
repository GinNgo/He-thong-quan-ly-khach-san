# Feature 007 Baseline - Frontend

## Commands

```powershell
Set-Location frontend
npm test -- --watch=false
npm run build
npx playwright test --list
```

## Results

- `npm test -- --watch=false`: timed out after 600 seconds without a result; no pass/fail count was claimed.
- `npm run build`: passed; initial bundle 1.10 MB raw and 205.18 kB estimated transfer, with 59 lazy chunks reported. CommonJS optimization warnings were emitted for `@stomp/stompjs` and `sockjs-client`.
- `npx playwright test --list`: 86 tests in 17 files discovered.
- Node 26.2.0 and npm version is recorded by the environment command used for this baseline.

No frontend source was changed to influence these measurements.
