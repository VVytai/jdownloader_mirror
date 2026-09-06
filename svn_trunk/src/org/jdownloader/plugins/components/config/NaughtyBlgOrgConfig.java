package org.jdownloader.plugins.components.config;

import org.appwork.storage.config.annotations.AboutConfig;
import org.appwork.storage.config.annotations.DefaultBooleanValue;
import org.appwork.storage.config.annotations.DescriptionForConfigEntry;
import org.jdownloader.plugins.config.Order;
import org.jdownloader.plugins.config.PluginConfigInterface;
import org.jdownloader.plugins.config.PluginHost;
import org.jdownloader.plugins.config.Type;

@PluginHost(host = "naughtyblog.my", type = Type.CRAWLER)
public interface NaughtyBlgOrgConfig extends PluginConfigInterface {
    public static final NaughtyBlgOrgConfig.TRANSLATION TRANSLATION = new TRANSLATION();

    public static class TRANSLATION {
        public String getCrawlCaptchaProtectedSpareLinks_label() {
            return "Also crawl captcha protected 'Spare links'?";
        }
    }

    @AboutConfig
    @DescriptionForConfigEntry("Also crawl captcha protected 'Spare links'?")
    @DefaultBooleanValue(true)
    @Order(10)
    boolean isCrawlCaptchaProtectedSpareLinks();

    void setCrawlCaptchaProtectedSpareLinks(boolean b);
}
