/**
 *
 * ====================================================================================================================================================
 *         "AppWork Utilities" License
 *         Copyright (c) 2009-2026, AppWork GmbH <e-mail@appwork.org>
 *         Spalter Strasse 58, 91183 Abenberg, Germany
 *         (License header abbreviated; see project license.)
 * ==================================================================================================================================================== */
package org.appwork.utils.os.windows.execute;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.appwork.jna.windows.Kernel32Ext;
import org.appwork.jna.windows.Wtsapi32Ext;
import org.appwork.loggingv3.LogV3;
import org.appwork.utils.os.WindowsUtils;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * Resolves interactive session user tokens (WTS / Shell_TrayWnd) for {@link RunAsHelper} session-owner launches.
 */
public final class SessionUserTokens {

    private static final int PROCESS_QUERY_INFORMATION = 0x0400;
    private static volatile boolean JNA_DEBUG_INITIALIZED = false;

    private SessionUserTokens() {
    }

    /**
     * Tries {@code WTSQueryUserToken} for the session, then the Explorer {@code Shell_TrayWnd} token only if that process belongs to the same WTS
     * session id (strict correlation).
     *
     * @param sessionId
     *            target session (not {@code 0xFFFFFFFF})
     * @return user token; caller must {@link Kernel32#CloseHandle}
     */
    public static WinNT.HANDLE openUserTokenForSession(int sessionId) {
        if (sessionId < 0 || sessionId == (int) 0xFFFFFFFFL) {
            throw new IllegalArgumentException("Invalid WTS session id: " + sessionId);
        }
        WinNT.HANDLE wts = queryWtsUserToken(sessionId);
        if (wts != null) {
            return wts;
        }
        return openShellTrayUserTokenForSession(sessionId);
    }

    private static WinNT.HANDLE queryWtsUserToken(int sessionId) {
        ensureJnaDebugOutput();
        final PointerByReference token = new PointerByReference();
        RunAsWin32ApiTrace.in("SessionUserTokens", "WTSQueryUserToken", "sessionId=" + sessionId);
        final boolean ok = Wtsapi32Ext.INSTANCE.WTSQueryUserToken(sessionId, token);
        final int gle = Kernel32.INSTANCE.GetLastError();
        RunAsWin32ApiTrace.out("SessionUserTokens", "WTSQueryUserToken", ok, gle);
        if (!ok) {
            LogV3.info("SessionUserTokens: WTSQueryUserToken failed for session " + sessionId + ", lastError=" + gle);
            return null;
        }
        LogV3.info("SessionUserTokens: WTSQueryUserToken succeeded for session " + sessionId);
        return new WinNT.HANDLE(token.getValue());
    }

    private static void ensureJnaDebugOutput() {
        if (JNA_DEBUG_INITIALIZED) {
            return;
        }
        synchronized (SessionUserTokens.class) {
            if (JNA_DEBUG_INITIALIZED) {
                return;
            }
            if (System.getProperty("jna.debug_load") == null) {
                System.setProperty("jna.debug_load", "true");
            }
            if (System.getProperty("jna.debug_load.jna") == null) {
                System.setProperty("jna.debug_load.jna", "true");
            }
            final Logger jnaLogger = Logger.getLogger("com.sun.jna");
            jnaLogger.setLevel(Level.ALL);
            final Handler[] handlers = Logger.getLogger("").getHandlers();
            if (handlers != null) {
                for (final Handler handler : handlers) {
                    if (handler != null) {
                        handler.setLevel(Level.ALL);
                    }
                }
            }
            LogV3.info("SessionUserTokens: JNA debug enabled (jna.debug_load=" + System.getProperty("jna.debug_load") + ", jna.debug_load.jna=" + System.getProperty("jna.debug_load.jna") + ", jna.library.path=" + System.getProperty("jna.library.path") + ", jna.boot.library.path=" + System.getProperty("jna.boot.library.path") + ", java.library.path=" + System.getProperty("java.library.path") + ")");
            JNA_DEBUG_INITIALIZED = true;
        }
    }

