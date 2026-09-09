import { useState } from 'react'
import api from '../api/api'

export default function ForgotUsername() {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const form = event.currentTarget
    const email = new FormData(form).get('email').trim()
    setError('')
    setBusy(true)
    try {
      const response = await api.auth.forgotUsername({ email })
      form.reset()
      setMessage(response.message)
    } catch (failure) { setError(failure.message || 'Unable to request your username. Please try again.') }
    finally { setBusy(false) }
  }
  return <section className="registration" aria-labelledby="forgot-username-heading">
    <div className="section-header"><div><h2 id="forgot-username-heading">Forgot username</h2>
      <p>Enter your registered email address to receive your username.</p>
    </div></div>
    {message ? <div className="form-card" role="status">{message}</div> : <form className="form-card" onSubmit={submit}>
      {error && <div className="error" role="alert">{error}</div>}
      <fieldset disabled={busy}>
        <div className="registration-fields"><label>Email address<input name="email" type="email" autoComplete="email" required maxLength={254} /></label></div>
        <div className="form-actions"><button className="primary" type="submit">{busy ? 'Sending…' : 'Send username'}</button></div>
      </fieldset>
    </form>}
    <p><a className="reset-password-link" href="/login">Back to login</a></p>
  </section>
}
