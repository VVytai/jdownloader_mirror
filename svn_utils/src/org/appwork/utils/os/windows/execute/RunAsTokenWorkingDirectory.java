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
import java.io.IOException;

import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.KnownFolders;
import com.sun.jna.platform.win32.ShlObj;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;

/**
 * Resolves and validates {@code lpCurrentDirectory} for token-based launches ({@link RunAsProcessLauncher}). When the caller sets
 * {@link RunAsLaunchOptions#getWorkingDir()}, the target user's primary token must be able to use that directory as a working directory;
 * otherwise launch fails before {@code CreateProcess*}.
 */
final class RunAsTokenWorkingDirectory {
    /** Minimum rights to use a folder as process current directory (list, traverse, read attributes, create file). */
    private static final int DIRECTORY_WORKING_ACCESS = WinNT.FILE_LIST_DIRECTORY | WinNT.FILE_READ_ATTRIBUTES | WinNT.FILE_TRAVERSE | WinNT.FILE_ADD_FILE;

    private RunAsTokenWorkingDirectory() {
    }

    /**
     * @param primaryToken
     *            duplicated primary token for the child process user
     * @param workingDir
     *            optional caller working directory; when {@code null}, the target user's profile folder is used
     * @return absolute path for {@code lpCurrentDirectory}
     */
    static String resolveForPrimaryToken(final HANDLE primaryToken, final File workingDir) throws IOException {
        if (primaryToken == null) {
            throw new IllegalArgumentException("primaryToken is required");
        }
        if (workingDir != null) {
            final String path = workingDir.getAbsolutePath();
            if (path == null || path.trim().length() == 0) {
                throw new IllegalArgumentException("workingDir path is empty");
            }
            final File asFile = new File(path);
            if (!asFile.isDirectory()) {
                throw new IllegalArgumentException("workingDir is not a directory: " + path);
            }
            assertAccessibleAsToken(primaryToken, path);
            return path;
        }
        return resolveDefaultProfileDirectory(primaryToken);
    }

    private static String resolveDefaultProfileDirectory(final HANDLE primaryToken) throws IOException {
        try {
            RunAsHelper.ensureWindowsAndJna();
            final String profile = RunAsHelper.getKnownFolderPath(primaryToken, KnownFolders.FOLDERID_Profile, ShlObj.KNOWN_FOLDER_FLAG.NONE.getFlag());
            if (profile == null || profile.trim().length() == 0) {
                throw new IOException("Cannot resolve profile folder for target user (empty path)");
            }
            return profile.trim();
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException("Cannot resolve profile folder for target user: " + t.getMessage(), t);
        }
    }

    private static void assertAccessibleAsToken(final HANDLE primaryToken, final String absolutePath) throws IOException {
        if (!Advapi32.INSTANCE.ImpersonateLoggedOnUser(primaryToken)) {
            final int gle = Kernel32.INSTANCE.GetLastError();
            throw new IOException("workingDir not accessible for target user (ImpersonateLoggedOnUser failed, Win32 " + gle + "): " + absolutePath);
        }
        try {
            final int attrs = Kernel32.INSTANCE.GetFileAttributes(absolutePath);
            if (attrs == WinBase.INVALID_FILE_ATTRIBUTES) {
                final int gle = Kernel32.INSTANCE.GetLastError();
                throw new IOException("workingDir not accessible for target user (GetFileAttributes failed, Win32 " + gle + "): " + absolutePath);
            }
            if ((attrs & WinNT.FILE_ATTRIBUTE_DIRECTORY) == 0) {
                throw new IOException("workingDir not accessible for target user (not a directory): " + absolutePath);
            }
            HANDLE hDir = null;
            try {
                hDir = Kernel32.INSTANCE.CreateFile(absolutePath, DIRECTORY_WORKING_ACCESS, WinNT.FILE_SHARE_READ | WinNT.FILE_SHARE_WRITE | WinNT.FILE_SHARE_DELETE, null, WinNT.OPEN_EXISTING, WinNT.FILE_FLAG_BACKUP_SEMANTICS, null);
                if (hDir == null || Pointer.nativeValue(hDir.getPointer()) == Pointer.nativeValue(WinBase.INVALID_HANDLE_VALUE.getPointer())) {
                    final int gle = Kernel32.INSTANCE.GetLastError();
                    throw new IOException("workingDir not accessible for target user (CreateFile failed, Win32 " + gle + "): " + absolutePath);
                }
            } finally {
                if (hDir != null && Pointer.nativeValue(hDir.getPointer()) != Pointer.nativeValue(WinBase.INVALID_HANDLE_VALUE.getPointer())) {
                    Kernel32.INSTANCE.CloseHandle(hDir);
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException("workingDir not accessible for target user: " + absolutePath + " (" + t.getMessage() + ")", t);
        } finally {
            Advapi32.INSTANCE.RevertToSelf();
        }
    }
}
