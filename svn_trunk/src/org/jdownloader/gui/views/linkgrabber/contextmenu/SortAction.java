package org.jdownloader.gui.views.linkgrabber.contextmenu;

import java.awt.event.ActionEvent;

import jd.controlling.packagecontroller.AbstractNode;
import jd.controlling.packagecontroller.AbstractPackageChildrenNode;
import jd.controlling.packagecontroller.AbstractPackageNode;
import jd.controlling.packagecontroller.PackageControllerComparator;
import jd.gui.swing.jdgui.MainTabbedPane;
import jd.gui.swing.jdgui.interfaces.View;

import org.appwork.swing.exttable.ExtColumn;
import org.appwork.utils.event.queue.Queue.QueuePriority;
import org.appwork.utils.event.queue.QueueAction;
import org.jdownloader.controlling.contextmenu.CustomizableTableContextAppAction;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.gui.views.SelectionInfo;
import org.jdownloader.gui.views.SelectionInfo.PackageView;
import org.jdownloader.gui.views.components.packagetable.PackageControllerTableModel;
import org.jdownloader.gui.views.downloads.DownloadsView;
import org.jdownloader.gui.views.downloads.table.DownloadsTable;
import org.jdownloader.gui.views.linkgrabber.LinkGrabberTable;
import org.jdownloader.gui.views.linkgrabber.LinkGrabberView;
import org.jdownloader.settings.staticreferences.CFG_GUI;

public class SortAction<PackageType extends AbstractPackageNode<ChildrenType, PackageType>, ChildrenType extends AbstractPackageChildrenNode<PackageType>> extends CustomizableTableContextAppAction<PackageType, ChildrenType> {
    /**
     *
     */
    private static final long       serialVersionUID = -3883739313644803093L;
    private ExtColumn<AbstractNode> column;

    @Override
    public void requestUpdate(Object requestor) {
        super.requestUpdate(requestor);
        final View view = MainTabbedPane.getInstance().getSelectedView();
        if (view instanceof DownloadsView) {
            this.column = DownloadsTable.getInstance().getMouseOverColumn();
        } else if (view instanceof LinkGrabberView) {
            this.column = LinkGrabberTable.getInstance().getMouseOverColumn();
        }
        if (getSelection() != null && this.column != null) {
            setIconKey(IconKey.ICON_SORT);
            setName(_GUI.T.SortAction_SortAction_object_(column.getName()));
        } else {
            setIconKey(IconKey.ICON_SORT);
            setName(_GUI.T.SortAction_SortAction_object_empty());
        }
    }

    @Override
    public void setEnabled(boolean newValue) {
        super.setEnabled(true);
    }

    public SortAction() {
        super(true, true);
        setIconKey(IconKey.ICON_SORT);
        setName(_GUI.T.SortAction_SortAction_object_empty());
    }

    public void actionPerformed(ActionEvent e) {
        if (!isEnabled()) {
            return;
        } else if (column == null) {
            return;
        } else if (!(column.getModel() instanceof PackageControllerTableModel)) {
            return;
        }
        final SelectionInfo<PackageType, ChildrenType> selection = getSelection();
        final PackageControllerTableModel model = (PackageControllerTableModel) column.getModel();
        model.getController().getQueue().add(new QueueAction<Void, RuntimeException>(QueuePriority.HIGH) {
            @SuppressWarnings({ "rawtypes", "unchecked" })
            @Override
            protected Void run() throws RuntimeException {
                if (model instanceof PackageControllerTableModel) {
                    PackageControllerComparator<? extends AbstractNode> comparator = null;
                    for (final PackageView<PackageType, ChildrenType> node : selection.getPackageViews()) {
                        if (comparator == null) {
                            String currentID = column.getModel().getModelID() + ".Column." + column.getID();
                            final PackageControllerComparator currentSorter = node.getPackage().getCurrentSorter();
                            final boolean asc;
                            if (currentSorter == null || !currentSorter.getID().equals(currentID)) {
                                if (CFG_GUI.CFG.isPrimaryTableSorterDesc()) {
                                    asc = true;
                                } else {
                                    asc = false;
                                }
                            } else {
                                asc = !currentSorter.isAsc();
                            }
                            currentID = (asc ? ExtColumn.SORT_ASC : ExtColumn.SORT_DESC) + "." + currentID;
                            comparator = PackageControllerComparator.getComparator(currentID);
                        }
                        model.sortPackageChildren(node.getPackage(), comparator);
                    }
                }
                return null;
            }
        });
    }
}
