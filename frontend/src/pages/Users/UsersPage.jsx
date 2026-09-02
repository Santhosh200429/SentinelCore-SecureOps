import { useEffect, useState } from 'react';
import DashboardLayout from '../../layouts/DashboardLayout.jsx';
import Loader from '../../components/common/Loader/Loader.jsx';
import { useToast } from '../../components/common/Toast/Toast.jsx';
import userService from '../../services/userService.js';
import { useAuth } from '../../context/AuthContext.jsx';
import Swal from 'sweetalert2';

const roleOptions = [
    'ROLE_SUPER_ADMIN',
    'ROLE_ADMIN',
    'ROLE_SOC_MANAGER',
    'ROLE_SECURITY_ANALYST',
    'ROLE_INCIDENT_RESPONDER',
    'ROLE_INFRA_ENGINEER',
    'ROLE_DEVSECOPS',
    'ROLE_AUDITOR',
    'ROLE_VIEWER',
];

const roleLabels = {
    ROLE_SUPER_ADMIN: 'Super Admin',
    ROLE_ADMIN: 'Admin',
    ROLE_SOC_MANAGER: 'SOC Manager',
    ROLE_SECURITY_ANALYST: 'Security Analyst',
    ROLE_INCIDENT_RESPONDER: 'Incident Responder',
    ROLE_INFRA_ENGINEER: 'Infra Engineer',
    ROLE_DEVSECOPS: 'DevSecOps',
    ROLE_AUDITOR: 'Auditor',
    ROLE_VIEWER: 'Viewer',
};

function responseMessage(err, fallback) {
    return err?.response?.data?.message || err?.response?.data?.error || fallback;
}

