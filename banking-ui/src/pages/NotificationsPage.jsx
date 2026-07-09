import { useEffect, useState } from 'react'
import { api } from '../api'
import { useAuth } from '../auth'

export default function NotificationsPage() {
  const { session } = useAuth()
  const token = session.accessToken
  const customerId = session.customerId
  const [items, setItems] = useState([])
  const [error, setError] = useState('')
  const [deviceStatus, setDeviceStatus] = useState('Checking…')

  const load = async () => {
    if (!customerId) return
    try {
      setItems(await api.listNotifications(token, customerId))
    } catch (err) {
      setError(err.message)
    }
  }

  const ensureDevice = async () => {
    if (!customerId) return
    try {
      const key = `harbor.push.${customerId}`
      let deviceToken = localStorage.getItem(key)
      if (!deviceToken) {
        deviceToken = `web-${customerId.slice(0, 8)}-${crypto.randomUUID()}`
        localStorage.setItem(key, deviceToken)
      }
      await api.registerDevice(token, {
        customerId,
        platform: 'WEB',
        token: deviceToken,
      })
      setDeviceStatus('Web push device registered for Harbor Bank alerts')
    } catch (err) {
      setDeviceStatus('Device registration skipped: ' + err.message)
    }
  }

  useEffect(() => {
    ensureDevice().then(load)
    const id = setInterval(load, 8000)
    return () => clearInterval(id)
  }, [])

  const markRead = async (id) => {
    try {
      await api.markNotificationRead(token, id)
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div>
      <h1 className="page-title">Alerts</h1>
      <p className="page-sub">
        Transaction and banking alerts arrive automatically over Kafka
        (transfers, payments, cards, loans) as in-app + push notifications.
      </p>
      <p className="muted" style={{ marginBottom: 12 }}>{deviceStatus}</p>
      {error && <div className="error" style={{ marginBottom: 12 }}>{error}</div>}
      <section className="panel">
        <h2>Inbox</h2>
        {items.length === 0 ? <div className="empty">No notifications yet. Complete a transfer to see a live alert.</div> : (
          <table className="table">
            <thead><tr><th>When</th><th>Category</th><th>Message</th><th>Channel</th><th>Status</th><th></th></tr></thead>
            <tbody>
              {items.map((n) => (
                <tr key={n.id}>
                  <td>{new Date(n.createdAt).toLocaleString()}</td>
                  <td>{n.category}</td>
                  <td><strong>{n.title}</strong><div className="muted">{n.body}</div>
                    {n.eventType && <div className="muted">event: {n.eventType}</div>}
                  </td>
                  <td>{n.channel}</td>
                  <td><span className={`badge ${n.status}`}>{n.status}</span></td>
                  <td>{n.status !== 'READ' && <button className="secondary" onClick={() => markRead(n.id)}>Mark read</button>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}
