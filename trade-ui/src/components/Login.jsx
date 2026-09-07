import { useState } from 'react'
import api from '../api/api'

export default function Login({ onLoggedIn }) {
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  async function submit(event) {
    event.preventDefault()
    if (submitting) return
    const form = event.currentTarget
    const data = Object.fromEntries(new FormData(form))
    setError('')
    setSubmitting(true)
    try {
      await api.auth.login(data)
      form.reset()
      await onLoggedIn()
    } catch (failure) { setError(failure.message || 'Login failed. Please try again.') }
    finally { setSubmitting(false) }
  }
  return (
    <section className="registration" aria-labelledby="login-heading">
      <div className="section-header"><div><h2 id="login-heading">Login</h2><p>Sign in to view your portfolio.</p></div></div>
      <form className="form-card" onSubmit={submit}>
        {error && <div className="error" role="alert">{error}</div>}
        <fieldset disabled={submitting}>
          <div className="registration-fields">
            <label>Username<input name="username" autoComplete="username" required maxLength={50} /></label>
            <label>Password<input name="password" type="password" autoComplete="current-password" required /></label>
          </div>
          <div className="form-actions"><button className="primary" type="submit">{submitting ? 'Signing in…' : 'Login'}</button></div>
        </fieldset>
      </form>
    </section>
  )
}
