/**
 *
 * ====================================================================================================================================================
 *         "AppWork Utilities" License
 *         Copyright (c) 2009-2026, AppWork GmbH <e-mail@appwork.org>
 *         Spalter Strasse 58, 91183 Abenberg, Germany
 *         (License header abbreviated; see project license.)
 * ==================================================================================================================================================== */
package org.appwork.utils.os.windows.execute.tests;

import java.io.File;
import java.io.Serializable;
import java.util.EnumSet;
import java.util.Set;

import org.appwork.JNAHelper;
import org.appwork.storage.TypeRef;
import org.appwork.testframework.AWTest;
import org.appwork.testframework.TestTag;
import org.appwork.testframework.TestDependency;
import org.appwork.testframework.executer.AdminExecuter;
import org.appwork.testframework.executer.ElevatedTestTask;
import org.appwork.testframework.executer.ProcessOptions;
import org.appwork.utils.os.CrossSystem;
import org.appwork.utils.os.WindowsUtils;
import org.appwork.utils.os.windows.execute.InteractiveSessionOwner;
import org.appwork.utils.os.windows.execute.RunAsHelper;
import org.appwork.utils.os.windows.execute.RunAsLaunchOptions;
import org.appwork.utils.processes.ProcessOutput;

import com.sun.jna.platform.win32.KnownFolders;
import com.sun.jna.platform.win32.ShlObj;

/**
 * Default working directory when {@link RunAsLaunchOptions#getWorkingDir()} is omitted for session-owner launch via
 * {@link RunAsHelper#runInSession}.
 * <p>
 * We expect default cwd == session owner profile directory, not Local System {@code SystemRoot\\System32} or the launching JVM temp path.
 * <p>
 * Flow:
 *
 * <pre>
 * Step 1: Capture interactive session-owner profile path (InteractiveOwnerBaseline-style baseline from WTS session)
 * Step 2: Execute task as Local System in that WTS session
 * Step 3: Launch session-owner cwd probe with no workingDir in RunAsLaunchOptions
 * Step 4: Assert session-owner cwd equals profile OR starts with profile + "\\"
 * Step 5: Assert session-owner cwd is not Local System System32
 * </pre>
 */
@TestDependency({ "org.appwork.utils.os.windows.execute.RunAsHelper", "org.appwork.testframework.executer.AdminExecuter", "org.appwork.testframework.executer.AdminHelperProcess" })
public class RunAsHelperSysCwdDefaultTest extends AWTest {
    private static final String                      PS_PROBE_CWD  = "Write-Output ((Get-Location).Path)";
    private static final TypeRef<CwdDefaultSnapshot> TYPE_SNAPSHOT = new TypeRef<CwdDefaultSnapshot>() {
                                                                   };

    public static void main(String[] args) {
        run();
    }

    @Override
    public boolean isMaintenance() {
        return false;
    }

    @Override
    public Set<TestTag> getTags() {
        return EnumSet.of(TestTag.UAC);
    }

    @Override
    public void runTest() throws Exception {
        if (!CrossSystem.isWindows() || !JNAHelper.isJNAAvailable()) {
            logInfoAnyway("RunAsHelperSysCwdDefaultTest: Windows + JNA required, skipped.");
            return;
        }
        final int interactiveSessionId = WindowsUtils.getCurrentProcessSessionId();
        if (interactiveSessionId < 0) {
            logInfoAnyway("RunAsHelperSysCwdDefaultTest: no WTS session, skipped.");
            return;
        }
        // Step 1: Resolve expected session-owner profile (baseline for this interactive WTS session)
        final String expectedSessionOwnerProfile = resolveSessionOwnerProfilePath(interactiveSessionId);
        // Step 2: Execute task as Local System
        // WARNING: THIS TEST does not AUTO-Resolve the owner but simply uses #interactiveSessionId
        final CwdDefaultSnapshot snapshot = AdminExecuter.runAsLocalSystem(new LocalSystemSessionOwnerCwdDefaultTask(interactiveSessionId), TYPE_SNAPSHOT, ProcessOptions.DEFAULT);
        // Local System task must return a snapshot with profile + cwd probe.
        assertTrue(snapshot != null, "snapshot null");
        // Task must have run as Local System — default-cwd rules are for SYSTEM -> session owner.
        assertTrue(snapshot.isLocalSystem, "localSystem must be SYSTEM");
        // Profile path from the task is the baseline for default workingDir.
        assertTrue(snapshot.sessionOwnerProfilePath != null && snapshot.sessionOwnerProfilePath.length() > 0, "sessionOwnerProfilePath missing");
        // Task profile must match host-resolved session-owner profile for this WTS session.
        assertEquals(normalizeWinPath(expectedSessionOwnerProfile), normalizeWinPath(snapshot.sessionOwnerProfilePath), "expected sessionOwner profile baseline");
        // Session-owner probe (no workingDir) must succeed so we can read default cwd.
        assertProbeRunOk("sessionOwner-cwd", snapshot.sessionOwnerCwdProbe);
        final String sessionOwnerCwd = normalizeWinPath(snapshot.sessionOwnerCwdProbe.firstLine);
        final String profileNorm = normalizeWinPath(snapshot.sessionOwnerProfilePath);
        // Default cwd line from powershell must be non-empty.
        assertTrue(sessionOwnerCwd.length() > 0, "sessionOwner cwd empty");
        // Default cwd must equal session-owner profile or a subfolder — not C:\Users or System32.
        assertTrue(sessionOwnerCwd.equals(profileNorm) || sessionOwnerCwd.startsWith(profileNorm + "\\"), "sessionOwner cwd must equal profile or be under profile, profile=" + snapshot.sessionOwnerProfilePath + ", cwd=" + sessionOwnerCwd);
        // Step 5: We expect default cwd == session owner profile, not LocalSystem System32
        if (snapshot.localSystemSystem32Path != null && snapshot.localSystemSystem32Path.length() > 0) {
            // Must not inherit Local System System32 as default cwd when workingDir is omitted.
            assertTrue(!sessionOwnerCwd.equals(normalizeWinPath(snapshot.localSystemSystem32Path)), "sessionOwner cwd must not be localSystem System32, sessionOwner=" + sessionOwnerCwd + ", system32=" + snapshot.localSystemSystem32Path);
        }
    }

