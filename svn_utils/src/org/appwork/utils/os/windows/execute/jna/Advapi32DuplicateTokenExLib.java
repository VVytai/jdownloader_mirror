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
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinNT.HANDLEByReference;
import org.appwork.jna.windows.JNAOptions;

/**
 * Minimal JNA binding for {@code DuplicateTokenEx} (primary token for {@code CreateProcessAsUser}).
 */
public interface Advapi32DuplicateTokenExLib extends Library {

    Advapi32DuplicateTokenExLib INSTANCE = Native.load("advapi32", Advapi32DuplicateTokenExLib.class, JNAOptions.UNICODE_SYSTEM_DLLS_ONLY);

    boolean DuplicateTokenEx(HANDLE hExistingToken, int dwDesiredAccess, Pointer lpTokenAttributes,
            int impersonationLevel, int tokenType, HANDLEByReference phNewToken);
}
