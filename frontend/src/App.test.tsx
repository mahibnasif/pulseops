import { render, screen } from '@testing-library/react'
import App from './App'

describe('App', () => {
  it('identifies PulseOps and the current foundation milestone', () => {
    render(<App />)

    expect(
      screen.getByRole('heading', {
        name: /service monitoring you can act on/i,
      }),
    ).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent(
      'Phase 1 · Project foundation',
    )
  })
})
