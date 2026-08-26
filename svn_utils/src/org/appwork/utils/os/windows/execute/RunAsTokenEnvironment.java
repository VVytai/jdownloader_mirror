/**
 *
 * ====================================================================================================================================================
 *         "AppWork Utilities" License
 *         Copyright (c) 2009-2026, AppWork GmbH <e-mail@appwork.org>
 *         Spalter Strasse 58, 91183 Abenberg, Germany
 *         (License header abbreviated; see project license.)
 * ==================================================================================================================================================== */
package org.appwork.utils.os.windows.execute;

import org.appwork.utils.os.windows.execute.jna.UserenvRunAsLib;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.PointerByReference;

/**
 * Builds / frees a Unicode environment block for token-based process creation ({@link RunAsProcessLauncher}).
 */
public final class RunAsTokenEnvironment {
    private RunAsTokenEnvironment() {
    }

    /**
     * @param primaryToken
     *            duplicated primary token for the target user
     * @param inheritFromCaller
     *            {@code false} so the child does not merge the current process environment (e.g. LocalSystem caller vars)
     * @return block for {@code CreateProcessAsUserW} / {@code CreateProcessWithTokenW}; call {@link #destroy(Pointer)} when done
     */
    public static Pointer createForPrimaryToken(final HANDLE primaryToken, final boolean inheritFromCaller) {
        if (primaryToken == null) {
            throw new IllegalArgumentException("primaryToken is required");
        }
        final PointerByReference envRef = new PointerByReference();
        RunAsWin32ApiTrace.in("RunAsTokenEnvironment", "CreateEnvironmentBlock", "token=" + RunAsWin32ApiTrace.h(primaryToken) + " inherit=" + inheritFromCaller);
        final boolean ok = UserenvRunAsLib.INSTANCE.CreateEnvironmentBlock(envRef, primaryToken, inheritFromCaller);
        final int gle = Kernel32.INSTANCE.GetLastError();
        RunAsWin32ApiTrace.out("RunAsTokenEnvironment", "CreateEnvironmentBlock", ok, gle);
        if (!ok) {
            throw new Win32Exception(gle);
        }
        final Pointer env = envRef.getValue();
        if (env == null) {
            throw new Win32Exception(gle != 0 ? gle : 0x8); // ERROR_NOT_ENOUGH_MEMORY fallback
        }
        return env;
    }

    public static void destroy(final Pointer envBlock) {
        if (envBlock == null) {
            return;
        }
        RunAsWin32ApiTrace.in("RunAsTokenEnvironment", "DestroyEnvironmentBlock", "env=" + envBlock);
        final boolean ok = UserenvRunAsLib.INSTANCE.DestroyEnvironmentBlock(envBlock);
        RunAsWin32ApiTrace.out("RunAsTokenEnvironment", "DestroyEnvironmentBlock", ok, Kernel32.INSTANCE.GetLastError());
    }
}
