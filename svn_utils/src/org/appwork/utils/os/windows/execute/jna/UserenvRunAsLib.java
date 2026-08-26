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
import com.sun.jna.ptr.PointerByReference;
import org.appwork.jna.windows.JNAOptions;

/**
 * {@code userenv.dll} bindings for {@link org.appwork.utils.os.windows.execute.RunAsTokenEnvironment} /
 * {@link org.appwork.utils.os.windows.execute.RunAsProcessLauncher}: build a process environment block for a user token
 * ({@link #CreateEnvironmentBlock}) instead of inheriting the caller process environment when {@code lpEnvironment} is {@code null} on
 * {@code CreateProcessAsUserW} / {@code CreateProcessWithTokenW}.
 */
public interface UserenvRunAsLib extends Library {

    UserenvRunAsLib INSTANCE = Native.load("userenv", UserenvRunAsLib.class, JNAOptions.UNICODE_SYSTEM_DLLS_ONLY);

    boolean CreateEnvironmentBlock(PointerByReference lpEnvironment, HANDLE hToken, boolean bInherit);

    boolean DestroyEnvironmentBlock(Pointer lpEnvironment);
}
