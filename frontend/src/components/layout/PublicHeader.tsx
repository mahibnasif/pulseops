import { Link, NavLink } from 'react-router-dom'

export function PublicHeader() {
  return (
    <header className="public-header">
      <Link className="brand" to="/" aria-label="PulseOps home">
        <span className="brand-mark" aria-hidden="true">
          P
        </span>
        PulseOps
      </Link>
      <nav aria-label="Primary navigation">
        <NavLink to="/login">Sign in</NavLink>
        <NavLink className="button button-small" to="/register">
          Create account
        </NavLink>
      </nav>
    </header>
  )
}
