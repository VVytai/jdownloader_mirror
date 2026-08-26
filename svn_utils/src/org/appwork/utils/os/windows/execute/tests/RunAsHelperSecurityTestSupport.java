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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.appwork.testframework.executer.ElevatedTestTask;
import org.appwork.utils.Exceptions;
import org.appwork.utils.IO;
import org.appwork.utils.os.WindowsUtils;
import org.appwork.utils.processes.ProcessBuilderFactory;
import org.appwork.utils.processes.ProcessOutput;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinNT;

/**
 * Shared env-map probes and Windows user helpers for {@link org.appwork.utils.os.windows.execute.RunAsHelper} security tests.
 * <p>
 * Probes return the full process environment {@link HashMap}; host AWTest code evaluates markers (e.g.
 * {@link #ENV_PROBE_NAME}).
 */
final class RunAsHelperSecurityTestSupport {
    static final String ENV_PROBE_NAME = "APWORK_RUNAS_HELPER_ENV_PROBE";

    /**
     * PowerShell: dump all Process env vars as NAME=VALUE lines (newlines in values collapsed to space).
     * <p>
     * Use single-quoted {@code '[\r\n]'} only — embedded double quotes in {@code -Command} are stripped by Windows
     * CreateProcess/ProcessBuilder command-line quoting and break {@code -replace}.
     */
    private static final String PS_ENV_DUMP_ALL = "Get-ChildItem Env: | ForEach-Object { $n=$_.Name; $v=[string]$_.Value; if($null -eq $v){$v=''}; $v=$v -replace '[\\r\\n]',' '; Write-Output ($n+'='+$v) }";

    private RunAsHelperSecurityTestSupport() {
    }

    /**
     * Capture current process environment via native {@code GetEnvironmentStrings} (reflects
     * {@link #setProcessEnvironmentVariable}; unlike often-cached {@link System#getenv()}).
     */
    static HashMap<String, String> captureCurrentProcessEnvironment() {
        final Map<String, String> nativeEnv = Kernel32Util.getEnvironmentVariables();
        final HashMap<String, String> copy = new HashMap<String, String>();
        if (nativeEnv != null) {
            final Iterator<Entry<String, String>> it = nativeEnv.entrySet().iterator();
            while (it.hasNext()) {
                final Entry<String, String> e = it.next();
                if (e.getKey() != null) {
                    copy.put(e.getKey(), e.getValue() != null ? e.getValue() : "");
                }
            }
        }
        return copy;
    }

    static String[] powershellEnvDumpAllArgv() {
        return new String[] { "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", PS_ENV_DUMP_ALL };
    }

    static String powershellEnvDumpAllToFileCommand(final String outputFilePath) {
        final String escapedPath = outputFilePath != null ? outputFilePath.replace("'", "''") : "";
        // Single-quoted regex / [char]10: avoid embedded \" in -Command (stripped by Windows argv quoting).
        return "$p='" + escapedPath + "';$lines=@();Get-ChildItem Env: | ForEach-Object { $n=$_.Name; $v=[string]$_.Value; if($null -eq $v){$v=''}; $v=$v -replace '[\\r\\n]',' '; $lines+=($n+'='+$v) }; Set-Content -LiteralPath $p -Value ($lines -join ([char]10)) -Encoding ASCII";
    }

    static String[] powershellEnvDumpAllToFileArgv(final File outputFile) {
        return new String[] { "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", powershellEnvDumpAllToFileCommand(outputFile.getAbsolutePath()) };
    }

