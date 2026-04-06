# PulseOps frontend

React and TypeScript single-page application built with Vite.

From this directory:

```powershell
npm.cmd ci
npm.cmd run dev
```

Quality checks:

```powershell
npm.cmd run lint
npm.cmd test
npm.cmd run build
```

Runtime API calls should use relative `/api/v1` URLs. The production Nginx
container proxies `/api/` to the backend, while Vite proxy configuration will
support local non-container development when API features are introduced.
