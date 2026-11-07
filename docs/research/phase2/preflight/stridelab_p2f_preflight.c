/* Frozen research source only; not compiled or executed by the v7 correction task. */
#include <windows.h>
#include <winternl.h>
#include <stdio.h>

int main(void) {
    RTL_OSVERSIONINFOW version = {0};
    SYSTEM_INFO info = {0};
    version.dwOSVersionInfoSize = sizeof(version);
    if (RtlGetVersion(&version) != 0) return 1;
    GetNativeSystemInfo(&info);
    if (printf("OS_VERSION=%lu.%lu.%lu\n", version.dwMajorVersion, version.dwMinorVersion, version.dwBuildNumber) < 0) return 1;
    if (printf("NATIVE_ARCH=%u\n", (unsigned)info.wProcessorArchitecture) < 0) return 1;
    return 0;
}
