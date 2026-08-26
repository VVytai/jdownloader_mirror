/**
 *
 * ====================================================================================================================================================
 *         "AppWork Utilities" License
 *         Copyright (c) 2009-2026, AppWork GmbH <e-mail@appwork.org>
 *         Spalter Strasse 58, 91183 Abenberg, Germany
 *         (License header abbreviated; see project license.)
 * ==================================================================================================================================================== */
package org.appwork.utils.os.windows.execute;

/**
 * Step 1 context snapshot for run-as decisions: caller process identity + owner identity of the current (or requested) WTS session.
 */
public final class RunAsCurrentContext {
    private final int     processSessionId;
    private final String  processSid;
    private final boolean processElevated;
    private final boolean processLocalSystem;
    private final int     ownerSessionId;
    private final String  ownerSid;
    private final String  ownerAccountName;

    RunAsCurrentContext(int processSessionId, String processSid, boolean processElevated, boolean processLocalSystem, int ownerSessionId, String ownerSid, String ownerAccountName) {
        this.processSessionId = processSessionId;
        this.processSid = processSid != null ? processSid : "";
        this.processElevated = processElevated;
        this.processLocalSystem = processLocalSystem;
        this.ownerSessionId = ownerSessionId;
        this.ownerSid = ownerSid != null ? ownerSid : "";
        this.ownerAccountName = ownerAccountName != null ? ownerAccountName : "";
    }

    public int getProcessSessionId() {
        return processSessionId;
    }

    public String getProcessSid() {
        return processSid;
    }

    public boolean isProcessElevated() {
        return processElevated;
    }

    public boolean isProcessLocalSystem() {
        return processLocalSystem;
    }

    public int getOwnerSessionId() {
        return ownerSessionId;
    }

    public String getOwnerSid() {
        return ownerSid;
    }

    public String getOwnerAccountName() {
        return ownerAccountName;
    }

    public boolean isProcessSidEqualOwnerSid() {
        return processSid.length() > 0 && ownerSid.length() > 0 && processSid.equalsIgnoreCase(ownerSid);
    }
}

