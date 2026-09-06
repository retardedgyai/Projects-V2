param([int]$PreviewPid, [switch]$ListMonitors)
$ErrorActionPreference = 'Stop'
if (-not $ListMonitors) {
    $client = Get-CimInstance Win32_Process -Filter "ProcessId=$PreviewPid"
    if (-not $client -or $client.CommandLine -notmatch 'harbor-review-client') {
        throw 'Only the isolated harbor review client may be moved'
    }
}
Add-Type @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Threading;
public static class HarborMonitorPlacement {
    [StructLayout(LayoutKind.Sequential)] public struct Rect { public int L,T,R,B; }
    [StructLayout(LayoutKind.Sequential)] struct MonitorInfo { public int Size; public Rect Bounds,Work; public uint Flags; }
    delegate bool MonitorCallback(IntPtr m,IntPtr h,ref Rect r,IntPtr p);
    delegate bool WindowCallback(IntPtr h,IntPtr p);
    [DllImport("user32.dll",CharSet=CharSet.Unicode)] static extern IntPtr OpenDesktop(string n,uint f,bool i,uint a);
    [DllImport("user32.dll")] static extern bool SetThreadDesktop(IntPtr d);
    [DllImport("user32.dll")] static extern bool CloseDesktop(IntPtr d);
    [DllImport("user32.dll")] static extern bool EnumDisplayMonitors(IntPtr h,IntPtr clip,MonitorCallback c,IntPtr p);
    [DllImport("user32.dll",CharSet=CharSet.Unicode)] static extern bool GetMonitorInfo(IntPtr m,ref MonitorInfo i);
    [DllImport("user32.dll")] static extern bool EnumWindows(WindowCallback c,IntPtr p);
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h,out uint p);
    [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll")] static extern bool SetWindowPos(IntPtr h,IntPtr a,int x,int y,int w,int height,uint f);
    [DllImport("user32.dll")] static extern bool GetWindowRect(IntPtr h,out Rect r);
    [DllImport("user32.dll")] static extern bool SetProcessDPIAware();
    public static string Run(int pid,bool listOnly) {
        string result=null; Exception error=null;
        var worker=new Thread(()=> {
            IntPtr desktop=OpenDesktop("Default",0,false,0x1FF);
            try {
                if(desktop==IntPtr.Zero || !SetThreadDesktop(desktop)) throw new Exception("Default desktop unavailable");
                SetProcessDPIAware();
                var monitors=new List<MonitorInfo>();
                EnumDisplayMonitors(IntPtr.Zero,IntPtr.Zero,(IntPtr m,IntPtr h,ref Rect r,IntPtr p)=> {
                    var info=new MonitorInfo();info.Size=Marshal.SizeOf(info);
                    if(GetMonitorInfo(m,ref info))monitors.Add(info);
                    return true;
                },IntPtr.Zero);
                if(listOnly) {
                    var rows=new List<string>();
                    foreach(var m in monitors)rows.Add(String.Format("primary={0} bounds={1},{2},{3},{4} work={5},{6},{7},{8}",
                        (m.Flags&1)!=0,m.Bounds.L,m.Bounds.T,m.Bounds.R,m.Bounds.B,m.Work.L,m.Work.T,m.Work.R,m.Work.B));
                    result=String.Join("\n",rows);return;
                }
                var secondary=monitors.FindAll(m=>(m.Flags&1)==0);
                if(secondary.Count!=1)throw new Exception("Expected exactly one secondary monitor; refusing to choose the primary or guess");
                var area=secondary[0].Work;
                int width=Math.Min(1600,area.R-area.L-32),height=Math.Min(939,area.B-area.T-32);
                int x=area.L+(area.R-area.L-width)/2,y=area.T+(area.B-area.T-height)/2;
                int stable=0;
                for(int attempt=0;attempt<450;attempt++) {
                    bool found=false;
                    EnumWindows((hwnd,p)=> {
                        uint owner;GetWindowThreadProcessId(hwnd,out owner);
                        if(owner!=pid || !IsWindowVisible(hwnd))return true;
                        found=true;
                        // SWP_NOZORDER | SWP_NOACTIVATE: never steal focus from the other monitor.
                        if(!SetWindowPos(hwnd,IntPtr.Zero,x,y,width,height,0x14))throw new Exception("Window placement failed");
                        Rect actual;GetWindowRect(hwnd,out actual);
                        if(actual.L<area.L || actual.T<area.T || actual.R>area.R || actual.B>area.B)
                            throw new Exception("Preview is not contained within the secondary monitor");
                        result=String.Format("HARBOR_SECONDARY_WINDOW pid={0} rect={1},{2},{3},{4}",pid,actual.L,actual.T,actual.R,actual.B);
                        return false;
                    },IntPtr.Zero);
                    if(found && ++stable>=15)return;
                    Thread.Sleep(100);
                }
                throw new Exception("No visible harbor window appeared within 45 seconds");
            } catch(Exception ex) {error=ex;} finally {if(desktop!=IntPtr.Zero)CloseDesktop(desktop);}
        });
        worker.Start();worker.Join();if(error!=null)throw error;return result;
    }
}
'@
[HarborMonitorPlacement]::Run($PreviewPid, $ListMonitors.IsPresent)
