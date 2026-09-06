//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.gui.swing.jdgui.components.speedmeter;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import jd.controlling.downloadcontroller.DownloadWatchDog;

import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.locale._AWU;
import org.appwork.utils.swing.Graph;
import org.appwork.utils.swing.graph.Limiter;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.settings.GraphicalUserInterfaceSettings.SPEEDUNIT;
import org.jdownloader.settings.staticreferences.CFG_GUI;
import org.jdownloader.updatev2.gui.LAFOptions;

public class SpeedMeterPanel extends Graph {
    private static final long     serialVersionUID = 5571694800446993879L;
    private final Limiter         speedLimiter;
    private final DecimalFormat   decimalFormat;
    private final SPEEDUNIT       maxSpeedUnit;

    @Override
    protected NumberFormat getNumberFormat() {
        return decimalFormat;
    }

    public SpeedMeterPanel(boolean contextMenu, boolean start) {
        super();
        final int fps = Math.max(1, CFG_GUI.CFG.getSpeedMeterFramesPerSecond());
        this.setCapacity((CFG_GUI.CFG.getSpeedMeterTimeFrame() * fps) / 1000);
        this.setInterval(1000 / fps);
        decimalFormat = new DecimalFormat("0.00");
        maxSpeedUnit = CFG_GUI.CFG.getMaxSpeedUnit();
        setCurrentColorTop(LAFOptions.getInstance().getColorForSpeedmeterCurrentTop());
        setCurrentColorBottom(LAFOptions.getInstance().getColorForSpeedmeterCurrentBottom());
        setAverageColor(LAFOptions.getInstance().getColorForSpeedMeterAverage());
        setAverageTextColor(LAFOptions.getInstance().getColorForSpeedMeterAverageText());
        setTextColor(LAFOptions.getInstance().getColorForSpeedMeterText());
        setOpaque(false);
        speedLimiter = new Limiter(LAFOptions.getInstance().getColorForSpeedmeterLimiterTop(), LAFOptions.getInstance().getColorForSpeedmeterLimiterBottom()) {
            public String getString() {
                return _GUI.T.SpeedMeterPanel_getString_limited(SizeFormatter.formatBytes(decimalFormat, getValue()));
            };

            public int getValue() {
                /*
                 * Show the effective limit as applied by the download speed manager: the pause speed while paused, the
                 * regular limit otherwise (0 = no limit, no line). The graph repaints periodically, so reading it live
                 * keeps the displayed limit in sync without any config listeners.
                 */
                return DownloadWatchDog.getInstance().getDownloadSpeedManager().getLimit();
            };
        };
        setLimiter(new Limiter[] { speedLimiter });
        if (start) {
            start();
        }
        setAntiAliasing(LAFOptions.getInstance().getCfg().isSpeedmeterAntiAliasingEnabled());
    }

    protected String createTooltipText() {
        /* effective limit incl. pause speed while paused (0 = no limit) */
        final int limit = DownloadWatchDog.getInstance().getDownloadSpeedManager().getLimit();
        if (limit > 0) {
            return getAverageSpeedString() + "  " + getSpeedString() + "\r\n" + _GUI.T.SpeedMeterPanel_createTooltipText_(SPEEDUNIT.formatValue(maxSpeedUnit, getNumberFormat(), limit));
        } else {
            return getAverageSpeedString() + "  " + getSpeedString();
        }
    }

    @Override
    public String getAverageSpeedString() {
        final long all = this.all;
        if (all <= 0) {
            return null;
        } else {
            return _AWU.T.AppWorkUtils_Graph_getAverageSpeedString2(SPEEDUNIT.formatValue(maxSpeedUnit, getNumberFormat(), this.average / all));
        }
    }

    @Override
    public String getSpeedString() {
        if (this.all <= 0) {
            return null;
        }
        return _AWU.T.AppWorkUtils_Graph_getSpeedString(SPEEDUNIT.formatValue(maxSpeedUnit, getNumberFormat(), this.value));
    }

    @Override
    public int getValue() {
        return DownloadWatchDog.getInstance().getDownloadSpeedManager().getSpeed();
    }
}