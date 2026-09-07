export default function AuthLinks({ user, registerUrl, loginUrl, onNavigate }) {
  if (user) return null
  function follow(event, url) {
    if (onNavigate && event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey) {
      event.preventDefault()
      onNavigate(url)
    }
  }
  return (
    <nav className="auth-links" aria-label="Account">
      <a href={registerUrl} onClick={event => follow(event, registerUrl)}>Register</a>
      <a href={loginUrl} onClick={event => follow(event, loginUrl)}>Login</a>
    </nav>
  )
}
