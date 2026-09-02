import { useEffect, useMemo, useState } from 'react';
import DashboardLayout from '../../layouts/DashboardLayout.jsx';
import Loader from '../../components/common/Loader/Loader.jsx';
import infrastructureService from '../../services/infrastructureService.js';
import alertService from '../../services/alertService.js';
import { ResponsiveContainer, AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip } from 'recharts';

const fmtBytes = (n) => typeof n === 'number' ? `${(n / 1024 / 1024 / 1024).toFixed(2)} GB` : 'Data unavailable';
const fmtDuration = (seconds) => typeof seconds === 'number' ? `${Math.floor(seconds / 86400)}d ${Math.floor((seconds % 86400) / 3600)}h ${Math.floor((seconds % 3600) / 60)}m` : 'Data unavailable';
const value = (v, suffix='') => (v === null || v === undefined || v === 'Data unavailable') ? 'Data unavailable' : `${v}${suffix}`;

export default function InfrastructurePage() {
    const [telemetry, setTelemetry] = useState(null);
    const [history, setHistory] = useState([]);
    const [processes, setProcesses] = useState([]);
    const [alerts, setAlerts] = useState([]);
    const [status, setStatus] = useState('WAITING FOR TELEMETRY');
    const [lastUpdated, setLastUpdated] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        let active = true;
        const streamUrl = `${import.meta.env.VITE_API_URL || ''}/api/infrastructure/stream`;
        const source = new EventSource(streamUrl, { withCredentials: true });
        source.addEventListener('telemetry', (event) => {
            if (!active) return;
            try {
                const data = JSON.parse(event.data);
                setTelemetry(data); setStatus('LIVE'); setLastUpdated(new Date()); setLoading(false);
                const t = new Date().toLocaleTimeString('en-US', { hour12: false });
                setHistory(prev => [...prev, { time: t, cpu: Number(data.cpuUsage) || null, memory: Number(data.memoryUsage) || null }].slice(-20));
            } catch { setStatus('Data unavailable'); }
        });
        source.onerror = () => { if (active) setStatus('DISCONNECTED'); };
        return () => { active = false; source.close(); };
    }, []);

    useEffect(() => {
        let active = true;
        const load = async () => {
            try {
                const [processRes, alertRes] = await Promise.all([infrastructureService.getProcesses(), alertService.getLive()]);
                if (active) { setProcesses(Array.isArray(processRes.data) ? processRes.data : []); setAlerts(Array.isArray(alertRes.data) ? alertRes.data : []); }
            } catch { /* shared auth interceptor handles authentication */ }
        };
        load(); const id = setInterval(load, 10000); return () => { active = false; clearInterval(id); };
    }, []);

    const dbUtil = useMemo(() => {
        if (typeof telemetry?.dbActiveConnections !== 'number' || typeof telemetry?.dbMaxConnections !== 'number' || telemetry.dbMaxConnections === 0) return null;
        return Math.round(telemetry.dbActiveConnections / telemetry.dbMaxConnections * 100);
    }, [telemetry]);

    return <DashboardLayout>
        <section className="content-header" style={{ marginBottom: 20, display:'flex', justifyContent:'space-between', alignItems:'center', gap:15, flexWrap:'wrap' }}>
            <div><h1>Infrastructure &amp; Telemetry <span style={{fontSize:'0.85rem',color:'var(--text-muted)',fontWeight:400}}>Host monitoring</span></h1><p style={{margin:0,color:'var(--text-muted)',fontSize:'.8rem'}}>Source: {telemetry?.telemetrySource || 'Waiting for telemetry'}</p></div>
            <div style={{padding:'8px 12px',borderRadius:999,border:'1px solid var(--border-color)',fontSize:'.78rem'}}><strong>● {status}</strong> · {lastUpdated ? `Updated ${Math.max(0, Math.round((Date.now()-lastUpdated.getTime())/1000))}s ago` : 'Waiting for telemetry'}</div>
        </section>
        {loading ? <Loader/> : <div style={{display:'flex',flexDirection:'column',gap:20}}>
            <section className="kpi-grid">
                <Mini label="CPU Usage" value={value(telemetry?.cpuUsage,'%')} icon="ph-cpu"/>
                <Mini label="Memory Usage" value={value(telemetry?.memoryUsage,'%')} icon="ph-memory"/>
                <Mini label="Disk Usage" value={value(telemetry?.diskUsage,'%')} icon="ph-hard-drives"/>
                <Mini label="Processes" value={value(telemetry?.processCount)} icon="ph-list-numbers"/>
                <Mini label="System Uptime" value={fmtDuration(telemetry?.uptime)} icon="ph-clock"/>
                <Mini label="PostgreSQL" value={telemetry?.dbHealth || 'Data unavailable'} icon="ph-database"/>
            </section>
            <section className="charts-grid" style={{display:'grid',gridTemplateColumns:'2fr 1fr',gap:16}}>
                <div className="panel-card"><h2 className="panel-title">Live Resource Utilization <span className="panel-subtitle">SSE telemetry</span></h2>
                    {history.length ? <ResponsiveContainer width="100%" height={260}><AreaChart data={history}><CartesianGrid strokeDasharray="3 3"/><XAxis dataKey="time"/><YAxis domain={[0,100]}/><Tooltip/><Area type="monotone" dataKey="cpu" name="CPU %" fillOpacity={0.15} strokeWidth={2}/><Area type="monotone" dataKey="memory" name="Memory %" fillOpacity={0.1} strokeWidth={2}/></AreaChart></ResponsiveContainer> : <Empty text="Waiting for telemetry"/>}
                </div>
                <div className="panel-card"><h2 className="panel-title">Host Details</h2><Info label="Hostname" v={telemetry?.hostname}/><Info label="OS" v={telemetry?.os}/><Info label="OS Version" v={telemetry?.osVersion}/><Info label="Architecture" v={telemetry?.architecture}/><Info label="Processor" v={telemetry?.processor}/><Info label="CPU Cores" v={telemetry?.cpuCores}/><Info label="Logical Processors" v={telemetry?.logicalProcessors}/></div>
            </section>
            <section className="charts-grid" style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:16}}>
                <div className="panel-card"><h2 className="panel-title">Memory &amp; Disk Capacity</h2><Info label="RAM Total" v={fmtBytes(telemetry?.memoryTotal)}/><Info label="RAM Used" v={fmtBytes(telemetry?.memoryUsed)}/><Info label="RAM Available" v={fmtBytes(telemetry?.memoryAvailable)}/><Info label="Disk Total" v={fmtBytes(telemetry?.diskTotal)}/><Info label="Disk Used" v={fmtBytes(telemetry?.diskUsed)}/><Info label="Disk Free" v={fmtBytes(telemetry?.diskFree)}/></div>
                <div className="panel-card"><h2 className="panel-title">PostgreSQL &amp; JVM</h2><Info label="DB Size" v={fmtBytes(telemetry?.databaseSizeBytes)}/><Info label="DB Connections" v={typeof telemetry?.dbActiveConnections==='number' ? `${telemetry.dbActiveConnections}/${telemetry.dbMaxConnections} (${dbUtil}%)` : telemetry?.dbActiveConnections}/><Info label="JVM Heap" v={typeof telemetry?.jvmHeapUsed==='number' ? `${fmtBytes(telemetry.jvmHeapUsed)} / ${fmtBytes(telemetry.jvmHeapMax)}` : telemetry?.jvmHeapUsed}/><Info label="JVM Threads" v={telemetry?.jvmThreadCount}/><Info label="GC Collections" v={telemetry?.jvmGcCollections}/><Info label="JVM Uptime" v={typeof telemetry?.jvmUptime==='number' ? `${Math.round(telemetry.jvmUptime/1000)}s` : telemetry?.jvmUptime}/></div>
            </section>
            <section className="panel-card"><h2 className="panel-title">Network Interfaces</h2><div className="table-wrapper"><table className="data-table"><thead><tr><th>Interface</th><th>Status</th><th>IPv4</th><th>Received</th><th>Sent</th></tr></thead><tbody>{(telemetry?.networkInterfaces || []).map((n,i)=><tr key={i}><td>{n.displayName || n.name}</td><td>{n.status}</td><td>{(n.ipv4||[]).join(', ') || 'Data unavailable'}</td><td>{fmtBytes(n.bytesReceived)}</td><td>{fmtBytes(n.bytesSent)}</td></tr>)}{!telemetry?.networkInterfaces?.length && <tr><td colSpan="5" style={{textAlign:'center'}}>Data unavailable</td></tr>}</tbody></table></div></section>
            <section className="panel-card"><h2 className="panel-title">Top Processes <span className="panel-subtitle">Read-only</span></h2><div className="table-wrapper"><table className="data-table"><thead><tr><th>PID</th><th>Process</th><th>CPU</th><th>Memory</th><th>Executable</th></tr></thead><tbody>{processes.map(p=><tr key={p.pid}><td>{p.pid}</td><td>{p.name}</td><td>{Number(p.cpuUsage).toFixed(2)}%</td><td>{fmtBytes(p.memoryBytes)}</td><td style={{maxWidth:320,overflow:'hidden',textOverflow:'ellipsis'}}>{p.executable}</td></tr>)}{!processes.length && <tr><td colSpan="5" style={{textAlign:'center'}}>Waiting for telemetry</td></tr>}</tbody></table></div></section>
            <section className="panel-card"><h2 className="panel-title">Recent Security Alerts</h2><div className="table-wrapper"><table className="data-table"><thead><tr><th>Severity</th><th>Message</th><th>Source</th><th>Timestamp</th></tr></thead><tbody>{alerts.map(a=><tr key={a.id}><td>{a.severity}</td><td>{a.title}</td><td>{a.source}</td><td>{new Date(a.timestamp).toLocaleString()}</td></tr>)}{!alerts.length&&<tr><td colSpan="4" style={{textAlign:'center'}}>No alerts detected</td></tr>}</tbody></table></div></section>
        </div>}
    </DashboardLayout>;
}
function Mini({label,value,icon}){return <div className="kpi-card blue"><div className="kpi-card-header"><span className="kpi-card-title">{label}</span><i className={`ph ${icon} kpi-card-icon`}/></div><div className="kpi-card-value" style={{fontSize:'1.35rem'}}>{value}</div></div>}
function Info({label,v}){return <div style={{display:'flex',justifyContent:'space-between',gap:12,padding:'9px 0',borderBottom:'1px solid var(--border-color)',fontSize:'.82rem'}}><span style={{color:'var(--text-muted)'}}>{label}</span><strong style={{textAlign:'right'}}>{v===null||v===undefined||v===''?'Data unavailable':v}</strong></div>}
function Empty({text}){return <div style={{padding:'70px 0',textAlign:'center',color:'var(--text-muted)'}}>{text}</div>}
