import { useEffect, useState } from 'react'
import api from '../api/api'

export default function ResetPassword() {
  const [token] = useState(() => typeof window === 'undefined' ? '' : new URLSearchParams(window.location.hash.slice(1)).get('token') || '')
  const [challenge, setChallenge] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  useEffect(() => {
    if (token) window.history.replaceState(null, '', window.location.pathname + window.location.search)
  }, [token])

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    const form = event.currentTarget
    const data = Object.fromEntries(new FormData(form))
    setError('')
    if (token && data.newPassword !== data.confirmPassword) {
      setError('Passwords do not match.')
      return
    }
    setBusy(true)
    try {
      if (token) {
        await api.auth.completePasswordReset({ token, newPassword: data.newPassword })
        form.reset()
        setMessage('Your password has been reset. You can now log in with your new password.')
      } else if (challenge) {
        const response = await api.auth.verifyPasswordReset({ challengeId: challenge.challengeId,
          answers: challenge.questions.map(question => ({ questionId: question.id, answer: data[`answer-${question.id}`] })) })
        form.reset()
        setMessage(response.message)
      } else {
        setChallenge(await api.auth.startPasswordReset({ username: data.username }))
      }
    } catch (failure) { setError(failure.message || 'Unable to reset password. Please try again.') }
    finally { setBusy(false) }
  }

  return <section className="registration" aria-labelledby="reset-heading">
    <div className="section-header"><div><h2 id="reset-heading">Reset password</h2>
      <p>{token ? 'Choose a new password.' : 'Answer your recovery questions to request a reset link by email.'}</p>
    </div></div>
    {message ? <div className="form-card" role="status">{message}</div> : <form className="form-card" onSubmit={submit}>
      {error && <div className="error" role="alert">{error}</div>}
      <fieldset disabled={busy}><div className="registration-fields">
        {token ? <>
          <label>New password<input name="newPassword" type="password" autoComplete="new-password" required minLength={12} maxLength={72} /></label>
          <label>Confirm new password<input name="confirmPassword" type="password" autoComplete="new-password" required minLength={12} maxLength={72} /></label>
        </> : challenge ? challenge.questions.map(question => <label key={question.id}>{question.text}
          <input name={`answer-${question.id}`} type="password" autoComplete="off" required maxLength={256} />
        </label>) : <label>Username<input name="username" autoComplete="username" required maxLength={50} pattern="[A-Za-z0-9_.\-]+" /></label>}
      </div><div className="form-actions"><button className="primary" type="submit">{busy ? 'Please wait…' : token ? 'Reset password' : challenge ? 'Send reset link' : 'Continue'}</button></div></fieldset>
    </form>}
    <p><a href="/login">Back to login</a></p>
  </section>
}