    private static String resolveSessionOwnerProfilePath(final int interactiveSessionId) throws Exception {
        final InteractiveSessionOwner owner = InteractiveSessionOwner.openForSession(interactiveSessionId);
        try {
            return RunAsHelper.getKnownFolderPath(owner.getUserTokenHandle(), KnownFolders.FOLDERID_Profile, ShlObj.KNOWN_FOLDER_FLAG.NONE.getFlag());
        } finally {
            owner.close();
        }
    }

    private void assertProbeRunOk(final String label, final CwdProbeRun run) throws Exception {
        // Probe must have been captured — missing run means session-owner launch never ran.
        assertTrue(run != null, label + ": run null");
        // Default-cwd probe must exit 0 (powershell Get-Location).
        assertEquals(0, run.exitCode, label + ": exit, stderr=" + run.stderr);
        // Need a non-empty first line — the default cwd path we assert against profile.
        assertTrue(run.firstLine != null && run.firstLine.length() > 0, label + ": cwd line missing");
    }

    static String normalizeWinPath(final String path) {
        if (path == null) {
            return "";
        }
        String p = path.trim().replace('/', '\\');
        while (p.length() > 3 && p.endsWith("\\")) {
            p = p.substring(0, p.length() - 1);
        }
        if (p.length() > 0) {
            p = RunAsHelperSysCwdTest.expandWinPathForCompare(p);
        }
        return p.toLowerCase();
    }

    public static final class CwdProbeRun implements Serializable {
        private static final long serialVersionUID = 1L;
        public int                exitCode;
        public String             stdout;
        public String             stderr;
        public String             firstLine;

        public CwdProbeRun() {
        }
    }

    public static final class CwdDefaultSnapshot implements Serializable {
        private static final long serialVersionUID = 1L;
        public boolean            isLocalSystem;
        public String             sessionOwnerProfilePath;
        public String             localSystemSystem32Path;
        public CwdProbeRun        sessionOwnerCwdProbe;

        public CwdDefaultSnapshot() {
        }
    }

    private static final class LocalSystemSessionOwnerCwdDefaultTask implements ElevatedTestTask {
        private static final long serialVersionUID = 1L;
        private final int         interactiveSessionId;

        LocalSystemSessionOwnerCwdDefaultTask(final int interactiveSessionId) {
            this.interactiveSessionId = interactiveSessionId;
        }

        @Override
        public Serializable run() throws Exception {
            if (!WindowsUtils.isRunningAsLocalSystem()) {
                throw new Exception("LocalSystemSessionOwnerCwdDefaultTask requires SYSTEM");
            }
            final CwdDefaultSnapshot snapshot = new CwdDefaultSnapshot();
            snapshot.isLocalSystem = true;
            final String systemRoot = System.getenv("SystemRoot");
            if (systemRoot != null && systemRoot.trim().length() > 0) {
                snapshot.localSystemSystem32Path = new File(systemRoot.trim(), "System32").getAbsolutePath();
            }
            // Step 1 (in task): resolve session-owner profile for this WTS session
            final InteractiveSessionOwner owner = InteractiveSessionOwner.openForSession(interactiveSessionId);
            try {
                snapshot.sessionOwnerProfilePath = RunAsHelper.getKnownFolderPath(owner.getUserTokenHandle(), KnownFolders.FOLDERID_Profile, ShlObj.KNOWN_FOLDER_FLAG.NONE.getFlag());
            } finally {
                owner.close();
            }
            // Step 3: runInSession with no workingDir — default cwd must be session-owner profile
            final RunAsLaunchOptions opts = RunAsLaunchOptions.builder().waitFor(true).build();
            final ProcessOutput out = RunAsHelper.runInSession(interactiveSessionId, new String[] { "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", PS_PROBE_CWD }, opts);
            snapshot.sessionOwnerCwdProbe = captureCwdProbe(out);
            return snapshot;
        }
    }

    private static CwdProbeRun captureCwdProbe(final ProcessOutput out) {
        final CwdProbeRun run = new CwdProbeRun();
        if (out == null) {
            run.exitCode = -1;
            run.firstLine = "";
            return run;
        }
        run.exitCode = out.getExitCode();
        run.stdout = out.getStdOutString() != null ? out.getStdOutString() : "";
        run.stderr = out.getErrOutString() != null ? out.getErrOutString() : "";
        run.firstLine = RunAsHelperSecurityTestSupport.firstLineOfStdout(run.stdout);
        return run;
    }
}
