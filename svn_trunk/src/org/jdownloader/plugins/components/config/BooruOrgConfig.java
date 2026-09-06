package org.jdownloader.plugins.components.config;

import org.appwork.storage.config.annotations.AboutConfig;
import org.appwork.storage.config.annotations.DefaultIntValue;
import org.appwork.storage.config.annotations.SpinnerValidator;
import org.jdownloader.plugins.config.Order;
import org.jdownloader.plugins.config.PluginConfigInterface;
import org.jdownloader.plugins.config.PluginHost;
import org.jdownloader.plugins.config.Type;

@PluginHost(host = "booru.org", type = Type.CRAWLER, multi = true)
public interface BooruOrgConfig extends PluginConfigInterface {
    public static final BooruOrgConfig.TRANSLATION TRANSLATION = new TRANSLATION();

    public static class TRANSLATION {
        public String getPaginationWaitSeconds_label() {
            return "Crawler: Wait time between pagination requests in seconds";
        }
    }

    @AboutConfig
    @SpinnerValidator(min = 0, max = 30, step = 1)
    @DefaultIntValue(1)
    @Order(10)
    int getPaginationWaitSeconds();

    void setPaginationWaitSeconds(int seconds);
}
