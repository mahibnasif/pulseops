import { Link } from 'react-router-dom'
import { PublicHeader } from '../components/layout/PublicHeader'

export function LandingPage() {
  return (
    <div className="public-shell">
      <PublicHeader />
      <main className="hero">
        <div className="hero-copy">
          <p className="eyebrow">Monitoring and incident response</p>
          <h1>Know when services fail. Act before users notice.</h1>
          <p className="hero-summary">
            PulseOps gives engineering teams one place to monitor services,
            detect outages, and coordinate incident response.
          </p>
          <div className="hero-actions">
            <Link className="button" to="/register">
              Start monitoring
            </Link>
            <Link className="text-link" to="/login">
              Sign in to your workspace
            </Link>
          </div>
        </div>
        <aside className="signal-card" aria-label="Example service status">
          <div className="signal-card-heading">
            <div>
              <p className="signal-label">Production API</p>
              <p className="signal-url">api.example.com/health</p>
            </div>
            <span className="status-pill status-operational">Operational</span>
          </div>
          <dl className="signal-metrics">
            <div>
              <dt>Uptime</dt>
              <dd>99.98%</dd>
            </div>
            <div>
              <dt>Response</dt>
              <dd>184 ms</dd>
            </div>
            <div>
              <dt>Incidents</dt>
              <dd>0 active</dd>
            </div>
          </dl>
          <div className="pulse-line" aria-hidden="true">
            <span />
            <span />
            <span />
            <span />
            <span />
            <span />
          </div>
        </aside>
      </main>
    </div>
  )
}
