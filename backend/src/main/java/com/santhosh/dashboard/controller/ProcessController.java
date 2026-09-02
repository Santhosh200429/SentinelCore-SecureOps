package com.santhosh.dashboard.controller;
import oshi.SystemInfo; import oshi.software.os.OSProcess; import oshi.software.os.OperatingSystem;
import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/infrastructure/processes")
public class ProcessController {
 @GetMapping @PreAuthorize("hasAuthority('ASSET_VIEW')") public List<Map<String,Object>> processes(){
  OperatingSystem os=new SystemInfo().getOperatingSystem();
  return os.getProcesses(null, OperatingSystem.ProcessSorting.CPU_DESC,20).stream().map(p->Map.<String,Object>of("pid",p.getProcessID(),"name",p.getName(),"cpuUsage",p.getProcessCpuLoadCumulative()*100,"memoryBytes",p.getResidentSetSize(),"executable",p.getPath()==null?"Data unavailable":p.getPath())).toList();
 }
}
