package jd.gui.swing.jdgui.views.settings.panels.accountmanager;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;

import org.appwork.utils.event.queue.QueueAction;
import org.appwork.utils.swing.EDTRunner;
import org.appwork.utils.swing.dialog.Dialog;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.helpdialogs.HelpDialog;
import org.jdownloader.gui.helpdialogs.MessageConfig;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.AbstractIcon;

import jd.SecondLevelLaunch;
import jd.controlling.AccountController;
import jd.controlling.AccountControllerEvent;
import jd.controlling.AccountControllerListener;
import jd.controlling.AccountFilter;
import jd.controlling.TaskQueue;
import jd.controlling.accountchecker.AccountChecker;
import jd.plugins.Account;

public class RefreshAction extends AbstractAction implements AccountControllerListener {
    /**
     *
     */
    private static final long        serialVersionUID = 1L;
    private final List<AccountEntry> selection;

    public RefreshAction() {
        selection = null;
        this.putValue(NAME, _GUI.T.settings_accountmanager_refresh());
        this.putValue(AbstractAction.SMALL_ICON, new AbstractIcon(IconKey.ICON_REFRESH, 16));
        initAccountControllerListener();
    }

    public RefreshAction(List<AccountEntry> selectedObjects) {
        selection = selectedObjects != null ? selectedObjects : new ArrayList<AccountEntry>();
        this.putValue(NAME, _GUI.T.settings_accountmanager_refresh());
        this.putValue(AbstractAction.SMALL_ICON, new AbstractIcon(IconKey.ICON_REFRESH, 16));
        initAccountControllerListener();
    }

    protected void initAccountControllerListener() {
        SecondLevelLaunch.ACCOUNTLIST_LOADED.executeWhenReached(new Runnable() {
            @Override
            public void run() {
                AccountController.getInstance().getEventSender().addListener(RefreshAction.this, true);
                updateEnabledState();
            }
        });
    }

    public void actionPerformed(ActionEvent e) {
        if (!isEnabled()) {
            /* Account was disabled -> Do nothing */
            return;
        }
        TaskQueue.getQueue().add(new QueueAction<Void, RuntimeException>() {
            @Override
            protected Void run() throws RuntimeException {
                final List<Account> accountsToCheck = getAccountsToCheck();
                if (accountsToCheck == null || accountsToCheck.isEmpty()) {
                    /* Do nothing. This can happen if e.g. all selected items are disabled. */
                    return null;
                }
                boolean containedCheckedMultihosterAccount = false;
                for (final Account acc : accountsToCheck) {
                    AccountChecker.getInstance().check(acc, true);
                    containedCheckedMultihosterAccount |= acc.isMultiHost();
                }
                if (containedCheckedMultihosterAccount) {
                    displayMultihosterDetailOverviewHelpDialog();
                }
                return null;
            }
        });
    }

    /**
     * Returns AccountFilter to filter accounts eligable for checking accounts when no specific accounts are selected -> For when user wants
     * to check all accounts by clicking the check button once.
     */
    protected AccountFilter getAccountFilter() {
        return new AccountFilter().setEnabled(true).setValid(true);
    }

    private List<Account> getAccountsToCheck() {
        if (selection == null) {
            /* All [enabled] accounts */
            return AccountController.getInstance().listAccounts(getAccountFilter());
        } else {
            final List<Account> accountsToCheck = new ArrayList<Account>();
            /* Selected [enabled] accounts only */
            for (final AccountEntry accEntry : selection) {
                final Account acc = accEntry.getAccount();
                if (acc == null) {
                    continue;
                }
                accountsToCheck.add(acc);
            }
            return accountsToCheck;
        }
    }

    public static void displayMultihosterDetailOverviewHelpDialog() {
        HelpDialog.showIfAllowed(new MessageConfig(null, "multihoster_table_detail_overview_hint", Dialog.STYLE_SHOW_DO_NOT_DISPLAY_AGAIN, _GUI.T.multihost_detailed_host_do_not_show_again_info_about_multi_host_overview_table_title(), _GUI.T.multihost_detailed_host_do_not_show_again_info_about_multi_host_overview_table_message(), new AbstractIcon(IconKey.ICON_SORT, 32)));
    }

    @Override
    public boolean isEnabled() {
        if (selection != null) {
            /*
             * Context-menu action: enabled only if at least one account is selected. An empty selection (e.g. right-click on empty table
             * area) must stay greyed out since there is nothing to refresh.
             */
            return selection.size() > 0;
        }
        /* Toolbar / "refresh all" action (selection == null): enabled only if at least one enabled+valid account exists. */
        final List<Account> accs = AccountController.getInstance().listAccounts(getAccountFilter().setMaxResultsNum(1));
        return accs.size() > 0;
    }

    /**
     * Re-evaluates {@link #isEnabled()} and notifies listeners (e.g. the toolbar button) so they can update their enabled state. Since
     * {@link #isEnabled()} is computed dynamically, callers must invoke this whenever the underlying account state may have changed.
     */
    public void updateEnabledState() {
        final boolean isEnabled = isEnabled();
        new EDTRunner() {
            @Override
            protected void runInEDT() {
                firePropertyChange("enabled", null, Boolean.valueOf(isEnabled));
            }
        };
    }

    @Override
    public void onAccountControllerEvent(AccountControllerEvent event) {
        updateEnabledState();
    }
}
