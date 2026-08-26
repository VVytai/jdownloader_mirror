/**
 *
 * ====================================================================================================================================================
 *         "AppWork Utilities" License
 *         Copyright (c) 2009-2026, AppWork GmbH <e-mail@appwork.org>
 *         Spalter Strasse 58, 91183 Abenberg, Germany
 *         (License header abbreviated; see project license.)
 * ==================================================================================================================================================== */
package org.appwork.utils.os.windows.execute;

import java.io.File;

import org.appwork.utils.os.JNAProcessInfo;
import org.appwork.utils.parser.ShellParser;
import org.appwork.utils.parser.ShellParser.Style;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinUser;

/**
 * ShellExecuteEx {@code runas} (UAC elevation prompt). Used by {@link RunAsHelper#runUACElevated}.
 */
final class RunAsUacLauncher {

    private RunAsUacLauncher() {
    }

    /**
     * @return process info with open handle ({@link Shell32#SEE_MASK_NOCLOSEPROCESS}); caller must {@link JNAProcessInfo#close()}
     */
    static JNAProcessInfo shellExecuteRunAs(String[] command, RunAsLaunchOptions options) throws Win32Exception {
        if (command == null || command.length == 0) {
            throw new IllegalArgumentException("command cannot be null or empty");
        }
        final RunAsLaunchOptions opts = options != null ? options : RunAsLaunchOptions.DEFAULT;
        final String binary = command[0];
        final String[] params = new String[command.length - 1];
        if (params.length > 0) {
            System.arraycopy(command, 1, params, 0, params.length);
        }
        final String lpFile = ShellParser.createCommandLine(Style.WINDOWS, binary);
        final String lpParameters = buildShellExecuteParameters(binary, params);
        final File workDir = opts.getWorkingDir();
        final String lpDirectory = workDir != null ? workDir.getAbsolutePath() : null;

        final Shell32.SHELLEXECUTEINFO sei = new Shell32.SHELLEXECUTEINFO();
        sei.cbSize = sei.size();
        sei.lpVerb = "runas";
        sei.lpFile = lpFile;
        sei.lpParameters = lpParameters.length() > 0 ? lpParameters : null;
        sei.lpDirectory = lpDirectory;
        sei.nShow = opts.isShowWindow() ? WinUser.SW_SHOW : WinUser.SW_HIDE;
        int fMask = Shell32.SEE_MASK_NOCLOSEPROCESS;
        if (opts.isNoErrorUI()) {
            fMask |= Shell32.SEE_MASK_FLAG_NO_UI;
        }
        sei.fMask = fMask;

        RunAsWin32ApiTrace.in("RunAsUacLauncher", "ShellExecuteEx", "verb=runas file=\"" + lpFile + "\" params=\"" + (lpParameters.length() > 120 ? lpParameters.substring(0, 120) + "..." : lpParameters) + "\" dir=" + lpDirectory + " nShow=" + sei.nShow);
        final boolean ok = Shell32.INSTANCE.ShellExecuteEx(sei);
        final int gle = Kernel32.INSTANCE.GetLastError();
        RunAsWin32ApiTrace.out("RunAsUacLauncher", "ShellExecuteEx", ok, gle);
        if (!ok) {
            throw new Win32Exception(gle);
        }
        final JNAProcessInfo info = new JNAProcessInfo(sei.hProcess);
        info.setCommandLine(lpParameters.length() > 0 ? lpFile + " " + lpParameters : lpFile);
        info.setWorkingDirectory(lpDirectory);
        return info;
    }

    /**
     * ShellExecuteEx parameter string for {@code lpParameters} (not parsed like CreateProcess command lines).
     */
    private static String buildShellExecuteParameters(String binary, String[] params) {
        if (isCmdExe(binary) && params.length == 2 && "/c".equals(params[0])) {
            /*
             * cmd.exe /c: ShellParser uses \" for CreateProcess; ShellExecuteEx + runas + elevated cmd need caret-escaped quotes.
             */
            final String cmdString = params[1];
            return "/c \"" + cmdString.replace("\"", "^\"") + "\"";
        }
        return ShellParser.createCommandLine(Style.WINDOWS, params);
    }

    private static boolean isCmdExe(String binary) {
        if (binary == null) {
            return false;
        }
        String name = binary;
        final int last = binary.lastIndexOf('\\');
        if (last >= 0 && last + 1 < binary.length()) {
            name = binary.substring(last + 1);
        }
        return "cmd.exe".equalsIgnoreCase(name) || "cmd".equalsIgnoreCase(name);
    }
}
