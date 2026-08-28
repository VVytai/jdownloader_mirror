package org.jdownloader.controlling.filter;

import jd.gui.swing.jdgui.views.settings.panels.linkgrabberfilter.editdialog.BooleanStatusFilter.Matchtype;
import jd.gui.swing.jdgui.views.settings.panels.linkgrabberfilter.editdialog.LinkgrabberDupeFilter;

import org.jdownloader.gui.IconKey;
import org.jdownloader.translate._JDT;

/**
 * Default linkgrabber view that marks all links which appear more than once in the linkgrabber, except the first (topmost)
 * occurrence. Only has an effect while the dupe manager is disabled (LinkCollector.dupemanagerenabled == false), because
 * otherwise duplicates are never added to the linkgrabber. The "first" occurrence is determined by the real linkgrabber order
 * (package order, then child order), independent of the current table sorting.
 */
public class LinkgrabberDupesView extends LinkgrabberFilterRule {
    public static final String ID = "LinkgrabberDupesView";

    public LinkgrabberDupesView() {

    }

    public LinkgrabberFilterRule init() {
        setLinkgrabberDupeFilter(new LinkgrabberDupeFilter(Matchtype.IS_TRUE, true));
        setName(_JDT.T.LinkFilterSettings_DefaultFilterList_linkgrabberDupes());
        setIconKey(IconKey.ICON_COPY);
        setEnabled(true);
        setAccept(true);
        setId(ID);
        setStaticRule(true);
        return this;
    }
}
