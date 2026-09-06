param([Parameter(Mandatory = $true)][int]$PreviewPid, [Parameter(Mandatory = $true)][string]$OutputPath)
$ErrorActionPreference = 'Stop'
$client = Get-CimInstance Win32_Process -Filter "ProcessId=$PreviewPid"
if (-not $client -or $client.CommandLine -notmatch 'harbor-review-client') { throw 'Only the isolated architectural review client may be photographed' }
Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Drawing;
using System.Runtime.InteropServices;
using System.Threading;
public static class HarborPhotograph {
    delegate bool Callback(IntPtr h, IntPtr p);
    [StructLayout(LayoutKind.Sequential)] struct Rect { public int L,T,R,B; }
    [StructLayout(LayoutKind.Sequential)] struct Point { public int X,Y; }
    [DllImport("user32.dll", CharSet=CharSet.Unicode)] static extern IntPtr OpenDesktop(string n,uint f,bool i,uint a);
    [DllImport("user32.dll")] static extern bool SetThreadDesktop(IntPtr d);
    [DllImport("user32.dll")] static extern bool CloseDesktop(IntPtr d);
    [DllImport("user32.dll")] static extern bool EnumWindows(Callback c,IntPtr p);
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h,out uint p);
    [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll")] static extern bool GetWindowRect(IntPtr h,out Rect r);
    [DllImport("user32.dll")] static extern bool GetClientRect(IntPtr h,out Rect r);
    [DllImport("user32.dll")] static extern bool ClientToScreen(IntPtr h,ref Point p);
    [DllImport("user32.dll")] static extern bool ShowWindow(IntPtr h,int c);
    [DllImport("user32.dll")] static extern bool SetWindowPos(IntPtr h,IntPtr a,int x,int y,int w,int hgt,uint f);
    public static void Capture(int pid,string path) {
        Exception failure=null; bool found=false;
        var worker=new Thread(()=>{
            IntPtr desktop=OpenDesktop("Default",0,false,0x1FF);
            try {
                if(desktop==IntPtr.Zero || !SetThreadDesktop(desktop)) throw new Exception("Default desktop unavailable");
                EnumWindows((hwnd,p)=>{
                    uint owner; GetWindowThreadProcessId(hwnd,out owner);
                    if(owner!=pid || !IsWindowVisible(hwnd))return true;
                    found=true; ShowWindow(hwnd,9);
                    try {
                        SetWindowPos(hwnd,new IntPtr(-1),0,0,0,0,0x13);
                        Thread.Sleep(500);
                        Rect rect; GetClientRect(hwnd,out rect);
                        Point origin=new Point(); ClientToScreen(hwnd,ref origin);
                        using(var bitmap=new Bitmap(rect.R-rect.L,rect.B-rect.T)) {
                            using(var graphics=Graphics.FromImage(bitmap)) graphics.CopyFromScreen(origin.X,origin.Y,0,0,bitmap.Size);
                            bitmap.Save(path,System.Drawing.Imaging.ImageFormat.Png);
                        }
                    } finally { SetWindowPos(hwnd,new IntPtr(-2),0,0,0,0,0x13); }
                    return false;
                },IntPtr.Zero);
            } catch(Exception e){failure=e;} finally {if(desktop!=IntPtr.Zero)CloseDesktop(desktop);}
        });
        worker.Start();worker.Join();if(failure!=null)throw failure;
        if(!found)throw new Exception("No visible preview window");
    }
}
'@
[HarborPhotograph]::Capture($PreviewPid, $OutputPath)
Write-Output "HARBOR_PHOTOGRAPH=$OutputPath"
