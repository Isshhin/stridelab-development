#include <windows.h>
#include <winternl.h>

typedef NTSTATUS (NTAPI *RtlGetVersionFn)(PRTL_OSVERSIONINFOW);
static char *dec(char *p, unsigned long v) { char *q=p; do { *q++=(char)('0'+v%10); v/=10; } while(v); for(char *a=p,*z=q-1;a<z;a++,z--){char t=*a;*a++=*z;*z=t;} return q; }
static int out(HANDLE h, char *p, unsigned long n) { DWORD w=0; return WriteConsoleA(h,p,n,&w,0) && w==n; }
__declspec(noreturn) void mainCRTStartup(void) {
    RTL_OSVERSIONINFOW v={0}; SYSTEM_INFO s={0}; char line[96], *p; HANDLE h;
    HMODULE ntdll=GetModuleHandleW(L"ntdll.dll"); RtlGetVersionFn f=(RtlGetVersionFn)GetProcAddress(ntdll,"RtlGetVersion");
    if(!f){ExitProcess(1);} v.dwOSVersionInfoSize=sizeof(v); if(f(&v)!=0){ExitProcess(1);} GetNativeSystemInfo(&s);
    h=GetStdHandle(STD_OUTPUT_HANDLE); if(h==INVALID_HANDLE_VALUE){ExitProcess(1);}
    p=line; p[0]='O';p[1]='S';p[2]='_';p[3]='V';p[4]='E';p[5]='R';p[6]='S';p[7]='I';p[8]='O';p[9]='N';p[10]='=';p+=11; p=dec(p,v.dwMajorVersion);*p++='.';p=dec(p,v.dwMinorVersion);*p++='.';p=dec(p,v.dwBuildNumber);*p++='\r';*p++='\n'; if(!out(h,line,(unsigned long)(p-line))){ExitProcess(1);}
    p=line; p[0]='N';p[1]='A';p[2]='T';p[3]='I';p[4]='V';p[5]='E';p[6]='_';p[7]='A';p[8]='R';p[9]='C';p[10]='H';p[11]='=';p+=12; p=dec(p,(unsigned long)s.wProcessorArchitecture);*p++='\r';*p++='\n'; if(!out(h,line,(unsigned long)(p-line))){ExitProcess(1);} ExitProcess(0);
}
