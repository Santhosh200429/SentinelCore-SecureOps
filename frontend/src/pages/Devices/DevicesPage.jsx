import { useEffect, useState } from 'react';
import DashboardLayout from '../../layouts/DashboardLayout.jsx';
import agentService from '../../services/agentService.js';

const fmt = (v, suffix = '') =>
  v === null || v === undefined || v === ''
    ? '—'
    : `${v}${suffix}`;

const duration = (s) =>
  typeof s !== 'number'
    ? '—'
    : `${Math.floor(s / 86400)}d ${Math.floor(
        (s % 86400) / 3600
      )}h ${Math.floor((s % 3600) / 60)}m`;

export default function DevicesPage() {
  const [agents, setAgents] = useState([]);
  const [name, setName] = useState('My Device');
  const [token, setToken] = useState('');
  const [selected, setSelected] = useState(null);
  const [telemetry, setTelemetry] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const load = async () => {
    try {
      const r = await agentService.list();

      setAgents(
        Array.isArray(r.data)
          ? r.data
          : []
      );

      setError('');
    } catch {
      setError('Unable to load devices.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const enroll = async () => {
    try {
      const r = await agentService.enroll(
        name || 'My Device'
      );

      setToken(r.data.token);
      setSelected(r.data.assetId || null);

      await load();
    } catch (e) {
      setError(
        e?.response?.data?.error ||
          'Device enrollment failed.'
      );
    }
  };

  const revoke = async (id) => {
    if (
      !window.confirm(
        'Revoke this device token? The agent will stop sending telemetry.'
      )
    ) {
      return;
    }

    try {
      await agentService.revoke(id);

      if (
        selected ===
        agents.find((a) => a.id === id)?.assetId
      ) {
        setSelected(null);
      }

      await load();
    } catch {
      setError('Could not revoke device.');
    }
  };

  useEffect(() => {
    if (!selected) {
      setTelemetry(null);
      return;
    }

    let active = true;

    const url = `${
      import.meta.env.VITE_API_URL || ''
    }/api/agents/stream/${selected}`;

    const source = new EventSource(url, {
      withCredentials: true,
    });

    source.addEventListener(
      'telemetry',
      (e) => {
        if (!active) return;

        try {
          setTelemetry(
            JSON.parse(e.data)
          );
        } catch {
          // Ignore invalid telemetry payload
        }
      }
    );

    source.onerror = () => {
      if (active) {
        setTelemetry((t) =>
          t
            ? {
                ...t,
                status: 'DISCONNECTED',
              }
            : t
        );
      }
    };

    return () => {
      active = false;
      source.close();
    };
  }, [selected]);

  return (
    <DashboardLayout>

      {/* PAGE HEADER */}
      <section
        className="content-header"
        style={{ marginBottom: 20 }}
      >
        <h1>
          My Devices{' '}
          <span
            style={{
              fontSize: '.85rem',
              color: 'var(--text-muted)',
              fontWeight: 400,
            }}
          >
            Endpoint monitoring
          </span>
        </h1>

        <p
          style={{
            margin: 0,
            color: 'var(--text-muted)',
            fontSize: '.85rem',
          }}
        >
          Connect your own computer to SentinelCore
          using a revocable device agent.
        </p>
      </section>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns:
            'minmax(280px,360px) 1fr',
          gap: 18,
          alignItems: 'start',
        }}
      >

        {/* CONNECT DEVICE */}
        <section className="panel-card">

          <h2 className="panel-title">
            Connect a device
          </h2>

          <p
            style={{
              color: 'var(--text-muted)',
              fontSize: '.82rem',
              lineHeight: 1.6,
            }}
          >
            Create a device token, install the
            SentinelCore Agent on the computer,
            then configure the agent with the token.
          </p>

          {/* DEVICE NAME */}
          <input
            value={name}
            onChange={(e) =>
              setName(e.target.value)
            }
            placeholder="Device name"
            style={inputStyle}
          />

          {/* GENERATE TOKEN */}
          <button
            onClick={enroll}
            style={buttonStyle}
          >
            Generate Device Token
          </button>

          {/* TOKEN */}
          {token && (
            <div style={{ marginTop: 14 }}>

              <strong
                style={{
                  fontSize: '.8rem',
                }}
              >
                Device token — shown once
              </strong>

              <textarea
                readOnly
                value={token}
                style={{
                  ...inputStyle,
                  minHeight: 95,
                  marginTop: 7,
                  fontFamily: 'monospace',
                  fontSize: '.72rem',
                }}
                onFocus={(e) =>
                  e.target.select()
                }
              />

              <p
                style={{
                  fontSize: '.74rem',
                  color: 'var(--text-muted)',
                }}
              >
                Save it securely. Never commit
                it to Git or share it publicly.
              </p>

            </div>
          )}

          {/* AGENT SETUP */}
          <div
            style={{
              marginTop: 18,
              padding: 14,
              border: '1px solid var(--border-color)',
              borderRadius: 10,
              fontSize: '.75rem',
              lineHeight: 1.6,
              color: '#111',
              background: '#fff',
            }}
          >

            <strong
              style={{
                display: 'block',
                fontSize: '.85rem',
                color: '#111',
                marginBottom: 8,
              }}
            >
              Agent setup
            </strong>

            <p
              style={{
                margin: '0 0 12px',
                color: '#555',
                fontSize: '.78rem',
              }}
            >
              Download the SentinelCore Agent
              for Windows and connect this computer.
            </p>

            {/* DOWNLOAD BUTTON */}
            <a
              href={`${import.meta.env.VITE_API_URL}/downloads/windows-agent`}
              target="_blank"
              rel="noopener noreferrer"
              style={{
                display: 'block',
                width: '100%',
                boxSizing: 'border-box',
                padding: '11px 14px',
                borderRadius: 8,
                textAlign: 'center',
                textDecoration: 'none',
                fontWeight: 700,
                fontSize: '.82rem',
                background: '#000',
                color: '#fff',
                border: '1px solid #000',
                marginBottom: 12,
                cursor: 'pointer',
              }}
            >
              ↓ Download Windows Agent
            </a>

            {/* SETUP INSTRUCTIONS */}
            <div
              style={{
                padding: 10,
                border: '1px solid #ddd',
                borderRadius: 8,
                color: '#222',
                background: '#fafafa',
                lineHeight: 1.7,
              }}
            >

              <strong
                style={{
                  color: '#111',
                }}
              >
                After downloading:
              </strong>

              <br />

              1. Generate your device token above.

              <br />

              2. Download the SentinelCore Agent.

              <br />

              3. Open Command Prompt in the folder
              where the agent was downloaded.

              <br />

              4. Run the following commands:

              <pre
                style={{
                  margin: '8px 0',
                  padding: 10,
                  background: '#111',
                  color: '#fff',
                  borderRadius: 6,
                  overflowX: 'auto',
                  fontSize: '.72rem',
                  lineHeight: 1.6,
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                }}
              >
{`set SENTINELCORE_AGENT_TOKEN=YOUR_DEVICE_TOKEN
java -jar sentinelcore-agent-1.0.0.jar`}
              </pre>

              5. Replace{' '}
              <code
                style={{
                  color: '#000',
                  background: '#eee',
                  padding: '2px 4px',
                  borderRadius: 4,
                }}
              >
                YOUR_DEVICE_TOKEN
              </code>{' '}
              with the device token generated above.

              <br />

              6. Keep the agent running to continue
              sending telemetry.

              <p
                style={{
                  margin: '10px 0 0',
                  color: '#555',
                  fontSize: '.72rem',
                }}
              >
                The agent automatically connects to
                the SentinelCore production server.
              </p>

            </div>

          </div>

        </section>

        {/* CONNECTED DEVICES */}
        <section className="panel-card">

          <h2 className="panel-title">
            Connected devices
          </h2>

          {error && (
            <div
              style={{
                marginBottom: 12,
                padding: 10,
                borderRadius: 8,
              }}
            >
              {error}
            </div>
          )}

          {loading ? (
            <p>Loading…</p>
          ) : !agents.length ? (
            <p
              style={{
                color: 'var(--text-muted)',
              }}
            >
              No devices enrolled yet.
            </p>
          ) : (
            <div className="table-wrapper">

              <table className="data-table">

                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Status</th>
                    <th>Last seen</th>
                    <th>Action</th>
                  </tr>
                </thead>

                <tbody>

                  {agents.map((a) => (
                    <tr key={a.id}>

                      <td>

                        <button
                          onClick={() =>
                            a.assetId &&
                            setSelected(
                              a.assetId
                            )
                          }
                          style={{
                            background: 'none',
                            border: 0,
                            cursor: a.assetId
                              ? 'pointer'
                              : 'default',
                            fontWeight: 700,
                            color: 'inherit',
                          }}
                        >
                          {a.name}
                        </button>

                      </td>

                      <td>
                        {a.active
                          ? a.lastSeen
                            ? 'CONNECTED'
                            : 'WAITING'
                          : 'REVOKED'}
                      </td>

                      <td>
                        {a.lastSeen
                          ? new Date(
                              a.lastSeen
                            ).toLocaleString()
                          : 'Never'}
                      </td>

                      <td>

                        <button
                          onClick={() =>
                            revoke(a.id)
                          }
                          disabled={!a.active}
                          style={{
                            ...smallButton,
                            opacity: a.active
                              ? 1
                              : 0.5,
                          }}
                        >
                          Revoke
                        </button>

                      </td>

                    </tr>
                  ))}

                </tbody>

              </table>

            </div>
          )}

        </section>

      </div>

      {/* LIVE TELEMETRY */}
      {selected && (
        <section
          className="panel-card"
          style={{ marginTop: 18 }}
        >

          <h2 className="panel-title">
            Live device telemetry{' '}
            <span className="panel-subtitle">
              SSE
            </span>
          </h2>

          {!telemetry ? (
            <p
              style={{
                color: 'var(--text-muted)',
              }}
            >
              Waiting for agent telemetry…
            </p>
          ) : (
            <div className="kpi-grid">

              <Mini
                label="CPU"
                value={fmt(
                  telemetry.cpuUsage,
                  '%'
                )}
              />

              <Mini
                label="Memory"
                value={fmt(
                  telemetry.memoryUsage,
                  '%'
                )}
              />

              <Mini
                label="Disk"
                value={fmt(
                  telemetry.diskUsage,
                  '%'
                )}
              />

              <Mini
                label="Processes"
                value={fmt(
                  telemetry.processCount
                )}
              />

              <Mini
                label="Uptime"
                value={duration(
                  telemetry.uptime
                )}
              />

              <Mini
                label="Status"
                value={
                  telemetry.status ||
                  'LIVE'
                }
              />

            </div>
          )}

          {telemetry && (
            <div
              style={{
                display: 'grid',
                gridTemplateColumns:
                  '1fr 1fr',
                gap: 18,
                marginTop: 18,
              }}
            >

              <div>

                <Info
                  k="Hostname"
                  v={telemetry.hostname}
                />

                <Info
                  k="OS"
                  v={telemetry.os}
                />

                <Info
                  k="OS Version"
                  v={telemetry.osVersion}
                />

                <Info
                  k="Architecture"
                  v={
                    telemetry.architecture
                  }
                />

              </div>

              <div>

                <Info
                  k="Processor"
                  v={
                    telemetry.processor
                  }
                />

                <Info
                  k="CPU Cores"
                  v={
                    telemetry.cpuCores
                  }
                />

                <Info
                  k="Logical Processors"
                  v={
                    telemetry.logicalProcessors
                  }
                />

                <Info
                  k="IP Address"
                  v={
                    telemetry.ipAddress
                  }
                />

              </div>

            </div>
          )}

        </section>
      )}

    </DashboardLayout>
  );
}


