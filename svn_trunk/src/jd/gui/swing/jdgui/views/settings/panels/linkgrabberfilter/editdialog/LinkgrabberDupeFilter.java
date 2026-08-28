package jd.gui.swing.jdgui.views.settings.panels.linkgrabberfilter.editdialog;

import org.appwork.storage.Storable;
import org.jdownloader.gui.translate._GUI;

public class LinkgrabberDupeFilter extends BooleanStatusFilter implements Storable {
    public LinkgrabberDupeFilter() {
        // Storable
    }

    public LinkgrabberDupeFilter(Matchtype matchType, boolean selected) {
        super(matchType, selected);
    }

    @Override
    protected String getTrueLabel() {
        return getTrueLabelStatic();
    }

    @Override
    protected String getFalseLabel() {
        return getFalseLabelStatic();
    }

    public static String getTrueLabelStatic() {
        return _GUI.T.FilterRule_LinkgrabberDupeFilter_true();
    }

    public static String getFalseLabelStatic() {
        return _GUI.T.FilterRule_LinkgrabberDupeFilter_false();
    }
}
