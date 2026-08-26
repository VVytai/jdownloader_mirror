/**
 *
 * ====================================================================================================================================================
 *         "AppWork Utilities" License
 *         Copyright (c) 2009-2026, AppWork GmbH <e-mail@appwork.org>
 *         Spalter Strasse 58, 91183 Abenberg, Germany
 *         (License header abbreviated; see project license.)
 * ==================================================================================================================================================== */
package org.appwork.utils.os.windows.execute.jna;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import org.appwork.jna.windows.JNAOptions;

/**
 * Minimal JNA binding for {@code CreateProcessWithTokenW}. This API is preferred for elevated->non-elevated same-user launch because it
 * typically requires only {@code SeImpersonatePrivilege}, while {@code CreateProcessAsUserW} often fails for normal admin tokens.
 */
public interface Advapi32CreateProcessWithTokenLib extends Library {
    Advapi32CreateProcessWithTokenLib INSTANCE = Native.load("advapi32", Advapi32CreateProcessWithTokenLib.class, JNAOptions.UNICODE_SYSTEM_DLLS_ONLY);

    int LOGON_WITH_PROFILE = 0x00000001;

    boolean CreateProcessWithTokenW(HANDLE hToken, int dwLogonFlags, String lpApplicationName, WString lpCommandLine, int dwCreationFlags, Pointer lpEnvironment, String lpCurrentDirectory,
                                    WinBase.STARTUPINFO lpStartupInfo, WinBase.PROCESS_INFORMATION lpProcessInformation);
}
