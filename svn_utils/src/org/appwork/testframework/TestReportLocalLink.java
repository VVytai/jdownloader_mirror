/**
 *
 * ====================================================================================================================================================
 *         "AppWork Utilities" License
 *         The "AppWork Utilities" will be called [The Product] from now on.
 * ====================================================================================================================================================
 *         Copyright (c) 2009-2026, AppWork GmbH <e-mail@appwork.org>
 *         Spalter Strasse 58
 *         91183 Abenberg
 *         Germany
 * ====================================================================================================================================================
 * ==================================================================================================================================================== */
package org.appwork.testframework;

import org.appwork.storage.Storable;
import org.appwork.storage.StorableDoc;
import org.appwork.storage.StorableExample;

/**
 * Named absolute filesystem path shown as a {@code file://} quick link in the HTML test report.
 */
@StorableDoc("Labelled local path for HTML test-report quick links (e.g. Dist, Project, Build archive).")
@StorableExample("{\"label\":\"Dist\",\"path\":\"C:\\\\workspace\\\\MyProject\\\\dist\"}")
public class TestReportLocalLink implements Storable {
    private String label;
    private String path;

    public TestReportLocalLink() {
    }

    public TestReportLocalLink(final String label, final String path) {
        this.label = label;
        this.path = path;
    }

    public String getLabel() {
        return this.label;
    }

    public void setLabel(final String label) {
        this.label = label;
    }

    public String getPath() {
        return this.path;
    }

    public void setPath(final String path) {
        this.path = path;
    }
}
