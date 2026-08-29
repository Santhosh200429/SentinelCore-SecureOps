import { useCallback, useEffect, useMemo, useState } from 'react';
import { Activity, AlertTriangle, ArrowUpRight, Bug, CheckCircle2, Clock3, Gauge, RefreshCw, Server, ShieldAlert, ShieldCheck } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import dashboardService from '../../services/dashboardService.js';
import assetService from '../../services/assetService.js';
import incidentService from '../../services/incidentService.js';
import vulnerabilityService from '../../services/vulnerabilityService.js';
import complianceService from '../../services/complianceService.js';
import alertService from '../../services/alertService.js';
import './securityCommandCenter.css';

const list = value => Array.isArray(value) ? value : [];
const number = value => Number.isFinite(Number(value)) ? Number(value) : 0;
const openStatus = value => !['resolved', 'closed'].includes(String(value || '').toLowerCase());

export default function SecurityCommandCenterPage() {
  const navigate = useNavigate();
  const [data, setData] = useState({ stats: null, assets: [], incidents: [], vulnerabilities: [], standards: [], alerts: [] });
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async (silent = false) => {
    silent ? setRefreshing(true) : setLoading(true);
    setError('');
    const results = await Promise.allSettled([
      dashboardService.getStats(), assetService.getAll(), incidentService.getAll(),
      vulnerabilityService.getAll(), complianceService.getStandards(), alertService.getLive(),
    ]);
    const value = (index, fallback) => results[index]?.status === 'fulfilled' ? results[index].value?.data : fallback;
    setData({
      stats: value(0, null), assets: list(value(1, [])), incidents: list(value(2, [])),
      vulnerabilities: list(value(3, [])), standards: list(value(4, [])), alerts: list(value(5, [])),
    });
    if (results.every(result => result.status === 'rejected')) setError('Unable to load command-center data. Check your login session and backend.');
    setLoading(false); setRefreshing(false);
  }, []);

  useEffect(() => {
    load();
    const timer = setInterval(() => load(true), 15000);
    return () => clearInterval(timer);
  }, [load]);

  const metrics = useMemo(() => {
    const { assets, incidents, vulnerabilities, standards } = data;
    const criticalAssets = assets.filter(a => String(a.status || '').toLowerCase() === 'critical').length;
    const healthyAssets = assets.filter(a => ['healthy', 'online', 'active'].includes(String(a.status || '').toLowerCase())).length;
    const activeIncidents = incidents.filter(i => openStatus(i.status));
    const criticalIncidents = activeIncidents.filter(i => String(i.severity || '').toLowerCase() === 'critical').length;
    const criticalVulnerabilities = vulnerabilities.filter(v => number(v.cvss) >= 9 || String(v.severity || '').toLowerCase() === 'critical').length;
    const complianceValues = standards.map(s => number(s.score ?? s.complianceScore ?? s.percentage)).filter(v => v > 0);
    const compliance = complianceValues.length ? Math.round(complianceValues.reduce((a,b) => a+b, 0) / complianceValues.length) : 0;
    let score = 100 - criticalAssets * 3 - criticalIncidents * 6 - criticalVulnerabilities * 2;
    if (assets.length) score -= Math.round(assets.reduce((sum, a) => sum + Math.max(0, number(a.cpuUsage)-80) * .12 + Math.max(0, number(a.memoryUsage)-85) * .12 + Math.max(0, number(a.diskUsage)-90) * .08, 0) / assets.length);
    const hasPostureData = assets.length > 0 || incidents.length > 0 || vulnerabilities.length > 0;
    return { criticalAssets, healthyAssets, activeIncidents: activeIncidents.length, criticalIncidents, criticalVulnerabilities, compliance, score: hasPostureData ? Math.max(0, Math.min(100, score)) : null };
  }, [data]);

  if (loading) return <div className="scc-loading"><RefreshCw className="scc-spin" /> Loading Security Command Center…</div>;

  return <div className="scc-page">
    <header className="scc-hero">
      <div><div className="scc-eyebrow"><Activity size={15}/> SECURITY OPERATIONS</div><h1>Security Command Center</h1><p>Unified operational visibility across SentinelCore security modules.</p></div>
      <button className="scc-refresh" onClick={() => load(true)} disabled={refreshing}><RefreshCw size={17} className={refreshing ? 'scc-spin' : ''}/> {refreshing ? 'Refreshing' : 'Refresh'}</button>
    </header>
    {error && <div className="scc-error"><AlertTriangle size={18}/>{error}</div>}

    <section className="scc-top-grid">
      <div className="scc-posture"><div className="scc-score"><strong>{metrics.score ?? '—'}</strong><span>{metrics.score == null ? '' : '/100'}</span></div><div><small>Overall security posture</small><h2>{metrics.score == null ? 'Waiting for telemetry' : metrics.score >= 80 ? 'Healthy' : metrics.score >= 60 ? 'Needs attention' : 'High risk'}</h2><p>{metrics.score == null ? 'Security posture will be calculated when operational data is available.' : 'Derived from current asset, incident and vulnerability data.'}</p></div></div>
      <Metric icon={<Server/>} label="Assets" value={data.assets.length} sub={`${metrics.healthyAssets} healthy · ${metrics.criticalAssets} critical`} tone="blue"/>
      <Metric icon={<ShieldAlert/>} label="Active incidents" value={metrics.activeIncidents} sub={`${metrics.criticalIncidents} critical`} tone="orange"/>
      <Metric icon={<Bug/>} label="Vulnerabilities" value={data.vulnerabilities.length} sub={`${metrics.criticalVulnerabilities} critical`} tone="purple"/>
    </section>

    <div className="scc-heading"><div><h2>Command overview</h2><p>Automatically refreshed every 15 seconds.</p></div></div>
    <section className="scc-panels">
      <Panel title="Infrastructure health" icon={<Gauge/>} action={() => navigate('/infrastructure')}>
        {data.assets.slice(0,6).map((asset, index) => { const score = Math.max(0, Math.round(100 - Math.max(0,number(asset.cpuUsage)-70)*.35 - Math.max(0,number(asset.memoryUsage)-75)*.3 - Math.max(0,number(asset.diskUsage)-80)*.2)); return <div className="health-row" key={asset.id ?? asset.assetId ?? index}><div><strong>{asset.name || asset.assetName || `Asset ${index+1}`}</strong><span>{asset.status || 'Unknown'}</span></div><div className="health-bar"><i style={{width:`${score}%`}}/></div><b>{score}</b></div>; })}
        {!data.assets.length && <Empty text="No assets available."/>}
      </Panel>
      <Panel title="Live security alerts" icon={<AlertTriangle/>} action={() => navigate('/infrastructure')}>
        {data.alerts.slice(0,6).map((alert,index) => <button className="scc-alert" key={alert.id ?? index} onClick={() => navigate('/infrastructure')}><span className={`alert-dot ${String(alert.severity || 'info').toLowerCase()}`}/><div><strong>{alert.title || 'Security alert'}</strong><small>{alert.source || 'SentinelCore'} · {alert.timestamp ? new Date(alert.timestamp).toLocaleString() : 'recent'}</small></div><ArrowUpRight size={15}/></button>)}
        {!data.alerts.length && <Empty text="No active alerts."/>}
      </Panel>
      <Panel title="Compliance posture" icon={<CheckCircle2/>} action={() => navigate('/compliance')}>
        <div className="compliance-main"><div><small>Average compliance</small><strong>{metrics.compliance}%</strong></div><CheckCircle2 size={40}/></div>
        {data.standards.slice(0,5).map((standard,index) => <div className="standard-row" key={index}><span>{standard.name || standard.standard || `Standard ${index+1}`}</span><b>{number(standard.score ?? standard.complianceScore ?? standard.percentage)}%</b></div>)}
        {!data.standards.length && <Empty text="No compliance data available."/>}
      </Panel>
      <Panel title="Incident watch" icon={<ShieldCheck/>} action={() => navigate('/incidents')}>
        {data.incidents.filter(i => openStatus(i.status)).slice(0,5).map((incident,index) => <button className="incident-row" key={incident.id ?? index} onClick={() => navigate('/incidents')}><span className={`severity ${String(incident.severity || 'medium').toLowerCase()}`}>{incident.severity || 'Medium'}</span><div><strong>{incident.title || incident.incidentId || `Incident ${index+1}`}</strong><small>{incident.status || 'Open'} · {incident.assignedTeam || 'Unassigned'}</small></div><Clock3 size={15}/></button>)}
        {!data.incidents.filter(i => openStatus(i.status)).length && <Empty text="No active incidents."/>}
      </Panel>
    </section>
    <div className="scc-note"><Activity size={14}/> Security Command Center aggregates the existing SentinelCore milestone APIs; it does not replace them.</div>
  </div>;
}

function Metric({icon,label,value,sub,tone}) { return <div className={`scc-metric ${tone}`}><div className="metric-icon">{icon}</div><div><small>{label}</small><strong>{value}</strong><span>{sub}</span></div></div>; }
function Panel({title,icon,action,children}) { return <section className="scc-panel"><div className="panel-head"><div className="panel-title"><span>{icon}</span><h3>{title}</h3></div><button onClick={action}>View <ArrowUpRight size={14}/></button></div>{children}</section>; }
function Empty({text}) { return <div className="scc-empty">{text}</div>; }
