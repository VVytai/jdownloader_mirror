package org.appwork.utils.os;

import java.io.File;
import java.io.IOException;

public class DesktopSupportWindowsViaJNA extends DesktopSupportWindows {

    public DesktopSupportWindowsViaJNA() {
        // TODO Auto-generated constructor stub
    }

    /**
     * @see org.appwork.utils.os.DesktopSupportWindows#openFile(java.io.File, boolean)
     */
    @Override
    public void openFile(File file, boolean tryToReuseWindows) throws IOException {
        if (tryToReuseWindows) {
            if (file.isDirectory()) {
                if (WindowsUtils.explorerToFront(file)) {
                    return;
                }
            }
        }
        super.openFile(file, tryToReuseWindows);
    }
}
