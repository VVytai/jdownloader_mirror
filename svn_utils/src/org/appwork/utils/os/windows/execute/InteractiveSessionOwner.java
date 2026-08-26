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
import org.appwork.utils.os.WindowsUtils;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;

/**
 * Result of resolving the interactive session owner (WTS session id, account SID, user token) for {@link RunAsHelper}. Step 2 in the run-as
 * pipeline: open the session user token via {@link SessionUserTokens#openUserTokenForSession(int)} and read the account. Call
 * {@link #close()} when done so the token handle is released.
 */
public final class InteractiveSessionOwner {
    private final int    sessionId;
    private final String ownerSid;
    private final String ownerAccountName;
    private WinNT.HANDLE userToken;

    private InteractiveSessionOwner(int sessionId, String ownerSid, String ownerAccountName, WinNT.HANDLE userToken) {
        this.sessionId = sessionId;
        this.ownerSid = ownerSid;
        this.ownerAccountName = ownerAccountName;
        this.userToken = userToken;
    }

    /**
     * Resolves the owner of the current process WTS session (same session id as {@link WindowsUtils#getCurrentProcessSessionId()}).
     */
    public static InteractiveSessionOwner openForCurrentProcess() throws Exception {
        int sid = WindowsUtils.getCurrentProcessSessionId();
        if (sid < 0) {
            throw new IllegalStateException("Cannot resolve WTS session id for current process (ProcessIdToSessionId failed).");
        }
        return openForSession(sid);
    }

    /**
     * Opens the interactive owner for the given WTS session id (e.g. when the caller is LocalSystem and must target a console/RDP session).
     * Token resolution correlates token and session id (WTSQueryUserToken, then Shell_TrayWnd only if same session).
     */
    public static InteractiveSessionOwner openForSession(int sessionId) throws Exception {
        RunAsWin32ApiTrace.in("InteractiveSessionOwner", "openForSession", "sessionId=" + sessionId);
        WinNT.HANDLE h = SessionUserTokens.openUserTokenForSession(sessionId);
        if (RunAsWin32ApiTrace.isEnabled()) {
            LogV3.info(RunAsWin32ApiTrace.PREFIX + " InteractiveSessionOwner INFO sessionUserToken=" + RunAsWin32ApiTrace.h(h));
        }
        try {
            Advapi32Util.Account a = Advapi32Util.getTokenAccount(h);
            if (a == null || a.sidString == null || a.sidString.trim().length() == 0) {
                throw new IllegalStateException("Cannot read account for session user token (session " + sessionId + ").");
            }
            String name = a.name != null ? a.name : "";
            return new InteractiveSessionOwner(sessionId, a.sidString.trim(), name, h);
        } catch (Throwable t) {
            closeHandleQuietly(h);
            if (t instanceof Exception) {
                throw (Exception) t;
            }
            throw new Exception(t);
        }
    }

    public int getSessionId() {
        return sessionId;
    }

    public String getOwnerSid() {
        return ownerSid;
    }

    /**
     * Account name from the token (may be empty on failure paths; SID is authoritative).
     */
    public String getOwnerAccountName() {
        return ownerAccountName;
    }

    /**
     * Session user token; owned by this instance until {@link #close()}. Do not close from outside.
     */
    public WinNT.HANDLE getUserTokenHandle() {
        return userToken;
    }

    public void close() {
        closeHandleQuietly(userToken);
        userToken = null;
    }

    private static void closeHandleQuietly(WinNT.HANDLE h) {
        if (h != null) {
            try {
                RunAsWin32ApiTrace.in("InteractiveSessionOwner", "CloseHandle", RunAsWin32ApiTrace.h(h));
                final boolean c = Kernel32.INSTANCE.CloseHandle(h);
                RunAsWin32ApiTrace.out("InteractiveSessionOwner", "CloseHandle", c, Kernel32.INSTANCE.GetLastError());
            } catch (Throwable t) {
                LogV3.log(t);
            }
        }
    }
}