/* KPI CARD */
function Mini({ label, value }) {
  return (
    <div className="kpi-card blue">

      <div className="kpi-card-header">

        <span className="kpi-card-title">
          {label}
        </span>

      </div>

      <div
        className="kpi-card-value"
        style={{
          fontSize: '1.15rem',
        }}
      >
        {value}
      </div>

    </div>
  );
}


/* TELEMETRY INFO */
function Info({ k, v }) {
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        padding: '8px 0',
        borderBottom:
          '1px solid var(--border-color)',
        fontSize: '.82rem',
      }}
    >

      <span
        style={{
          color: 'var(--text-muted)',
        }}
      >
        {k}
      </span>

      <strong>
        {v ?? '—'}
      </strong>

    </div>
  );
}


/* STYLES */
const inputStyle = {
  width: '100%',
  boxSizing: 'border-box',
  padding: '10px 12px',
  border:
    '1px solid var(--border-color)',
  borderRadius: 8,
  background: 'var(--bg-base)',
  color: 'inherit',
  marginBottom: 10,
};

const buttonStyle = {
  width: '100%',
  padding: '11px 14px',
  border: 0,
  borderRadius: 8,
  cursor: 'pointer',
  fontWeight: 700,
};

const smallButton = {
  padding: '6px 10px',
  border:
    '1px solid var(--border-color)',
  borderRadius: 7,
  background: 'transparent',
  color: 'inherit',
  cursor: 'pointer',
};