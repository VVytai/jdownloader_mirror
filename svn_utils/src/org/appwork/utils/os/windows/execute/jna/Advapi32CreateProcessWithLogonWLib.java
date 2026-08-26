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
import com.sun.jna.platform.win32.WinBase.PROCESS_INFORMATION;
import com.sun.jna.platform.win32.WinBase.STARTUPINFO;
import org.appwork.jna.windows.JNAOptions;

/**
 * Minimal JNA binding for {@code CreateProcessWithLogonW} (e.g. {@code org.appwork.testframework.executer.RunTaskAsUserLauncher} fallback
 * when {@code LogonUser}/{@code CreateProcessAsUserW} is not used).
 */
public interface Advapi32CreateProcessWithLogonWLib extends Library {

    Advapi32CreateProcessWithLogonWLib INSTANCE = Native.load("advapi32", Advapi32CreateProcessWithLogonWLib.class, JNAOptions.UNICODE_SYSTEM_DLLS_ONLY);

    /** LOGON_WITH_PROFILE */
    int LOGON_WITH_PROFILE = 0x00000001;

    /**
     * @param lpDomain
     *            optional; {@code null} or empty for local account {@code lpUsername}
     * @param lpCommandLine
     *            writable UTF-16 command line buffer (see MSDN)
     */
    boolean CreateProcessWithLogonW(String lpUsername, String lpDomain, String lpPassword, int dwLogonFlags, String lpApplicationName,
            Pointer lpCommandLine, int dwCreationFlags, Pointer lpEnvironment, String lpCurrentDirectory, STARTUPINFO lpStartupInfo,
            PROCESS_INFORMATION lpProcessInformation);
}
