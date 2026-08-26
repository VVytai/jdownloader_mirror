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
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import org.appwork.jna.windows.JNAOptions;

/**
 * Minimal JNA binding for {@code CreateProcessAsUserW} with a writable {@code lpCommandLine} buffer (see MSDN).
 */
public interface Advapi32CreateProcessLib extends Library {

    Advapi32CreateProcessLib INSTANCE = Native.load("advapi32", Advapi32CreateProcessLib.class, JNAOptions.UNICODE_SYSTEM_DLLS_ONLY);

    boolean CreateProcessAsUser(HANDLE hToken, Pointer lpApplicationName, Pointer lpCommandLine,
            WinBase.SECURITY_ATTRIBUTES lpProcessAttributes, WinBase.SECURITY_ATTRIBUTES lpThreadAttributes,
            boolean bInheritHandles, int dwCreationFlags, Pointer lpEnvironment, String lpCurrentDirectory,
            WinBase.STARTUPINFO lpStartupInfo, WinBase.PROCESS_INFORMATION lpProcessInformation);
}
