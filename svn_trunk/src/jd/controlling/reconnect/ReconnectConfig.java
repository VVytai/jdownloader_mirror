package jd.controlling.reconnect;

import org.appwork.storage.config.ConfigInterface;
import org.appwork.storage.config.annotations.AboutConfig;
import org.appwork.storage.config.annotations.DefaultBooleanValue;
import org.appwork.storage.config.annotations.DefaultIntValue;
import org.appwork.storage.config.annotations.DefaultStringValue;
import org.appwork.storage.config.annotations.DescriptionForConfigEntry;
import org.appwork.storage.config.annotations.PlainStorage;
import org.appwork.storage.config.annotations.SpinnerValidator;

import jd.controlling.reconnect.ipcheck.IP;

@PlainStorage
public interface ReconnectConfig extends ConfigInterface {
    @AboutConfig
    String getActivePluginID();

    void setActivePluginID(String id);

    @DefaultIntValue(0)
    void setGlobalFailedCounter(int i);

    int getGlobalFailedCounter();

    @DefaultIntValue(0)
    void setFailedCounter(int i);

    int getFailedCounter();

    @DefaultIntValue(0)
    void setGlobalSuccessCounter(int i);

    int getGlobalSuccessCounter();

    @DefaultIntValue(0)
    void setSuccessCounter(int i);

    int getSuccessCounter();

    @AboutConfig
    @DefaultIntValue(300)
    int getSecondsToWaitForIPChange();

    void setSecondsToWaitForIPChange(int i);

    @AboutConfig
    @DefaultIntValue(60)
    int getSecondsToWaitForOffline();

    void setSecondsToWaitForOffline(int i);

    @AboutConfig
    @DefaultBooleanValue(false)
    boolean isIPCheckGloballyDisabled();

    void setIPCheckGloballyDisabled(boolean b);

    @DefaultIntValue(5)
    @AboutConfig
    int getSecondsBeforeFirstIPCheck();

    void setSecondsBeforeFirstIPCheck(int seconds);

    @DescriptionForConfigEntry("Please enter Website for IPCheck here")
    @AboutConfig
    String getGlobalIPCheckUrl();

    void setGlobalIPCheckUrl(String url);

    @DescriptionForConfigEntry("Please enter Regex for IPCheck here")
    @AboutConfig
    @DefaultStringValue(IP.IP_PATTERN)
    String getGlobalIPCheckPattern();

    void setGlobalIPCheckPattern(String pattern);

    @AboutConfig
    @DefaultBooleanValue(false)
    boolean isCustomIPCheckEnabled();

    void setCustomIPCheckEnabled(boolean b);

    @DefaultIntValue(5)
    @AboutConfig
    int getMaxReconnectRetryNum();

    void setMaxReconnectRetryNum(int num);

    @AboutConfig
    @DefaultIntValue(2000)
    int getIPCheckConnectTimeout();

    void setIPCheckConnectTimeout(int ms);

    @AboutConfig
    @DefaultIntValue(10000)
    int getIPCheckReadTimeout();

    void setIPCheckReadTimeout(int ms);

    @AboutConfig
    @DefaultIntValue(30000)
    int getReconnectBrowserReadTimeout();

    void setReconnectBrowserReadTimeout(int ms);

    @AboutConfig
    @DefaultIntValue(30000)
    int getReconnectBrowserConnectTimeout();

    void setReconnectBrowserConnectTimeout(int ms);

    @AboutConfig
    @DefaultIntValue(5)
    @DescriptionForConfigEntry("Auto Reconnect Wizard performs a few reconnects for each successful script to find the fastest one. The more rounds we use, the better the result will be, but the longer it will take.")
    @SpinnerValidator(min = 1, max = 20)
    int getOptimizationRounds();

    void setOptimizationRounds(int num);

    @AboutConfig
    @DefaultBooleanValue(true)
    @DescriptionForConfigEntry("AutoReconnect enabled?")
    boolean isAutoReconnectEnabled();

    @AboutConfig
    @DescriptionForConfigEntry("Do not start further downloads if others are waiting for a reconnect/new ip")
    @DefaultBooleanValue(true)
    boolean isDownloadControllerPrefersReconnectEnabled();

    @AboutConfig
    @DefaultBooleanValue(true)
    @DescriptionForConfigEntry("If disabled, No Reconnects will be done while Resumable Downloads (Premium Downloads) are running")
    boolean isReconnectAllowedToInterruptResumableDownloads();

    void setAutoReconnectEnabled(boolean b);

    @AboutConfig
    @DefaultIntValue(10)
    @DescriptionForConfigEntry("Disable auto reconnect if reconnect method has failed x times in a row (-1 = never disable auto reconnect)")
    @SpinnerValidator(min = -1, max = 100)
    int getDisableAutoReconnectFails();

    void setDisableAutoReconnectFails(int num);

    void setReconnectAllowedToInterruptResumableDownloads(boolean b);

    void setDownloadControllerPrefersReconnectEnabled(boolean b);

    @AboutConfig
    @DefaultBooleanValue(false)
    @DescriptionForConfigEntry("Usually, the IP Check has to use the direct connection. However, in some rare situations, it is important to use a proxy to do the ipcheck. Only change this if you are 100% sure.")
    void setIPCheckUsesProxyEnabled(boolean b);

    boolean isIPCheckUsesProxyEnabled();

    @AboutConfig
    @DefaultBooleanValue(false)
    @DescriptionForConfigEntry("If your router supports UPNP, it might be possible to get your external IP by asking the router instead of doing a query to ipcheck*.jdownloader.org. This information might be incorrect. If the reconnect does not work after choosing this option, disable it.")
    boolean isIPCheckAllowLocalUpnpIpCheckEnabled();

    void setIPCheckAllowLocalUpnpIpCheckEnabled(boolean b);
}
