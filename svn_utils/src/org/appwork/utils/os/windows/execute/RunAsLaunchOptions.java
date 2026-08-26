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

import org.appwork.utils.LogCallback;

/**
 * Launch settings for {@link RunAsHelper} / {@link RunAsProcessLauncher} (working directory, wait/pipes, ShellExecuteEx options for
 * {@link RunAsHelper#runUACElevated}).
 */
public final class RunAsLaunchOptions {

    public static final RunAsLaunchOptions DEFAULT = builder().build();

    private final File        workingDir;
    private final boolean     waitFor;
    private final LogCallback logCallback;
    /**
     * For {@link RunAsHelper#runUACElevated}: show the child window ({@link com.sun.jna.platform.win32.WinUser#SW_SHOW}) when {@code true}.
     */
    private final boolean     showWindow;
    /**
     * For {@link RunAsHelper#runUACElevated}: set {@link com.sun.jna.platform.win32.Shell32#SEE_MASK_FLAG_NO_UI} on ShellExecuteEx.
     */
    private final boolean     noErrorUI;

    private RunAsLaunchOptions(File workingDir, boolean waitFor, LogCallback logCallback, boolean showWindow, boolean noErrorUI) {
        this.workingDir = workingDir;
        this.waitFor = waitFor;
        this.logCallback = logCallback;
        this.showWindow = showWindow;
        this.noErrorUI = noErrorUI;
    }

    public File getWorkingDir() {
        return workingDir;
    }

    public boolean isWaitFor() {
        return waitFor;
    }

    public LogCallback getLogCallback() {
        return logCallback;
    }

    public boolean isShowWindow() {
        return showWindow;
    }

    public boolean isNoErrorUI() {
        return noErrorUI;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private File        workingDir;
        private boolean     waitFor = true;
        private LogCallback logCallback;
        private boolean     showWindow         = false;
        private boolean     noErrorUI          = false;

        public Builder workingDir(File workingDir) {
            this.workingDir = workingDir;
            return this;
        }

        public Builder waitFor(boolean waitFor) {
            this.waitFor = waitFor;
            return this;
        }

        public Builder logCallback(LogCallback logCallback) {
            this.logCallback = logCallback;
            return this;
        }

        public Builder showWindow(boolean showWindow) {
            this.showWindow = showWindow;
            return this;
        }

        public Builder noErrorUI(boolean noErrorUI) {
            this.noErrorUI = noErrorUI;
            return this;
        }

        public RunAsLaunchOptions build() {
            return new RunAsLaunchOptions(workingDir, waitFor, logCallback, showWindow, noErrorUI);
        }
    }
}
