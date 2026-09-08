import { useEffect, useState } from 'react'
import api from '../api/api'

export function profileFormValues(profile) {
  return {
    email: profile.email, firstName: profile.firstName || '', lastName: profile.lastName || '',
    phoneNumber: profile.phoneNumber || '', avatarUrl: profile.avatarUrl || '',
    hintQuestion: profile.hintQuestion == null ? '' : String(profile.hintQuestion),
    hintAnswer: '', currentPassword: '', changeHint: false
  }
}

export function profileUpdatePayload(form) {
  const payload = {
    email: form.email, firstName: form.firstName, lastName: form.lastName,
    phoneNumber: form.phoneNumber, avatarUrl: form.avatarUrl
  }
  if (form.changeHint) {
    payload.hintQuestion = Number(form.hintQuestion)
    payload.hintAnswer = form.hintAnswer
  }
  if (form.currentPassword) payload.currentPassword = form.currentPassword
  return payload
}

export default function Profile() {
  const [profile, setProfile] = useState(null)
  const [questions, setQuestions] = useState([])
  const [form, setForm] = useState(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [reload, setReload] = useState(0)
  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    Promise.all([api.profile.get(), api.profile.questions()]).then(([account, catalog]) => {
      if (active) { setProfile(account); setForm(profileFormValues(account)); setQuestions(catalog) }
    }).catch(failure => { if (active) setError(failure.message || 'Unable to load your profile.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [reload])

  function change(name, value) {
    setForm(previous => ({ ...previous, [name]: value, ...(name === 'changeHint' ? { hintAnswer: '', currentPassword: '' } : {}) }))
    setMessage('')
  }
  async function submit(event) {
    event.preventDefault()
    if (saving) return
    setSaving(true)
    setError('')
    setMessage('')
    try {
      const saved = await api.profile.update(profileUpdatePayload(form))
      setProfile(saved)
      setForm(profileFormValues(saved))
      setMessage('Your profile has been updated.')
    } catch (failure) {
      setError(failure.message || 'Unable to save your profile.')
    } finally {
      setForm(previous => ({ ...previous, hintAnswer: '', currentPassword: '' }))
      setSaving(false)
    }
  }
  return <section className="registration" aria-labelledby="profile-heading">
    <div className="section-header"><div><h2 id="profile-heading">My profile</h2><p>Manage your contact details and profile hint.</p></div></div>
    {loading ? <p role="status">Loading profile…</p> : !profile ? <div className="form-card">
      <p className="error" role="alert">{error}</p><button type="button" onClick={() => setReload(value => value + 1)}>Try again</button>
    </div> : <>
      {error && <p className="error" role="alert">{error}</p>}
      {message && <p role="status">{message}</p>}
      <ProfileForm profile={profile} form={form} questions={questions} saving={saving} onChange={change} onSubmit={submit}
        onCancel={() => { setForm(profileFormValues(profile)); setError(''); setMessage('') }} />
    </>}
  </section>
}

export function ProfileForm({ profile, form, questions, saving, onChange, onSubmit, onCancel }) {
  const sensitive = form.email !== profile.email || form.changeHint
  const selected = questions.find(question => question.id === profile.hintQuestion)
  function input(event) { onChange(event.target.name, event.target.value) }
  return <form className="form-card" onSubmit={onSubmit}>
    <fieldset disabled={saving}>
      <div className="registration-fields">
        <label>Username<input value={profile.username} readOnly aria-describedby="username-help" /></label>
        <p id="username-help" className="field-help">Your username cannot be changed.</p>
        <label>Email<input name="email" type="email" autoComplete="email" value={form.email} onChange={input} required maxLength={100} /></label>
        <label>First name<input name="firstName" autoComplete="given-name" value={form.firstName} onChange={input} maxLength={50} /></label>
        <label>Last name<input name="lastName" autoComplete="family-name" value={form.lastName} onChange={input} maxLength={50} /></label>
        <label>Phone number<input name="phoneNumber" type="tel" autoComplete="tel" value={form.phoneNumber} onChange={input} maxLength={20} aria-describedby="phone-help" /></label>
        <p id="phone-help" className="field-help">Use 7–15 digits. A leading +, spaces, parentheses, and hyphens are allowed.</p>
        <label>Avatar URL<input name="avatarUrl" type="url" value={form.avatarUrl} onChange={input} maxLength={255} placeholder="https://example.com/avatar.png" /></label>
        <div><strong>Profile hint</strong><p>{selected?.text || 'No hint question selected.'}</p>
          <p>{profile.hintAnswerSet ? 'An answer is saved securely.' : 'No hint answer saved.'}</p>
        </div>
        <label className="profile-hint-toggle"><input type="checkbox" checked={form.changeHint}
          onChange={event => onChange('changeHint', event.target.checked)} />{profile.hintAnswerSet ? 'Change hint question or answer' : 'Set a hint question and answer'}</label>
        {form.changeHint && <>
          <label>Hint question<select name="hintQuestion" value={form.hintQuestion} onChange={input} required>
            <option value="">Select a question</option>
            {questions.map(question => <option key={question.id} value={question.id}>{question.text}</option>)}
          </select></label>
          <label>New hint answer<input name="hintAnswer" type="password" autoComplete="off" value={form.hintAnswer} onChange={input} required maxLength={256} /></label>
        </>}
        {sensitive && <>
          <label>Current password<input name="currentPassword" type="password" autoComplete="current-password" value={form.currentPassword} onChange={input} required maxLength={72} /></label>
          <p className="field-help">Confirm your password to change your email or hint details.</p>
        </>}
      </div>
      <div className="form-actions"><button className="secondary" type="button" onClick={onCancel}>Cancel changes</button>
        <button className="primary" type="submit">{saving ? 'Saving…' : 'Save profile'}</button></div>
    </fieldset>
  </form>
}
