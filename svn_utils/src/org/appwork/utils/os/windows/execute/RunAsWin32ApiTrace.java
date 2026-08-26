/**
 *
 * ====================================================================================================================================================
 *         "AppWork Utilities" License
 *         Copyright (c) 2009-2026, AppWork GmbH <e-mail@appwork.org>
 *         Spalter Strasse 58, 91183 Abenberg, Germany
 *         (License header abbreviated; see project license.)
 * ==================================================================================================================================================== */
package org.appwork.utils.os.windows.execute;

import org.appwork.loggingv3.LogV3;
import org.appwork.loggingv3.NoLogSource;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT.HANDLE;

/**
 * Verbose, removable tracing for Win32/JNA calls on the run-as path. Prefix {@value #PREFIX}; grep logs for {@code RunAsApiTrace}.
 * <p>
 * Set system property {@value #SYSPROP_APITRACE} to {@code true} ({@code -Dappwork.runas.apitrace=true}) so code that checks {@link #isEnabled()}
 * emits additional detail lines (WaitForSingleObject return code, ReadFile byte counts, etc.). IN/OUT lines from {@code in}/{@code out} are not gated by this flag.
 */
public final class RunAsWin32ApiTrace {
    public static final String PREFIX       = "RunAsApiTrace";
    /** When {@code true} ({@link Boolean#getBoolean(String)}), supplemental {@code LogV3.info} diagnostics guarded by {@link #isEnabled()} are emitted. */
    public static final String SYSPROP_APITRACE = "appwork.runas.apitrace";

    private RunAsWin32ApiTrace() {
    }

    /**
     * @return {@link Boolean#getBoolean(String)} for {@value #SYSPROP_APITRACE} — pass {@code -Dappwork.runas.apitrace=true} to the JVM.
     */
    public static boolean isEnabled() {
        return Boolean.getBoolean(SYSPROP_APITRACE);
    }

    @NoLogSource
    public static void in(final String component, final String api, final String detail) {
        LogV3.info(PREFIX + " " + component + " IN  " + api + " " + (detail != null ? detail : ""));
    }

    /**
     * @param gle
     *            {@link Kernel32#GetLastError()} captured immediately after the native call (before further Win32 calls).
     */
    @NoLogSource
    public static void out(final String component, final String api, final boolean ok, final int gle) {
        LogV3.info(PREFIX + " " + component + " OUT " + api + " ok=" + ok + " gle=" + gle);
    }

    @NoLogSource
    public static String h(final HANDLE h) {
        if (h == null || h.getPointer() == null) {
            return "null";
        }
        return "0x" + Long.toHexString(Pointer.nativeValue(h.getPointer()));
    }
}
