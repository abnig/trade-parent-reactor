import { useState } from 'react'
import api from '../api/api'

export default function Register() {
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [registered, setRegistered] = useState(false)

  async function submit(event) {
    event.preventDefault()
    if (submitting) return
    const form = event.currentTarget
    const data = Object.fromEntries(new FormData(form))
    setError('')
    if (data.password !== data.confirmPassword) {
      setError('Passwords do not match.')
      return
    }
    if (new TextEncoder().encode(data.password).length > 72) {
      setError('Password must not exceed 72 UTF-8 bytes.')
      return
    }
    delete data.confirmPassword
    setSubmitting(true)
    try {
      await api.auth.register(data)
      form.reset()
      setRegistered(true)
    } catch (failure) {
      setError(failure.message || 'Registration failed. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="registration" aria-labelledby="register-heading">
      <div className="section-header">
        <div><h2 id="register-heading">Register</h2><p>Create your Trade Management account.</p></div>
      </div>
      {registered ? (
        <div className="form-card" role="status">
          <h3>Account created</h3>
          <p>Your registration was successful.</p>
          <a href="/login">Continue to login</a>
        </div>
      ) : (
        <form className="form-card" onSubmit={submit}>
          {error && <div className="error" role="alert">{error}</div>}
          <fieldset disabled={submitting}>
            <div className="registration-fields">
              <label>Username<input name="username" autoComplete="username" required maxLength={50} pattern="[A-Za-z0-9_.\-]+" title="Use letters, digits, underscores, dots, or hyphens." /></label>
              <label>Email<input name="email" type="email" autoComplete="email" required maxLength={100} /></label>
              <label>Password<input name="password" type="password" autoComplete="new-password" required minLength={12} maxLength={72} aria-describedby="password-help" /></label>
              <p id="password-help" className="field-help">Use 12–72 characters (at most 72 UTF-8 bytes).</p>
              <label>Confirm password<input name="confirmPassword" type="password" autoComplete="new-password" required minLength={12} maxLength={72} /></label>
              <label>First name (optional)<input name="firstName" autoComplete="given-name" maxLength={50} /></label>
              <label>Last name (optional)<input name="lastName" autoComplete="family-name" maxLength={50} /></label>
              <label>Phone number (optional)<input name="phoneNumber" type="tel" autoComplete="tel" maxLength={20} /></label>
            </div>
            <div className="form-actions"><button className="primary" type="submit">{submitting ? 'Creating account…' : 'Create account'}</button></div>
          </fieldset>
        </form>
      )}
    </section>
  )
}
