import { useState, useEffect } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext.jsx';
import logo from '../../assets/logo.svg';
import '../../styles/login.css';

export default function LoginPage() {
    const { login, isAuthenticated } = useAuth();
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [rememberMe, setRememberMe] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [showPassword, setShowPassword] = useState(false);

    const hasError = searchParams.has('error');
    const errorType = searchParams.get('error');
    const hasRegistered = searchParams.has('registered');
    const hasLogout = searchParams.has('logout');
    const hasAccess = searchParams.has('access');
    const hasExpired = searchParams.has('expired');

    useEffect(() => {
        if (isAuthenticated) navigate('/dashboard', { replace: true });
    }, [isAuthenticated, navigate]);

    function getErrorMessage() {
        if (error) return error;
        if (hasError) {
            if (errorType === 'locked') return 'Your account has been locked. Contact an administrator.';
            if (errorType === 'disabled') return 'This account is disabled. Contact support.';
            return 'Invalid username or password. Please try again.';
        }
        if (hasExpired) return 'Your session expired. Please sign in again.';
        return '';
    }

    async function handleSubmit(e) {
        e.preventDefault();
        if (!username.trim() || !password) {
            setError('Please enter your username and password.');
            return;
        }
        setError('');
        setLoading(true);
        try {
            await login(username.trim(), password, rememberMe);
        } catch (err) {
            const status = err?.response?.status;
            if (status === 401 || status === 403) {
                setError('Invalid username or password. Please try again.');
            } else if (err?.code === 'ERR_NETWORK' || err?.code === 'ERR_CONNECTION_REFUSED') {
                setError('Connection error. Please make sure the SentinelCore backend is running.');
            } else {
                setError('Unable to sign in right now. Please try again.');
            }
        } finally {
            setLoading(false);
        }
    }

    return (
        <main className="auth-body auth-login-page">
            <div className="auth-bg-glow auth-bg-glow-one" />
            <div className="auth-bg-glow auth-bg-glow-two" />
            <div className="auth-grid" />

            <section className="login-shell" aria-label="SentinelCore SecureOps sign in">
                <div className="login-brand-panel">
                    <div className="login-brand-top">
                        <div className="login-logo-wrap"><img src={logo} alt="SentinelCore" className="login-logo" /></div>
                        <div><div className="login-brand-name">SentinelCore</div><div className="login-brand-product">SECUREOPS</div></div>
                    </div>
                    <div className="login-hero-copy">
                        <span className="login-eyebrow"><span className="live-dot" /> Security Operations Center</span>
                        <h1>Protect your infrastructure.<br /><span>Stay ahead of threats.</span></h1>
                        <p>One secure workspace for assets, incidents, vulnerabilities, compliance and security operations.</p>
                    </div>
                    <div className="login-security-grid">
                        <div className="login-security-card"><i className="ph ph-shield-check" /><div><strong>Secure Access</strong><span>Role-based protection</span></div></div>
                        <div className="login-security-card"><i className="ph ph-pulse" /><div><strong>Live Monitoring</strong><span>Real-time operations</span></div></div>
                        <div className="login-security-card"><i className="ph ph-lock-key" /><div><strong>Audited Actions</strong><span>Traceable activity</span></div></div>
                        <div className="login-security-card"><i className="ph ph-chart-line-up" /><div><strong>Security Insights</strong><span>Actionable visibility</span></div></div>
                    </div>
                    <div className="login-status-line"><span className="status-check"><i className="ph ph-check" /></span><span>SentinelCore systems are ready for secure operations</span></div>
                </div>

                <div className="login-form-panel">
                    <div className="mobile-login-brand"><img src={logo} alt="SentinelCore" /><div><strong>SentinelCore</strong><span>SecureOps</span></div></div>
                    <div className="login-form-heading">
                        <span className="form-kicker">WELCOME BACK</span>
                        <h2>Sign in to your workspace</h2>
                        <p>Enter your credentials to continue to the security console.</p>
                    </div>

                    {getErrorMessage() && <div className="login-alert login-alert-danger" role="alert"><i className="ph ph-warning-circle" /><span>{getErrorMessage()}</span></div>}
                    {hasRegistered && <div className="login-alert login-alert-success" role="status"><i className="ph ph-check-circle" /><span>Account created successfully. You can sign in now.</span></div>}
                    {hasLogout && <div className="login-alert login-alert-success" role="status"><i className="ph ph-sign-out" /><span>You have been signed out successfully.</span></div>}
                    {hasAccess && <div className="login-alert login-alert-info" role="status"><i className="ph ph-shield-warning" /><span>Access denied. You do not have permission for that resource.</span></div>}

                    <form className="login-form" onSubmit={handleSubmit} noValidate>
                        <div className="login-field">
                            <label htmlFor="username">Username</label>
                            <div className="login-input-wrap"><i className="ph ph-user" /><input type="text" id="username" name="username" placeholder="Enter your username" autoFocus autoComplete="username" required value={username} onChange={(e) => setUsername(e.target.value)} /></div>
                        </div>
                        <div className="login-field">
                            <div className="login-label-row"><label htmlFor="password">Password</label><span>Protected connection</span></div>
                            <div className="login-input-wrap"><i className="ph ph-lock-key" /><input type={showPassword ? 'text' : 'password'} id="password" name="password" placeholder="Enter your password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} /><button type="button" className="password-toggle" onClick={() => setShowPassword(prev => !prev)} aria-label={showPassword ? 'Hide password' : 'Show password'}><i className={`ph ${showPassword ? 'ph-eye-slash' : 'ph-eye'}`} /></button></div>
                        </div>
                        <div className="login-options">
                            <label className="login-check"><input type="checkbox" name="remember-me" checked={rememberMe} onChange={(e) => setRememberMe(e.target.checked)} /><span>Keep me signed in</span></label>
                            <button type="button" className="login-forgot" onClick={() => setError('Please contact your SentinelCore administrator to reset your password.')}>Forgot password?</button>
                        </div>
                        <button type="submit" className="login-submit" disabled={loading}>{loading ? <><span className="login-spinner" /> Signing in…</> : <><span>Sign in securely</span><i className="ph ph-arrow-right" /></>}</button>
                    </form>

                    <div className="login-divider"><span>OR</span></div>
                    <div className="login-register"><span>New to SentinelCore?</span><Link to="/register">Create an account <i className="ph ph-arrow-up-right" /></Link></div>
                    <div className="login-demo"><div className="login-demo-icon"><i className="ph ph-flask" /></div><div><strong>Demo access</strong><span>Use your assigned account credentials. You can also register a new account.</span></div></div>
                    <div className="login-footer"><span><i className="ph ph-lock-simple" /> Encrypted session</span><span><i className="ph ph-shield-check" /> SecureOps Platform</span></div>
                </div>
            </section>
        </main>
    );
}
