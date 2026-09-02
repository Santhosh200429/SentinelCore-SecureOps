import { useEffect, useMemo, useState } from 'react';
import { Activity, RefreshCw, ShieldAlert } from 'lucide-react';
import DashboardLayout from '../../layouts/DashboardLayout.jsx';
import securityEventService from '../../services/securityEventService.js';

export default function SecurityEventsPage(){
 const [events,setEvents]=useState([]),[loading,setLoading]=useState(true),[filter,setFilter]=useState('');
 async function load(){setLoading(true);try{const r=await securityEventService.getAll();setEvents(Array.isArray(r.data)?r.data:[]);}finally{setLoading(false);}}
 useEffect(()=>{load();const t=setInterval(load,10000);return()=>clearInterval(t);},[]);
 const filtered=useMemo(()=>filter?events.filter(e=>String(e.severity||'').toLowerCase()===filter.toLowerCase()):events,[events,filter]);
 return <DashboardLayout><div style={{padding:'24px'}}>
  <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:20}}><div><div style={{fontSize:12,fontWeight:700,letterSpacing:1,color:'var(--highlight-blue)'}}>SOC OPERATIONS</div><h1 style={{margin:'6px 0'}}>Security Events</h1><p style={{margin:0,color:'var(--text-muted)'}}>Normalized endpoint security events and detection evidence.</p></div><button className="btn" onClick={load}><RefreshCw size={16}/> Refresh</button></div>
  <div style={{display:'flex',gap:8,marginBottom:16}}>{['','HIGH','MEDIUM','INFO'].map(x=><button key={x} className={`filter-btn ${filter===x?'active':''}`} onClick={()=>setFilter(x)}>{x||'All'} {x?`(${events.filter(e=>String(e.severity).toUpperCase()===x).length})`:''}</button>)}</div>
  <section className="panel-card"><div className="panel-title" style={{display:'flex',alignItems:'center',gap:8}}><ShieldAlert size={18}/> Event stream <span style={{marginLeft:'auto',fontSize:12,color:'var(--text-muted)'}}><Activity size={13}/> auto-refresh 10s</span></div>
   <div className="table-wrapper"><table className="data-table"><thead><tr><th>Severity</th><th>Event</th><th>Category</th><th>Asset</th><th>Source</th><th>Timestamp</th></tr></thead><tbody>{loading?<tr><td colSpan="6" style={{textAlign:'center'}}>Loading…</td></tr>:filtered.map((e,i)=><tr key={e.id||i}><td><b>{e.severity||'INFO'}</b></td><td>{e.message||e.eventId}</td><td>{e.category||'SECURITY'}</td><td>{e.hostname||e.assetId||'—'}</td><td>{e.source||'SentinelCore'}</td><td>{e.timestamp?new Date(e.timestamp).toLocaleString():'—'}</td></tr>)}{!loading&&!filtered.length&&<tr><td colSpan="6" style={{textAlign:'center'}}>No security events collected yet.</td></tr>}</tbody></table></div>
  </section>
 </div></DashboardLayout>
}
