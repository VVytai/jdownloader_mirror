/**
 *
 * ====================================================================================================================================================
 *         "AppWork Utilities" License
 *         Copyright (c) 2009-2026, AppWork GmbH <e-mail@appwork.org>
 *         Spalter Strasse 58, 91183 Abenberg, Germany
 *         (License header abbreviated; see project license.)
 * ==================================================================================================================================================== */
package org.appwork.utils.os.windows.execute.jna;

import java.util.Arrays;
import java.util.List;

import org.appwork.jna.windows.JNAOptions;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinNT.HANDLEByReference;
import com.sun.jna.platform.win32.WinNT.SID_AND_ATTRIBUTES;

/**
 * Minimal Advapi32 WinSafer + {@code SetTokenInformation} bindings for same-user admin downgrade
 * ({@link org.appwork.utils.os.windows.execute.RunAsHelper#runNonElevated}).
 */
public interface Advapi32SaferLib extends Library {
    Advapi32SaferLib INSTANCE                 = Native.load("advapi32", Advapi32SaferLib.class, JNAOptions.SYSTEM_DLLS_ONLY);
    /** User scope (restriction applies to current user). */
    int              SAFER_SCOPEID_USER       = 2;
    /** Normal user level (non-elevated, medium integrity). */
    int              SAFER_LEVELID_NORMALUSER = 0x20000;
    /** Open existing level. */
    int              SAFER_LEVEL_OPEN         = 1;
    /** Integrity level: medium (non-elevated). SID S-1-16-8192. */
    String           INTEGRITY_SID_MEDIUM     = "S-1-16-8192";
    /** Label attribute for integrity. */
    int              SE_GROUP_INTEGRITY       = 0x00000020;

    boolean SaferCreateLevel(int dwScopeId, int dwLevelId, int openFlags, HANDLEByReference pLevelHandle, Pointer pReserved);

    boolean SaferComputeTokenFromLevel(HANDLE levelHandle, HANDLE inAccessToken, HANDLEByReference outAccessToken, int dwFlags, Pointer lpReserved);

    boolean SaferCloseLevel(HANDLE levelHandle);

    boolean SetTokenInformation(HANDLE tokenHandle, int tokenInfoClass, Pointer tokenInformation, int length);

    /**
     * TOKEN_MANDATORY_LABEL for SetTokenInformation(TokenIntegrityLevel). Same layout as SID_AND_ATTRIBUTES.
     */
    class TOKEN_MANDATORY_LABEL extends Structure {
        public SID_AND_ATTRIBUTES Label;

        public TOKEN_MANDATORY_LABEL() {
            Label = new SID_AND_ATTRIBUTES();
        }

        public TOKEN_MANDATORY_LABEL(Pointer p) {
            super(p);
            Label = new SID_AND_ATTRIBUTES();
            Label.read();
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("Label");
        }
    }
}
