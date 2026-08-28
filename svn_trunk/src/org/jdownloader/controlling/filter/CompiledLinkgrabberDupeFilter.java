package org.jdownloader.controlling.filter;

import org.appwork.storage.Storable;
import org.appwork.storage.StorableAllowPrivateAccessModifier;

import jd.controlling.linkcollector.LinkCollector;
import jd.controlling.linkcrawler.CrawledLink;
import jd.gui.swing.jdgui.views.settings.panels.linkgrabberfilter.editdialog.LinkgrabberDupeFilter;

public class CompiledLinkgrabberDupeFilter extends LinkgrabberDupeFilter implements Storable {
    @StorableAllowPrivateAccessModifier
    private CompiledLinkgrabberDupeFilter() {
    }

    public CompiledLinkgrabberDupeFilter(LinkgrabberDupeFilter filter) {
        super(filter.getMatchType(), filter.isEnabled());
    }

    public boolean matches(CrawledLink link) {
        if (link == null) {
            return false;
        }
        // A duplicate inside the linkgrabber is only meaningful when the dupe manager is disabled (otherwise duplicates are
        // never added in the first place). LinkCollector.isLinkgrabberDupe() already returns false when the dupe manager is
        // enabled, so no extra guard is required here.
        final boolean isDupe = LinkCollector.getInstance().isLinkgrabberDupe(link);
        switch (getMatchType()) {
        case IS_TRUE:
            return isDupe;
        case IS_FALSE:
            return !isDupe;
        }
        return false;
    }
}