export default function UsersPage() {
    const showToast = useToast();
    const { hasPermission } = useAuth();
    const [users, setUsers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);

    async function fetchUsers() {
        try {
            const res = await userService.getAll();
            setUsers(Array.isArray(res.data) ? res.data : []);
        } catch (err) {
            showToast(responseMessage(err, 'Failed to load user administration list'), 'error');
        }
    }

    useEffect(() => {
        fetchUsers().finally(() => setLoading(false));
    }, []);

    async function handleAddUser() {
        if (!hasPermission('USER_MANAGE')) {
            Swal.fire('Access Denied', 'You do not have permission to add users.', 'error');
            return;
        }

        const result = await Swal.fire({
            title: 'Add User',
            text: 'Create a new SentinelCore user account',
            html: `
                <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;text-align:left">
                    <input id="admin-firstName" class="swal2-input" style="width:auto;margin:0" placeholder="First name">
                    <input id="admin-lastName" class="swal2-input" style="width:auto;margin:0" placeholder="Last name">
                    <input id="admin-username" class="swal2-input" style="width:auto;margin:0;grid-column:1/-1" placeholder="Username *" autocomplete="off">
                    <input id="admin-email" type="email" class="swal2-input" style="width:auto;margin:0;grid-column:1/-1" placeholder="Email">
                    <input id="admin-phone" class="swal2-input" style="width:auto;margin:0" placeholder="Phone">
                    <input id="admin-organization" class="swal2-input" style="width:auto;margin:0" placeholder="Organization">
                    <div style="position:relative;grid-column:1/-1">
                        <input id="admin-password" type="password" class="swal2-input" style="width:100%;margin:0;box-sizing:border-box;padding-right:76px" placeholder="Password *" autocomplete="new-password">
                        <button type="button" id="admin-password-toggle" style="position:absolute;right:8px;top:8px;height:36px;border:0;border-radius:8px;background:transparent;color:#666;cursor:pointer">Show</button>
                    </div>
                    <div style="position:relative;grid-column:1/-1">
                        <input id="admin-confirmPassword" type="password" class="swal2-input" style="width:100%;margin:0;box-sizing:border-box;padding-right:76px" placeholder="Confirm password *" autocomplete="new-password">
                        <button type="button" id="admin-confirm-toggle" style="position:absolute;right:8px;top:8px;height:36px;border:0;border-radius:8px;background:transparent;color:#666;cursor:pointer">Show</button>
                    </div>
                    <select id="admin-role" class="swal2-select" style="width:100%;margin:0;grid-column:1/-1">
                        <option value="" selected disabled>Select role *</option>
                        ${roleOptions.map((role) => `<option value="${role}">${roleLabels[role]}</option>`).join('')}
                    </select>
                </div>
            `,
            showCancelButton: true,
            confirmButtonText: 'Create User',
            cancelButtonText: 'Cancel',
            confirmButtonColor: '#3a7bd5',
            width: 620,
            focusConfirm: false,
            didOpen: () => {
                const bindToggle = (inputId, buttonId) => {
                    const input = document.getElementById(inputId);
                    const button = document.getElementById(buttonId);
                    button?.addEventListener('click', () => {
                        const visible = input.type === 'text';
                        input.type = visible ? 'password' : 'text';
                        button.textContent = visible ? 'Show' : 'Hide';
                    });
                };
                bindToggle('admin-password', 'admin-password-toggle');
                bindToggle('admin-confirmPassword', 'admin-confirm-toggle');
                document.getElementById('admin-username')?.focus();
            },
            preConfirm: () => {
                const get = (id) => document.getElementById(id)?.value?.trim() || '';
                const password = document.getElementById('admin-password')?.value || '';
                const confirmPassword = document.getElementById('admin-confirmPassword')?.value || '';
                const role = get('admin-role');

                if (!get('admin-username')) {
                    Swal.showValidationMessage('Username is required.');
                    return false;
                }
                if (password.length < 8) {
                    Swal.showValidationMessage('Password must be at least 8 characters.');
                    return false;
                }
                if (password !== confirmPassword) {
                    Swal.showValidationMessage('Passwords do not match.');
                    return false;
                }
                if (!role) {
                    Swal.showValidationMessage('Please select a role.');
                    return false;
                }

                return {
                    username: get('admin-username'),
                    email: get('admin-email'),
                    password,
                    firstName: get('admin-firstName'),
                    lastName: get('admin-lastName'),
                    phone: get('admin-phone'),
                    organization: get('admin-organization'),
                    role,
                };
            },
        });

        if (!result.isConfirmed || !result.value) return;

        setBusy(true);
        try {
            await userService.create(result.value);
            showToast(`User ${result.value.username} created successfully`, 'success');
            await fetchUsers();
        } catch (err) {
            showToast(responseMessage(err, 'Failed to create user'), 'error');
        } finally {
            setBusy(false);
        }
    }

    async function handleRoleChange(id, username, newRole) {
        if (!hasPermission('ROLE_ASSIGN')) {
            Swal.fire('Access Denied', 'You do not have permission to assign roles.', 'error');
            return;
        }

        try {
            await userService.assignRole(id, newRole);
            showToast(`Assigned ${roleLabels[newRole] || newRole} to ${username}`, 'success');
            await fetchUsers();
        } catch (err) {
            showToast(responseMessage(err, 'Failed to update user role'), 'error');
        }
    }

    async function handleToggleStatus(id, username, currentEnabled) {
        if (!hasPermission('USER_MANAGE')) {
            Swal.fire('Access Denied', 'You do not have permission to manage users.', 'error');
            return;
        }

        const action = currentEnabled ? 'disable' : 'enable';
        const confirm = await Swal.fire({
            title: `${currentEnabled ? 'Disable' : 'Enable'} User?`,
            text: `Are you sure you want to ${action} ${username}?`,
            icon: 'warning',
            showCancelButton: true,
            confirmButtonColor: currentEnabled ? '#d53a3a' : '#3a9d5d',
            cancelButtonColor: '#777',
            confirmButtonText: currentEnabled ? 'Yes, disable' : 'Yes, enable',
        });

        if (!confirm.isConfirmed) return;

        try {
            await userService.setEnabled(id, !currentEnabled);
            showToast(`User ${username} ${currentEnabled ? 'disabled' : 'enabled'} successfully`, 'success');
            await fetchUsers();
        } catch (err) {
            showToast(responseMessage(err, 'Failed to update user status'), 'error');
        }
    }

    async function handleResetPassword(id, username) {
        if (!hasPermission('USER_MANAGE')) {
            Swal.fire('Access Denied', 'You do not have permission to manage users.', 'error');
            return;
        }

        const result = await Swal.fire({
            title: 'Reset Password',
            text: `Set a new password for ${username}`,
            html: `
                <div style="position:relative;text-align:left">
                    <input id="reset-password" type="password" class="swal2-input" style="width:100%;margin:8px 0;box-sizing:border-box;padding-right:76px" placeholder="New password" autocomplete="new-password">
                    <button type="button" id="reset-password-toggle" style="position:absolute;right:8px;top:16px;height:36px;border:0;border-radius:8px;background:transparent;color:#666;cursor:pointer">Show</button>
                </div>
                <div style="position:relative;text-align:left">
                    <input id="reset-confirm-password" type="password" class="swal2-input" style="width:100%;margin:8px 0;box-sizing:border-box;padding-right:76px" placeholder="Confirm new password" autocomplete="new-password">
                    <button type="button" id="reset-confirm-toggle" style="position:absolute;right:8px;top:16px;height:36px;border:0;border-radius:8px;background:transparent;color:#666;cursor:pointer">Show</button>
                </div>
            `,
            showCancelButton: true,
            confirmButtonText: 'Reset Password',
            confirmButtonColor: '#3a7bd5',
            focusConfirm: false,
            didOpen: () => {
                const bindToggle = (inputId, buttonId) => {
                    const input = document.getElementById(inputId);
                    const button = document.getElementById(buttonId);
                    button?.addEventListener('click', () => {
                        const visible = input.type === 'text';
                        input.type = visible ? 'password' : 'text';
                        button.textContent = visible ? 'Show' : 'Hide';
                    });
                };
                bindToggle('reset-password', 'reset-password-toggle');
                bindToggle('reset-confirm-password', 'reset-confirm-toggle');
                document.getElementById('reset-password')?.focus();
            },
            preConfirm: () => {
                const password = document.getElementById('reset-password')?.value || '';
                const confirmPassword = document.getElementById('reset-confirm-password')?.value || '';

                if (password.length < 8) {
                    Swal.showValidationMessage('Password must be at least 8 characters.');
                    return false;
                }
                if (password !== confirmPassword) {
                    Swal.showValidationMessage('Passwords do not match.');
                    return false;
                }
                return password;
            },
        });

        if (!result.isConfirmed || !result.value) return;

        try {
            await userService.resetPassword(id, result.value);
            showToast(`Password for ${username} has been reset successfully`, 'success');
        } catch (err) {
            showToast(responseMessage(err, 'Failed to reset password'), 'error');
        }
    }

    async function handleDelete(id, username) {
        if (!hasPermission('USER_MANAGE')) {
            Swal.fire('Access Denied', 'You do not have permission to manage users.', 'error');
            return;
        }

        const confirm = await Swal.fire({
            title: 'Delete User?',
            text: `Are you sure you want to permanently delete ${username}?`,
            icon: 'warning',
            showCancelButton: true,
            confirmButtonColor: '#d53a3a',
            cancelButtonColor: '#777',
            confirmButtonText: 'Yes, delete',
        });

        if (!confirm.isConfirmed) return;

        try {
            await userService.delete(id);
            showToast(`User ${username} deleted successfully`, 'success');
            await fetchUsers();
        } catch (err) {
            showToast(responseMessage(err, 'Failed to delete user'), 'error');
        }
    }

    return (
        <DashboardLayout>
            <section className="content-header" style={{ marginBottom: 20 }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
                    <div>
                        <h1 style={{ marginBottom: 4 }}>
                            User Administration &amp; Identity
                            <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)', fontWeight: 400 }}> RBAC configuration</span>
                        </h1>
                        <p style={{ margin: 0, color: 'var(--text-muted)', fontSize: '0.78rem' }}>
                            Add, manage, disable, reset passwords, assign roles, and remove users.
                        </p>
                    </div>
                    {hasPermission('USER_MANAGE') && (
                        <button className="btn btn-primary" onClick={handleAddUser} disabled={busy} style={{ width: 'auto', minWidth: 130 }}>
                            {busy ? 'Creating…' : '+ Add User'}
                        </button>
                    )}
                </div>
            </section>

            {loading ? <Loader /> : (
                <div className="panel-card">
                    <h2 className="panel-title" style={{ marginBottom: 15 }}>Managed Security Identities</h2>
                    <div className="table-wrapper">
                        <table className="data-table">
                            <thead>
                                <tr>
                                    <th>ID</th>
                                    <th>Username</th>
                                    <th>Email</th>
                                    <th>Role</th>
                                    <th>Status</th>
                                    <th>Last Login</th>
                                    <th>Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                {users.length === 0 ? (
                                    <tr>
                                        <td colSpan="7" style={{ textAlign: 'center', color: 'var(--text-muted)' }}>No user records found.</td>
                                    </tr>
                                ) : (
                                    users.map((user) => {
                                        const primaryRole = user.primaryRoleName || user.roles?.[0] || 'ROLE_VIEWER';
                                        return (
                                            <tr key={user.id}>
                                                <td>{user.id}</td>
                                                <td><strong>{user.username}</strong></td>
                                                <td>{user.email || '—'}</td>
                                                <td>
                                                    {hasPermission('ROLE_ASSIGN') ? (
                                                        <select
                                                            value={primaryRole}
                                                            onChange={(e) => handleRoleChange(user.id, user.username, e.target.value)}
                                                            style={{ padding: '4px 8px', border: '1px solid var(--border-color)', borderRadius: 4, background: 'var(--bg-inset)', color: 'var(--text-primary)', fontSize: '0.8rem' }}
                                                        >
                                                            {roleOptions.map((role) => (
                                                                <option key={role} value={role}>{roleLabels[role]}</option>
                                                            ))}
                                                        </select>
                                                    ) : (
                                                        <span className="badge badge-info">{roleLabels[primaryRole] || primaryRole}</span>
                                                    )}
                                                </td>
                                                <td>
                                                    <span className={`badge badge-status ${user.enabled ? 'ok' : 'alert'}`}>
                                                        {user.enabled ? 'Enabled' : 'Disabled'}
                                                    </span>
                                                </td>
                                                <td>{user.lastLogin ? new Date(user.lastLogin).toLocaleString() : 'Never'}</td>
                                                <td>
                                                    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                                                        {hasPermission('USER_MANAGE') && (
                                                            <>
                                                                <button className="btn btn-warning" style={{ width: 'auto', padding: '4px 9px', fontSize: '0.75rem', color: '#000' }}
                                                                    onClick={() => handleResetPassword(user.id, user.username)}>
                                                                    Reset PW
                                                                </button>
                                                                <button className="btn" style={{ width: 'auto', padding: '4px 9px', fontSize: '0.75rem', background: user.enabled ? '#E53935' : '#43A047', border: 'none' }}
                                                                    onClick={() => handleToggleStatus(user.id, user.username, user.enabled)}>
                                                                    {user.enabled ? 'Disable' : 'Enable'}
                                                                </button>
                                                                <button className="btn btn-red" style={{ width: 'auto', padding: '4px 9px', fontSize: '0.75rem' }}
                                                                    onClick={() => handleDelete(user.id, user.username)}>
                                                                    Delete
                                                                </button>
                                                            </>
                                                        )}
                                                    </div>
                                                </td>
                                            </tr>
                                        );
                                    })
                                )}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}
        </DashboardLayout>
    );
}
