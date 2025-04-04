package jd.gui.swing.jdgui.views.settings.panels.accountmanager;

import java.awt.event.ActionEvent;
import java.util.List;

import org.appwork.utils.event.queue.QueueAction;
import org.appwork.utils.swing.dialog.Dialog;
import org.appwork.utils.swing.dialog.DialogNoAnswerException;
import org.jdownloader.actions.AppAction;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;

import jd.controlling.AccountController;
import jd.controlling.TaskQueue;

public class RemoveAction extends AppAction {
    private static final long   serialVersionUID = 1L;
    private PremiumAccountTable table;
    private boolean             force            = false;
    private List<AccountEntry>  selection        = null;

    public RemoveAction(PremiumAccountTable table) {
        this.table = table;
        setName(_GUI.T.literally_remove());
        setIconKey(IconKey.ICON_REMOVE);
    }

    public RemoveAction(final List<AccountEntry> selection, final boolean force) {
        this.force = force;
        this.selection = selection;
        setName(_GUI.T.literally_remove());
        setIconKey(IconKey.ICON_REMOVE);
    }

    public void actionPerformed(ActionEvent e) {
        if (!isEnabled()) {
            return;
        }
        final List<AccountEntry> finalSelection;
        if (selection != null) {
            finalSelection = selection;
        } else if (table != null) {
            finalSelection = table.getModel().getSelectedObjects();
        } else {
            finalSelection = null;
        }
        if (finalSelection == null || finalSelection.isEmpty()) {
            return;
        }
        final StringBuilder sb = new StringBuilder();
        for (AccountEntry account : finalSelection) {
            if (sb.length() > 0) {
                sb.append("\r\n");
            }
            sb.append(account.getAccount().getHosterByPlugin() + "-Account (" + account.getAccount().getUser() + ")");
        }
        try {
            if (!force) {
                Dialog.getInstance().showConfirmDialog(Dialog.STYLE_LARGE, _GUI.T.account_remove_action_title(finalSelection.size()), _GUI.T.account_remove_action_msg(finalSelection.size() <= 1 ? sb.toString() : "\r\n" + sb.toString()));
            }
            TaskQueue.getQueue().add(new QueueAction<Void, RuntimeException>() {
                @Override
                protected Void run() throws RuntimeException {
                    for (AccountEntry account : finalSelection) {
                        AccountController.getInstance().removeAccount(account.getAccount());
                    }
                    return null;
                }
            });
        } catch (DialogNoAnswerException e1) {
            e1.printStackTrace();
        }
    }

    @Override
    public boolean isEnabled() {
        if (this.table != null) {
            return super.isEnabled();
        }
        return (selection != null && selection.size() > 0);
    }
}