    /**
     * Parse NAME=VALUE dump lines into a map. First {@code '='} separates name and value. Lines without {@code '='} are skipped.
     */
    static HashMap<String, String> parseEnvDumpStdout(final String stdout) {
        final HashMap<String, String> map = new HashMap<String, String>();
        if (stdout == null || stdout.length() == 0) {
            return map;
        }
        final String[] lines = stdout.split("\r?\n", -1);
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            if (line == null || line.length() == 0) {
                continue;
            }
            final int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            final String name = line.substring(0, eq);
            final String value = line.substring(eq + 1);
            map.put(name, value);
        }
        return map;
    }

    /** Value of {@code name} in map, or {@code null} if missing / empty. */
    static String envValue(final Map<String, String> env, final String name) {
        if (env == null || name == null) {
            return null;
        }
        final String v = env.get(name);
        if (v == null || v.length() == 0) {
            return null;
        }
        return v;
    }

    static String firstLineOfStdout(final String stdout) {
        if (stdout == null || stdout.trim().length() == 0) {
            return "";
        }
        return stdout.split("\r?\n", -1)[0].trim();
    }

    static void setProcessEnvironmentVariable(final String name, final String value) {
        if (name == null || name.trim().length() == 0) {
            throw new IllegalArgumentException("env name required");
        }
        if (!Kernel32.INSTANCE.SetEnvironmentVariable(name, value)) {
            throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
        }
    }

    static void waitForProcessExit(final int pid) throws Exception {
        if (pid <= 0) {
            throw new IllegalArgumentException("invalid pid: " + pid);
        }
        final WinNT.HANDLE h = Kernel32.INSTANCE.OpenProcess(WinNT.SYNCHRONIZE, false, pid);
        if (h == null) {
            throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
        }
        try {
            final int rc = Kernel32.INSTANCE.WaitForSingleObject(h, Kernel32.INFINITE);
            if (rc != 0) {
                throw new Exception("WaitForSingleObject failed for pid " + pid + ", rc=" + rc);
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(h);
        }
    }

    static File newAsyncProbeOutputFile(final String markerSegment) throws Exception {
        final String tempRoot = System.getenv("TEMP");
        final File parent = tempRoot != null && tempRoot.trim().length() > 0 ? new File(tempRoot.trim()) : new File("C:\\Windows\\Temp");
        final File f = new File(parent, "APWORK_RUNAS_ASYNC_ENV_" + markerSegment + ".txt");
        if (f.exists() && !f.delete()) {
            logDeleteIgnored(f);
        }
        return f;
    }

    private static void logDeleteIgnored(final File f) {
        // best-effort cleanup only
    }

    /** Snapshot of current JVM process env (native block). exitCode 0 on success. */
    static EnvProbeRun captureCurrentProcessEnvProbe() {
        final EnvProbeRun run = new EnvProbeRun();
        try {
            run.env = captureCurrentProcessEnvironment();
            run.exitCode = 0;
            run.stdout = "";
            run.stderr = "";
        } catch (Throwable t) {
            run.env = new HashMap<String, String>();
            run.exitCode = -1;
            run.stdout = "";
            run.stderr = Exceptions.getStackTrace(t);
        }
        return run;
    }

    /** Child-process dump-all stdout → env map. */
    static EnvProbeRun captureEnvMapProbe(final ProcessOutput out) {
        final EnvProbeRun run = new EnvProbeRun();
        if (out == null) {
            run.exitCode = -1;
            run.stdout = "";
            run.stderr = "null ProcessOutput";
            run.env = new HashMap<String, String>();
            return run;
        }
        run.exitCode = out.getExitCode();
        run.stdout = out.getStdOutString() != null ? out.getStdOutString() : "";
        run.stderr = out.getErrOutString() != null ? out.getErrOutString() : "";
        run.env = parseEnvDumpStdout(run.stdout);
        return run;
    }

    static EnvProbeRun readEnvMapProbeFile(final File probeFile) throws Exception {
        final EnvProbeRun run = new EnvProbeRun();
        if (!probeFile.isFile()) {
            run.exitCode = -1;
            run.stdout = "";
            run.stderr = "probe file missing: " + probeFile.getAbsolutePath();
            run.env = new HashMap<String, String>();
            return run;
        }
        run.exitCode = 0;
        run.stdout = IO.readFileToString(probeFile);
        run.stderr = "";
        run.env = parseEnvDumpStdout(run.stdout);
        return run;
    }

    static EnvProbeRun captureEnvMapProbeFromThrowable(final Throwable t) {
        final EnvProbeRun run = new EnvProbeRun();
        run.exitCode = -1;
        run.stdout = "";
        run.stderr = Exceptions.getStackTrace(t);
        run.env = new HashMap<String, String>();
        return run;
    }

    static void deleteTestUserQuietly(final String userName) {
        try {
            org.appwork.testframework.executer.AdminExecuter.runAsAdmin(new DeleteTestWindowsUserTask(userName), org.appwork.storage.TypeRef.OBJECT, org.appwork.testframework.executer.ProcessOptions.DEFAULT);
        } catch (Throwable t) {
            // cleanup best-effort
        }
    }

    /**
     * Full process environment from a probe. Host evaluates {@link #env} (e.g. {@link #envValue(Map, String)} for
     * {@link #ENV_PROBE_NAME}).
     */
    static final class EnvProbeRun implements Serializable {
        private static final long   serialVersionUID = 2L;
        public int                  exitCode;
        public String               stdout;
        public String               stderr;
        /** Full process environment; never null after capture helpers. */
        public HashMap<String, String> env;

        public EnvProbeRun() {
            this.env = new HashMap<String, String>();
        }
    }

    static final class CreateTestWindowsUserTask implements ElevatedTestTask {
        private static final long serialVersionUID = 1L;
        private final String      userName;
        private final String      password;
        private final boolean     addToAdministrators;

        CreateTestWindowsUserTask(final String userName, final String password, final boolean addToAdministrators) {
            this.userName = userName != null ? userName : "";
            this.password = password != null ? password : "";
            this.addToAdministrators = addToAdministrators;
        }

        @Override
        public Serializable run() throws Exception {
            if (userName.length() == 0) {
                throw new Exception("CreateTestWindowsUserTask: empty userName");
            }
            final ProcessOutput existsProbe = ProcessBuilderFactory.runCommand("net", "user", userName);
            if (existsProbe.getExitCode() == 0) {
                final ProcessOutput del = ProcessBuilderFactory.runCommand("net", "user", userName, "/delete");
                if (del.getExitCode() != 0) {
                    throw new Exception("net user /delete failed: " + del.getErrOutString());
                }
            }
            final ProcessOutput add = ProcessBuilderFactory.runCommand("net", "user", userName, password, "/add", "/expires:never", "/passwordchg:no");
            if (add.getExitCode() != 0) {
                throw new Exception("net user add failed: " + add.getErrOutString());
            }
            if (addToAdministrators) {
                final String builtinAdminSid = WindowsUtils.SID.SID_BUILTIN_ADMINISTRATORS.sid;
                final Advapi32Util.Account adminGroup = Advapi32Util.getAccountBySid(builtinAdminSid);
                final ProcessOutput groupAdd = ProcessBuilderFactory.runCommand("net", "localgroup", adminGroup.name, userName, "/add");
                if (groupAdd.getExitCode() != 0) {
                    throw new Exception("net localgroup add failed: " + groupAdd.getErrOutString());
                }
            }
            return Boolean.TRUE;
        }
    }

    static final class DeleteTestWindowsUserTask implements ElevatedTestTask {
        private static final long serialVersionUID = 1L;
        private final String      userName;

        DeleteTestWindowsUserTask(final String userName) {
            this.userName = userName != null ? userName : "";
        }

        @Override
        public Serializable run() throws Exception {
            ProcessBuilderFactory.runCommand("net", "user", userName, "/delete");
            return null;
        }
    }
}
