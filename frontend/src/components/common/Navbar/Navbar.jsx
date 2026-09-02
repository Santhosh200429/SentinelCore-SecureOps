import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../../context/AuthContext.jsx';
import ProfileDropdown from '../../profile/ProfileDropdown.jsx';
import alertService from '../../../services/alertService.js';
import { useToast } from '../Toast/Toast.jsx';

export default function Navbar() {
    const { user, logout } = useAuth();
    const navigate = useNavigate();
    const showToast = useToast();
    const [time, setTime] = useState('');
    const [alerts, setAlerts] = useState([]);
    const [showNotifications, setShowNotifications] = useState(false);
    const knownAlertIds = useRef(new Set());
    const firstLoad = useRef(true);

    useEffect(() => {
        const tick = () => setTime(new Date().toLocaleTimeString('en-US', { hour12: false }));
        tick();
        const id = setInterval(tick, 1000);
        return () => clearInterval(id);
    }, []);

    useEffect(() => {
        let active = true;
        const loadInitial = async () => {
            try { const response = await alertService.getLive(); if (!active) return; const next = Array.isArray(response.data) ? response.data : []; setAlerts(next); knownAlertIds.current = new Set(next.map(a => String(a.id))); firstLoad.current = false; } catch {}
        };
        loadInitial();
        const streamUrl = `${import.meta.env.VITE_API_URL || ''}/api/alerts/stream`;
        const source = new EventSource(streamUrl, { withCredentials: true });
        source.addEventListener('alert', (event) => {
            try {
                const alert = JSON.parse(event.data);
                if (!active) return;
                setAlerts(prev => [alert, ...prev.filter(a => String(a.id) !== String(alert.id))].slice(0,10));
                if (!firstLoad.current && !knownAlertIds.current.has(String(alert.id))) {
                    const severity = String(alert.severity || 'Info').toLowerCase();
                    showToast(`${String(alert.severity || 'ALERT').toUpperCase()}: ${alert.title} • ${alert.source}`, severity === 'critical' ? 'error' : severity === 'warning' ? 'warning' : 'info');
                }
                knownAlertIds.current.add(String(alert.id)); firstLoad.current = false;
            } catch {}
        });
        return () => { active = false; source.close(); };
    }, [showToast]);

    async function handleLogout() {
        try { await logout(); }
        finally { navigate('/login?logout', { replace: true }); }
    }

    return (
        <header className="topnav">
            <div className="navbar-welcome">
                <button className="mobile-sidebar-toggle nav-icon-btn" type="button" aria-label="Toggle sidebar" onClick={() => window.dispatchEvent(new CustomEvent('sentinelcore:toggle-sidebar'))}>
                    <i className="ph ph-list" />
                </button>
                <div>
                    <div className="navbar-welcome-title">Welcome back, {user?.username || 'Operator'}! <span aria-hidden="true">👋</span></div>
                    <div className="navbar-welcome-subtitle">Here's what's happening with your security operations today.</div>
                </div>
            </div>

            <div className="global-search-container">
                <i className="ph ph-magnifying-glass search-icon" />
                <input type="text" placeholder="Search Assets, Incidents, Users..." className="global-search" id="globalSearchInput" />
                <kbd className="search-shortcut">/</kbd>
            </div>

            <div className="user-controls">
                <div className="env-badge"><i className="ph ph-shield-check" /> PROD</div>
                <div className="time-display" id="currentTimeDisplay">{time}</div>
                <button className="nav-icon-btn tooltip-parent" id="refreshBtn" title="Refresh Dashboard" onClick={() => window.location.reload()}>
                    <i className="ph ph-arrows-clockwise" />
                </button>

                <div className="notification-center">
                    <button className="nav-icon-btn notification-trigger" id="notifBtn" aria-label="Security notifications" onClick={() => setShowNotifications((v) => !v)}>
                        <i className={`ph ${alerts.length ? 'ph-bell-ringing' : 'ph-bell'}`} />
                        {alerts.length > 0 && <span className="notif-badge">{alerts.length > 99 ? '99+' : alerts.length}</span>}
                    </button>
                    {showNotifications && (
                        <div className="notification-panel">
                            <div className="notification-panel-header">
                                <div><strong>Security Alerts</strong><span>Live • updates every 5s</span></div>
                                <button type="button" onClick={() => setShowNotifications(false)} aria-label="Close notifications">×</button>
                            </div>
                            <div className="notification-list">
                                {alerts.length === 0 ? (
                                    <div className="notification-empty"><i className="ph ph-shield-check" /><span>No active security alerts</span></div>
                                ) : alerts.map((alert) => (
                                    <button type="button" className={`notification-item ${String(alert.severity || '').toLowerCase()}`} key={alert.id} onClick={() => { setShowNotifications(false); navigate('/infrastructure'); }}>
                                        <span className="notification-icon"><i className="ph ph-warning" /></span>
                                        <span className="notification-copy"><strong>{alert.title}</strong><small>{alert.source} • {alert.severity}</small></span>
                                    </button>
                                ))}
                            </div>
                        </div>
                    )}
                </div>
                <ProfileDropdown />
            </div>
        </header>
    );
}