    private static WinNT.HANDLE openShellTrayUserTokenForSession(int expectedSessionId) {
        RunAsWin32ApiTrace.in("SessionUserTokens", "FindWindow", "className=Shell_TrayWnd windowName=null");
        HWND hwnd = User32.INSTANCE.FindWindow("Shell_TrayWnd", null);
        int gleFw = Kernel32.INSTANCE.GetLastError();
        RunAsWin32ApiTrace.out("SessionUserTokens", "FindWindow", hwnd != null, gleFw);
        if (hwnd == null) {
            throw new IllegalStateException("Shell_TrayWnd not found (Explorer not running?)" + buildCurrentContextSuffix(expectedSessionId));
        }
        IntByReference pid = new IntByReference();
        RunAsWin32ApiTrace.in("SessionUserTokens", "GetWindowThreadProcessId", "hwnd=" + hwnd);
        int tid = User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
        int gleGw = Kernel32.INSTANCE.GetLastError();
        RunAsWin32ApiTrace.out("SessionUserTokens", "GetWindowThreadProcessId", tid != 0, gleGw);
        if (tid == 0) {
            throw new Win32Exception(Native.getLastError());
        }
        IntByReference sessionRef = new IntByReference();
        RunAsWin32ApiTrace.in("SessionUserTokens", "ProcessIdToSessionId", "pid=" + pid.getValue());
        boolean pts = Kernel32Ext.INSTANCE.ProcessIdToSessionId(pid.getValue(), sessionRef);
        int glePts = Kernel32.INSTANCE.GetLastError();
        RunAsWin32ApiTrace.out("SessionUserTokens", "ProcessIdToSessionId", pts, glePts);
        if (!pts) {
            throw new Win32Exception(glePts);
        }
        int traySession = sessionRef.getValue();
        if (traySession != expectedSessionId) {
            throw new IllegalStateException("Shell_TrayWnd process session " + traySession + " does not match target WTS session " + expectedSessionId);
        }
        LogV3.info("SessionUserTokens: falling back to Shell_TrayWnd token (pid=" + pid.getValue() + ", session=" + traySession + ")");
        RunAsWin32ApiTrace.in("SessionUserTokens", "OpenProcess", "access=PROCESS_QUERY_INFORMATION pid=" + pid.getValue());
        WinNT.HANDLE hProcess = Kernel32.INSTANCE.OpenProcess(PROCESS_QUERY_INFORMATION, false, pid.getValue());
        int gleOp = Kernel32.INSTANCE.GetLastError();
        RunAsWin32ApiTrace.out("SessionUserTokens", "OpenProcess", hProcess != null, gleOp);
        if (hProcess == null) {
            throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
        }
        try {
            WinNT.HANDLEByReference hToken = new WinNT.HANDLEByReference();
            RunAsWin32ApiTrace.in("SessionUserTokens", "OpenProcessToken", "hProcess=" + RunAsWin32ApiTrace.h(hProcess) + " access=TOKEN_DUPLICATE|TOKEN_QUERY");
            final boolean opened = Advapi32.INSTANCE.OpenProcessToken(hProcess, WinNT.TOKEN_DUPLICATE | WinNT.TOKEN_QUERY, hToken);
            int gleOpt = Kernel32.INSTANCE.GetLastError();
            RunAsWin32ApiTrace.out("SessionUserTokens", "OpenProcessToken", opened, gleOpt);
            if (!opened) {
                throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
            }
            return hToken.getValue();
        } finally {
            RunAsWin32ApiTrace.in("SessionUserTokens", "CloseHandle", "hProcess explorer pid=" + pid.getValue());
            boolean closed = Kernel32.INSTANCE.CloseHandle(hProcess);
            RunAsWin32ApiTrace.out("SessionUserTokens", "CloseHandle(hProcess)", closed, Kernel32.INSTANCE.GetLastError());
        }
    }

    /**
     * Adds current process context details to exceptions so session mismatch/debugging is easier in service/system/rdp setups.
     */
    private static String buildCurrentContextSuffix(int expectedSessionId) {
        String currentSid = "?";
        String currentSession = "?";
        String elevated = "?";
        String localSystem = "?";
        String activeConsoleSession = "?";
        try {
            currentSession = String.valueOf(WindowsUtils.getCurrentProcessSessionId());
        } catch (Throwable ignore) {
        }
        try {
            currentSid = String.valueOf(WindowsUtils.getCurrentUserSID());
        } catch (Throwable ignore) {
        }
        try {
            elevated = String.valueOf(WindowsUtils.isElevated());
        } catch (Throwable ignore) {
        }
        try {
            localSystem = String.valueOf(WindowsUtils.isRunningAsLocalSystem());
        } catch (Throwable ignore) {
        }
        try {
            activeConsoleSession = String.valueOf(Kernel32Ext.INSTANCE.WTSGetActiveConsoleSessionId());
        } catch (Throwable ignore) {
        }
        return " [expectedSession=" + expectedSessionId + ", currentSession=" + currentSession + ", currentSid=" + currentSid + ", elevated=" + elevated + ", localSystem=" + localSystem + ", activeConsoleSession=" + activeConsoleSession + "]";
    }
}
