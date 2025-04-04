/**
 * 
 * ====================================================================================================================================================
 *         "AppWork Utilities" License
 *         The "AppWork Utilities" will be called [The Product] from now on.
 * ====================================================================================================================================================
 *         Copyright (c) 2009-2015, AppWork GmbH <e-mail@appwork.org>
 *         Schwabacher Straße 117
 *         90763 Fürth
 *         Germany   
 * === Preamble ===
 *     This license establishes the terms under which the [The Product] Source Code & Binary files may be used, copied, modified, distributed, and/or redistributed.
 *     The intent is that the AppWork GmbH is able to provide their utilities library for free to non-commercial projects whereas commercial usage is only permitted after obtaining a commercial license.
 *     These terms apply to all files that have the [The Product] License header (IN the file), a <filename>.license or <filename>.info (like mylib.jar.info) file that contains a reference to this license.
 * 	
 * === 3rd Party Licences ===
 *     Some parts of the [The Product] use or reference 3rd party libraries and classes. These parts may have different licensing conditions. Please check the *.license and *.info files of included libraries
 *     to ensure that they are compatible to your use-case. Further more, some *.java have their own license. In this case, they have their license terms in the java file header. 	
 * 	
 * === Definition: Commercial Usage ===
 *     If anybody or any organization is generating income (directly or indirectly) by using [The Product] or if there's any commercial interest or aspect in what you are doing, we consider this as a commercial usage.
 *     If your use-case is neither strictly private nor strictly educational, it is commercial. If you are unsure whether your use-case is commercial or not, consider it as commercial or contact us.
 * === Dual Licensing ===
 * === Commercial Usage ===
 *     If you want to use [The Product] in a commercial way (see definition above), you have to obtain a paid license from AppWork GmbH.
 *     Contact AppWork for further details: <e-mail@appwork.org>
 * === Non-Commercial Usage ===
 *     If there is no commercial usage (see definition above), you may use [The Product] under the terms of the 
 *     "GNU Affero General Public License" (http://www.gnu.org/licenses/agpl-3.0.en.html).
 * 	
 *     If the AGPL does not fit your needs, please contact us. We'll find a solution.
 * ====================================================================================================================================================
 * ==================================================================================================================================================== */
package org.appwork.swing.exttable.columns;

import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.regex.Pattern;

import javax.swing.JComponent;

import org.appwork.swing.exttable.ExtTableModel;
import org.appwork.utils.locale._AWU;

/**
 * @author daniel
 * 
 */
public abstract class ExtPasswordEditorColumn<E> extends ExtTextColumn<E> implements ActionListener {

    private static final long   serialVersionUID = -3107569347493659178L;
    private static final String BLINDTEXT        = "******";

    public ExtPasswordEditorColumn(final String name) {
        this(name, null);

    }

    public ExtPasswordEditorColumn(final String name, final ExtTableModel<E> table) {
        super(name, table);
        this.editorField.addFocusListener(new FocusListener() {

            @Override
            public void focusGained(final FocusEvent e) {
                ExtPasswordEditorColumn.this.editorField.selectAll();

            }

            @Override
            public void focusLost(final FocusEvent e) {
			}
        });

    }

    /**
     * @return
     */
    @Override
    public JComponent getEditorComponent(final E value, final boolean isSelected, final int row, final int column) {
        return this.editorField;
    }

    protected abstract String getPlainStringValue(E value);

    @Override
    public String getStringValue(final E value) {
        return this.hasPassword(value) ? ExtPasswordEditorColumn.BLINDTEXT : "";
    }

    @Override
    protected String getTooltipText(final E obj) {

        return _AWU.T.extpasswordeditorcolumn_tooltip();
    }

    /**
     * @param value
     * @return
     */
    private boolean hasPassword(final E value) {
        final String pw = this.getPlainStringValue(value);
        return pw != null && pw.length() > 0;
    }

    @Override
    public boolean isEditable(final E obj) {
        return true;
    }

    @Override
    public boolean matchSearch(final E object, final Pattern pattern) {
        return false;
    }

    @Override
    protected abstract void setStringValue(String value, E object);

    @Override
    public void setValue(final Object value, final E object) {
        if (!value.toString().equals(ExtPasswordEditorColumn.BLINDTEXT)) {
            this.setStringValue((String) value, object);
        }
    }

}
