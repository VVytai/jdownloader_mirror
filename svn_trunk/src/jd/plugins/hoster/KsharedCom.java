//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.plugins.hoster;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.appwork.net.protocol.http.HTTPConstants;
import org.appwork.storage.JSonStorage;
import org.appwork.storage.StorageException;
import org.appwork.storage.TypeRef;
import org.appwork.utils.StringUtils;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperHostPluginRecaptchaV2;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.plugins.controller.LazyPlugin;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.controlling.AccountController;
import jd.http.Browser;
import jd.http.Cookies;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.AccountInvalidException;
import jd.plugins.AccountRequiredException;
import jd.plugins.AccountUnavailableException;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision: 50542 $", interfaceVersion = 3, names = {}, urls = {})
public class KsharedCom extends PluginForHost {
    public KsharedCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://www." + getHost() + "/premium");
    }

    @Override
    public LazyPlugin.FEATURE[] getFeatures() {
        return new LazyPlugin.FEATURE[] { LazyPlugin.FEATURE.USERNAME_IS_EMAIL };
    }

    @Override
    public String getAGBLink() {
        return "https://www." + getHost() + "/help/tos";
    }

    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForHost, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "kshared.com" });
        return ret;
    }

    public static String[] getAnnotationNames() {
        return buildAnnotationNames(getPluginDomains());
    }

    @Override
    public String[] siteSupportedNames() {
        return buildSupportedNames(getPluginDomains());
    }

    public static String[] getAnnotationUrls() {
        final List<String> ret = new ArrayList<String>();
        for (final String[] domains : getPluginDomains()) {
            ret.add("https?://(?:www\\.)?" + buildHostsPatternPart(domains) + "/file/([A-Za-z0-9]+)(/([^/]+))?");
        }
        return ret.toArray(new String[0]);
    }

    private static final String PROPERTY_PREMIUMONLY   = "premiumonly";
    private static final String PROPERTY_ACCOUNT_TOKEN = "access_token";
    private static final String PROPERTY_ACCOUNT_UT    = "ut";
    private static final String PROPERTY_ACCOUNT_UD    = "ud";

    @Override
    public boolean isResumeable(final DownloadLink link, final Account account) {
        return true;
    }

    public int getMaxChunks(final Account account) {
        final AccountType type = account != null ? account.getType() : null;
        if (AccountType.FREE.equals(type)) {
            /* Free Account */
            return 0;
        } else if (AccountType.PREMIUM.equals(type) || AccountType.LIFETIME.equals(type)) {
            /* Premium account */
            return 0;
        } else {
            /* Free(anonymous) and unknown account type */
            return 0;
        }
    }

    @Override
    public String getLinkID(final DownloadLink link) {
        final String linkid = getFID(link);
        if (linkid != null) {
            return this.getHost() + "://" + linkid;
        } else {
            return super.getLinkID(link);
        }
    }

    public String getFID(final DownloadLink link) {
        return new Regex(link.getPluginPatternMatcher(), this.getSupportedLinks()).getMatch(0);
    }

    private String getFallbackFilename(final DownloadLink link) {
        final String filenameURL = new Regex(link.getPluginPatternMatcher(), this.getSupportedLinks()).getMatch(2);
        if (filenameURL != null) {
            return filenameURL;
        } else {
            return this.getFID(link);
        }
    }

    private Browser prepBR(final Browser br) {
        br.setFollowRedirects(true);
        return br;
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        final Account account = AccountController.getInstance().getValidAccount(this.getHost());
        return requestFileInformation(link, account);
    }

    public AvailableStatus requestFileInformation(final DownloadLink link, final Account account) throws IOException, PluginException {
        if (!link.isNameSet()) {
            link.setName(getFallbackFilename(link));
        }
        if (account == null) {
            prepBR(this.br);
            this.setBrowserExclusive();
            br.getPage(link.getPluginPatternMatcher());
            findAndSetBearerToken(br);
        }
        final Map<String, Object> data = new HashMap<String, Object>();
        if (account != null) {
            data.put("ud", this.accountGetUD(account));
            data.put("ut", this.accountGetUT(account));
        }
        data.put("fileid", this.getFID(link));
        br.postPageRaw("https://www." + this.getHost() + "/v1/drive/get_download", JSonStorage.serializeToJson(data));
        final Map<String, Object> entries = this.handleErrors(br, link, account);
        if (entries == null) {
            /* 2021-02-18: Returns broken json for offline items */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final Map<String, Object> fileinfo = (Map<String, Object>) entries.get("file");
        if (fileinfo == null) {
            /* 2021-02-18: Returns broken json for offline items */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        // final Object errorO = entries.get("error");
        // if (errorO != null) {
        // throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        // }
        final String filename = (String) fileinfo.get("name");
        final Number filesizeN = (Number) fileinfo.get("size");
        if (!StringUtils.isEmpty(filename)) {
            link.setFinalFileName(filename);
        }
        link.setName(Encoding.htmlDecode(filename.trim()));
        if (filesizeN != null) {
            link.setVerifiedFileSize(filesizeN.longValue());
        }
        if (StringUtils.equalsIgnoreCase((String) fileinfo.get("locked"), "premium")) {
            link.setProperty(PROPERTY_PREMIUMONLY, true);
        } else {
            link.removeProperty(PROPERTY_PREMIUMONLY);
        }
        link.setPasswordProtected((Boolean) fileinfo.get("hasPassword"));
        return AvailableStatus.TRUE;
    }

    private boolean isPremiumonly(final DownloadLink link) {
        return link.hasProperty(PROPERTY_PREMIUMONLY);
    }

    /** Obtains API bearer token from html code and sets it as Authorization header. */
    private String findAndSetBearerToken(final Browser br) throws PluginException {
        String hash = br.getRegex("hash\\s*:\\s*\"([^\"]+)\"").getMatch(0);
        if (hash == null) {
            hash = br.getRegex("window\\.___ha=\"(ey[^\"]+)").getMatch(0);
        }
        if (hash == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        br.getHeaders().put(HTTPConstants.HEADER_REQUEST_AUTHORIZATION, "Bearer " + hash);
        return hash;
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception, PluginException {
        requestFileInformation(link);
        handleDownload(link, null);
    }

    private String getDirecturlProperty(final DownloadLink link, final Account account) {
        if (account == null) {
            return "free_directlink";
        } else if (AccountType.PREMIUM.equals(account.getType()) || AccountType.LIFETIME.equals(account.getType())) {
            return "premium_directlink";
        } else {
            return "free_directlink";
        }
    }

    private boolean attemptStoredDownloadurlDownload(final DownloadLink link, final Account account) throws Exception {
        final String property = getDirecturlProperty(link, account);
        final String url = link.getStringProperty(property);
        if (StringUtils.isEmpty(url)) {
            return false;
        }
        try {
            final Browser brc = br.cloneBrowser();
            dl = new jd.plugins.BrowserAdapter().openDownload(brc, link, url, true, 1);
            if (this.looksLikeDownloadableContent(dl.getConnection())) {
                return true;
            } else {
                /* Remove that so we don't waste time checking this again. */
                link.removeProperty(property);
                brc.followConnection(true);
                throw new IOException();
            }
        } catch (final Throwable e) {
            logger.log(e);
            try {
                dl.getConnection().disconnect();
            } catch (Throwable ignore) {
            }
            return false;
        }
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return Integer.MAX_VALUE;
    }

    private Map<String, Object> login(final Account account, final boolean force) throws Exception {
        synchronized (account) {
            br.setFollowRedirects(true);
            br.setCookiesExclusive(true);
            prepBR(this.br);
            String accessToken = this.accountGetAccessToken(account);
            String ud = this.accountGetUD(account);
            String ut = this.accountGetUT(account);
            final Map<String, Object> data2 = new HashMap<String, Object>();
            final Cookies userCookies = account.loadUserCookies();
            final Cookies cookies = account.loadCookies("");
            if (userCookies != null) {
                logger.info("Attempting user cookie login");
                br.setCookies(this.getHost(), userCookies);
                ud = br.getCookie(this.getHost(), "__ud");
                ut = br.getCookie(this.getHost(), "__ut");
                if (ud == null || ut == null) {
                    /* Invalid cookies */
                    throw new AccountInvalidException(_GUI.T.accountdialog_check_cookies_invalid());
                }
                account.setProperty(PROPERTY_ACCOUNT_UD, ud);
                account.setProperty(PROPERTY_ACCOUNT_UT, ut);
                if (accessToken == null || force) {
                    /* Fetch this on first attempt and when routine account check is happening. */
                    /* 2022-06-24: This will most likely fail here due to Cloudflare! */
                    br.getPage("https://www." + this.getHost() + "/account/signin");
                    accessToken = findAndSetBearerToken(br);
                    account.setProperty(PROPERTY_ACCOUNT_TOKEN, accessToken);
                }
                if (!force) {
                    /* Do not verify cookies */
                    return null;
                }
                try {
                    final Map<String, Object> entries = checkLogin(br, account);
                    logger.info("User cookie login successful");
                    return entries;
                } catch (final PluginException e) {
                    logger.log(e);
                    /* Dead end */
                    if (account.hasEverBeenValid()) {
                        throw new AccountInvalidException(_GUI.T.accountdialog_check_cookies_expired());
                    } else {
                        throw new AccountInvalidException(_GUI.T.accountdialog_check_cookies_invalid());
                    }
                }
            } else if (cookies != null && accessToken != null && ut != null && ud != null) {
                logger.info("Attempting token login");
                this.br.setCookies(this.getHost(), cookies);
                this.br.getHeaders().put("Authorization", "Bearer " + accessToken);
                br.getHeaders().put("Origin", "https://www." + this.getHost());
                br.getHeaders().put("Referer", "https://www." + this.getHost() + "/drive");
                if (!force) {
                    /* Do not verify cookies */
                    return null;
                }
                try {
                    final Map<String, Object> entries = checkLogin(br, account);
                    logger.info("Token login successful");
                    /* Refresh cookie timestamp */
                    account.saveCookies(this.br.getCookies(this.getHost()), "");
                    return entries;
                } catch (final PluginException e) {
                    logger.log(e);
                    logger.info("Token login failed");
                    br.clearCookies(br.getHost());
                }
            }
            logger.info("Performing full login");
            br.getPage("https://" + this.getHost() + "/account/signin");
            accessToken = findAndSetBearerToken(br);
            final Map<String, Object> data = new HashMap<String, Object>();
            data.put("email", account.getUser());
            data.put("passw", account.getPass());
            br.postPageRaw("/v1/account/signin", JSonStorage.serializeToJson(data));
            Map<String, Object> entries = this.handleErrors(br, null, account);
            // accessToken = (String) entries.get("accesstoken");
            ut = (String) entries.get("ut");
            ud = (String) entries.get("accesstoken");
            if (StringUtils.isEmpty(ut) || StringUtils.isEmpty(ud)) {
                /* This should never happen */
                throw new AccountUnavailableException("Unknown failure", 10 * 60 * 1000l);
            }
            data2.clear();
            data2.put("ud", ud);
            data2.put("ut", ut);
            br.getHeaders().put("Authorization", "Bearer " + accessToken);
            br.postPageRaw("/v1/account/is_user", JSonStorage.serializeToJson(data2));
            entries = this.handleErrors(br, null, account);
            account.setProperty(PROPERTY_ACCOUNT_TOKEN, accessToken);
            account.setProperty(PROPERTY_ACCOUNT_UD, ud);
            account.setProperty(PROPERTY_ACCOUNT_UT, ut);
            /* No error? Login was successful! */
            account.saveCookies(this.br.getCookies(this.getHost()), "");
            return entries;
        }
    }

    private Map<String, Object> checkLogin(final Browser br, final Account account) throws StorageException, IOException, PluginException {
        Map<String, Object> data2 = new HashMap<String, Object>();
        data2.put("ud", this.accountGetUD(account));
        data2.put("ut", this.accountGetUT(account));
        br.postPageRaw("https://www." + this.getHost() + "/v1/account/is_user", JSonStorage.serializeToJson(data2));
        return this.handleErrors(br, null, account);
    }

    private String accountGetAccessToken(final Account account) {
        return account.getStringProperty(PROPERTY_ACCOUNT_TOKEN);
    }

    private String accountGetUT(final Account account) {
        return account.getStringProperty(PROPERTY_ACCOUNT_UT);
    }

    private String accountGetUD(final Account account) {
        return account.getStringProperty(PROPERTY_ACCOUNT_UD);
    }

    private Map<String, Object> handleErrors(final Browser br, final DownloadLink link, final Account account) throws PluginException {
        String json = br.getRequest().getHtmlCode();
        /* 2025-01-31: They sometimes return invalid json (wtf how does that happen??) -> Fix it */
        json = json.replace("}{", ",");
        final Map<String, Object> entries = restoreFromString(json, TypeRef.MAP);
        /** 2021-02-18: In some rare cases "error" can be true WITHOUT "reason" and without "message"! */
        final boolean isError = entries.containsKey("error") ? ((Boolean) entries.get("error")).booleanValue() : false;
        if (!isError) {
            /* No error */
            return entries;
        }
        final String reason = (String) entries.get("reason");
        // final String message = (String) entries.get("message");
        if (reason == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        } else if (reason.equalsIgnoreCase("notfound")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (reason.equalsIgnoreCase("AccountNotFound") || reason.equalsIgnoreCase("InvalidPassword")) {
            throw new AccountInvalidException();
        } else if (reason.equalsIgnoreCase("bandwidth")) {
            throw new AccountUnavailableException("Bandwidth limit reached", 5 * 60 * 1000l);
        } else if (reason.equalsIgnoreCase("dlreached")) {
            /* Free download limit reached */
            throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "You have reached your download limit");
        } else {
            if (link == null) {
                throw new AccountUnavailableException("Unknown error: " + reason, 5 * 60 * 1000l);
            } else {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown error: " + reason);
            }
        }
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        final Map<String, Object> entries = login(account, true);
        final Map<String, Object> userinfo = (Map<String, Object>) entries.get("me");
        final Map<String, Object> spaceInfo = (Map<String, Object>) userinfo.get("disk");
        final Map<String, Object> trafficInfo = (Map<String, Object>) userinfo.get("bandwidth");
        final String email = (String) userinfo.get("em");
        if (!StringUtils.equalsIgnoreCase(account.getUser(), email)) {
            account.setUser(email);
        }
        final long trafficMax = ((Number) trafficInfo.get("total")).longValue();
        final long trafficUsed = ((Number) trafficInfo.get("used")).longValue();
        ai.setTrafficMax(trafficMax);
        ai.setTrafficLeft(trafficMax - trafficUsed);
        ai.setUsedSpace(((Number) spaceInfo.get("used")).longValue());
        if (Boolean.TRUE.equals(userinfo.get("hasPremium"))) {
            /* E.g. "1 Month" */
            final String proPlan = (String) userinfo.get("proPlan");
            final long expireTimestamp = JavaScriptEngineFactory.toLong(userinfo.get("premiumExpires"), 0);
            ai.setValidUntil(expireTimestamp * 1000l, this.br);
            account.setType(AccountType.PREMIUM);
            account.setMaxSimultanDownloads(this.getMaxSimultanPremiumDownloadNum());
            account.setConcurrentUsePossible(true);
            ai.setStatus("Premium account | " + proPlan);
        } else {
            account.setType(AccountType.FREE);
            /* Free accounts can still have captcha */
            account.setConcurrentUsePossible(false);
        }
        return ai;
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        login(account, false);
        handleDownload(link, account);
    }

    private void handleDownload(final DownloadLink link, final Account account) throws Exception, PluginException {
        if (!this.attemptStoredDownloadurlDownload(link, account)) {
            requestFileInformation(link, account);
            final boolean isPremium = account != null && AccountType.PREMIUM.equals(account.getType());
            if (isPremiumonly(link) && !isPremium) {
                throw new AccountRequiredException();
            }
            final Map<String, Object> data = new HashMap<String, Object>();
            if (account != null) {
                data.put("ud", this.accountGetUD(account));
                data.put("ut", this.accountGetUT(account));
            }
            data.put("passw", link.getDownloadPassword());
            data.put("fileid", this.getFID(link));
            if (account == null || account.getType() != AccountType.PREMIUM) {
                final Browser brc = br.cloneBrowser();
                brc.getPage("/zuz-assets/themes/drive/js/bct.js");
                String rcKey = br.getRegex("sitekey:\"([^\"]+)\"").getMatch(0);
                if (rcKey == null) {
                    /* Static fallback */
                    rcKey = "6LfdKAYaAAAAACFOmDNWJX4MeGSAEpbRJsbBXkbi";
                }
                final String recaptchaV2Response = new CaptchaHelperHostPluginRecaptchaV2(this, br, rcKey).getToken();
                data.put("captcha", recaptchaV2Response);
            }
            br.postPageRaw("/v1/drive/get_download_link", JSonStorage.serializeToJson(data));
            final Map<String, Object> entries = this.handleErrors(br, link, account);
            final String dllink = (String) entries.get("link");
            if (StringUtils.isEmpty(dllink)) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Failed to find final downloadurl", 5 * 60 * 1000l);
            }
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, this.isResumeable(link, account), this.getMaxChunks(account));
            if (!this.looksLikeDownloadableContent(dl.getConnection())) {
                br.followConnection(true);
                if (dl.getConnection().getResponseCode() == 403) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
                } else if (dl.getConnection().getResponseCode() == 404) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 60 * 60 * 1000l);
                } else {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
            }
            link.setProperty(this.getDirecturlProperty(link, account), dl.getConnection().getURL().toExternalForm());
        }
        dl.startDownload();
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean hasCaptcha(final DownloadLink link, final Account acc) {
        if (acc == null) {
            /* no account, yes we can expect captcha */
            return true;
        } else if (acc.getType() == AccountType.FREE) {
            /* Free accounts can have captchas */
            return true;
        } else {
            /* Premium accounts do not have captchas */
            return false;
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }
}