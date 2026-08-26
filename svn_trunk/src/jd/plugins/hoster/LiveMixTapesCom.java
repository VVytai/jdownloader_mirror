//jDownloader - Downloadmanager
//Copyright (C) 2010  JD-Team support@jdownloader.org
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
import java.util.regex.Pattern;

import org.appwork.net.protocol.http.HTTPConstants;
import org.appwork.storage.TypeRef;
import org.appwork.utils.StringUtils;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperHostPluginRecaptchaV2;
import org.jdownloader.plugins.components.antiDDoSForHost;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.http.requests.PostRequest;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
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

@HostPlugin(revision = "$Revision: 53220 $", interfaceVersion = 2, names = {}, urls = {})
public class LiveMixTapesCom extends antiDDoSForHost {
    private static final String               TYPE_REDIRECTLINK             = "https?://(www\\.)?livemixtap\\.es/[a-z0-9]+";
    private static final String               TYPE_DIRECTLINK               = "https?://club\\.livemixtapes\\.com/play/\\d+";
    private static final String               TYPE_ALBUM                    = "https?://(?:www\\.)?livemixtapes\\.com/download/\\d+.*?";
    private static final String               TYPE_MIXTAPE                  = "https?://(?:www\\.)?livemixtapes\\.com/mixtape/([^/]+)";
    protected static HashMap<String, Cookies> antiCaptchaCookies            = new HashMap<String, Cookies>();
    private final String                      PROPERTY_DIRECTURL            = "directurl";
    private static final String               PROPERTY_ACCOUNT_ACCESS_TOKEN = "access_token";
    private static final String               PROPERTY_ACCOUNT_USER_ID      = "user_id";

    public LiveMixTapesCom(PluginWrapper wrapper) {
        super(wrapper);
        // Currently there is only support for free accounts
        this.enablePremium("https://" + getHost() + "/premium");
    }

    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForHost, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "livemixtapes.com" });
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
        return buildAnnotationUrls(getPluginDomains());
    }

    private static final Pattern PATTERN_DOWNLOAD = Pattern.compile("/download(?:/mp3)?/(\\d+)/([a-z0-9\\-]+)\\.html");
    private static final Pattern PATTERN_MIXTAPE  = Pattern.compile("/mixtape/([^/]+)");
    private static final Pattern PATTERN_PLAY     = Pattern.compile("/play/(\\d+)");

    public static String[] buildAnnotationUrls(final List<String[]> pluginDomains) {
        final List<String> ret = new ArrayList<String>();
        for (final String[] domains : pluginDomains) {
            ret.add("https?://(?:[a-z0-9]+\\.)?" + buildHostsPatternPart(domains) + "/(" + PATTERN_DOWNLOAD.pattern().substring(1) + "|" + PATTERN_MIXTAPE.pattern().substring(1) + "|" + PATTERN_PLAY.pattern().substring(1) + ")");
        }
        return ret.toArray(new String[0]);
    }

    @Override
    public String getLinkID(final DownloadLink link) {
        final String fid = getFID(link);
        if (fid != null) {
            final String type;
            if (link.getPluginPatternMatcher().matches(TYPE_DIRECTLINK)) {
                type = "direct";
            } else if (link.getPluginPatternMatcher().matches(TYPE_ALBUM)) {
                type = "download_album";
            } else if (link.getPluginPatternMatcher().matches(TYPE_MIXTAPE)) {
                type = "download_mixtape";
            } else {
                type = "download_single";
            }
            return this.getHost() + "://" + fid + type;
        } else {
            return super.getLinkID(link);
        }
    }

    private String getFID(final DownloadLink link) {
        String ret = new Regex(link.getPluginPatternMatcher(), "/(\\d+)(?:/[a-z0-9\\-]+\\.html)?$").getMatch(0);
        if (ret == null) {
            ret = new Regex(link.getPluginPatternMatcher(), "/mixtape/([^/]+)").getMatch(0);
        }
        return ret;
    }

    @Override
    protected Browser prepBrowser(final Browser prepBr, final String host) {
        if ((browserPrepped.containsKey(prepBr) && browserPrepped.get(prepBr) == Boolean.TRUE)) {
            return prepBr;
        }
        loadAntiCaptchaCookies(prepBr, host);
        prepBr.getHeaders().put("Accept-Encoding", "gzip, deflate, br");
        prepBr.getHeaders().put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.61 Safari/537.36");
        prepBr.setFollowRedirects(true);
        return super.prepBrowser(prepBr, host);
    }

    protected void loadAntiCaptchaCookies(final Browser prepBr, final String host) {
        synchronized (antiCaptchaCookies) {
            if (!antiCaptchaCookies.isEmpty()) {
                for (final Map.Entry<String, Cookies> cookieEntry : antiCaptchaCookies.entrySet()) {
                    final String key = cookieEntry.getKey();
                    if (key != null && key.equals(host)) {
                        try {
                            prepBr.setCookies(key, cookieEntry.getValue(), false);
                        } catch (final Throwable e) {
                        }
                    }
                }
            }
        }
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        return requestFileInformation(link, false);
    }

    private boolean isAccountRequired() {
        if (br.containsHTML(">\\s*NO SIGN UP\\s*/\\s*LOGIN REQUIRED")) {
            return false;
        } else if (br.containsHTML("class=\"download-member-only\"")) {
            return true;
        } else {
            return false;
        }
    }

    public AvailableStatus requestFileInformation(final DownloadLink link, final boolean isDownload) throws Exception {
        br.setFollowRedirects(true);
        if (link.getPluginPatternMatcher().matches(TYPE_DIRECTLINK)) {
            URLConnectionAdapter con = null;
            try {
                con = br.openHeadConnection(link.getPluginPatternMatcher());
                if (this.looksLikeDownloadableContent(con)) {
                    if (con.getCompleteContentLength() > 0) {
                        if (con.isContentDecoded()) {
                            link.setDownloadSize(con.getCompleteContentLength());
                        } else {
                            link.setVerifiedFileSize(con.getCompleteContentLength());
                        }
                    }
                    /* Check if final filename has been set in crawler before */
                    if (link.getFinalFileName() == null) {
                        link.setFinalFileName(Encoding.htmlDecode(getFileNameFromConnection(con).trim()));
                    }
                } else {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        } else {
            getPage(link.getPluginPatternMatcher());
            if (isUserVerifyNeeded() && !isDownload) {
                logger.info("Cannot do linkcheck because of antiddos captcha");
                return AvailableStatus.UNCHECKABLE;
            }
            this.handleUserVerify();
            if (br.getHttpConnection().getResponseCode() == 404 || br.containsHTML("(?i)(>Not Found</|The page you requested could not be found\\.<|>This mixtape is no longer available for download.<)")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            String filename = br.getRegex("<title>\\s*(.*?)\\s*(Mixtape\\s*(Hosted by.+))?\\s*<").getMatch(0);
            if (filename == null) {
                /* Fallback */
                filename = getFID(link);
            }
            filename = Encoding.htmlDecode(filename).trim();
            if (!filename.endsWith(".mp3") && !filename.endsWith(".zip")) {
                if (link.getPluginPatternMatcher().matches(TYPE_ALBUM) || link.getPluginPatternMatcher().matches(TYPE_MIXTAPE)) {
                    filename += ".zip";
                } else {
                    filename += ".mp3";
                }
            }
            /* Only set final filename if not e.g. set previously in crawler. */
            if (link.getFinalFileName() == null) {
                link.setFinalFileName(filename);
            }
        }
        return AvailableStatus.TRUE;
    }

    private void handleDownload(final DownloadLink link, final Account account) throws Exception, PluginException {
        if (!attemptStoredDownloadurlDownload(link)) {
            requestFileInformation(link, true);
            handleUserVerify();
            br.setFollowRedirects(false);
            final String dllink;
            if (link.getPluginPatternMatcher().matches(TYPE_DIRECTLINK)) {
                dllink = link.getPluginPatternMatcher();
            } else {
                /* 2020-04-22: Resume possible */
                if (isAccountRequired()) {
                    if (account != null) {
                        /* Should never happen */
                        throw new AccountUnavailableException("Session expired?", 2 * 60 * 1000l);
                    } else {
                        throw new AccountRequiredException();
                    }
                }
                final String download_id = br.getRegex("/images/share/(\\d+)").getMatch(0);
                if (download_id == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                final PostRequest download = br.createPostRequest(" https://api.livemixtapes.com/v3/mixtapes/" + download_id + "/download", "");
                // can be found here _next/static/chunks/pages/_app-981b367ee1df430.js
                download.getHeaders().put("X-Api-Key", "CUZD8nfMwMnjzzADA5U2acZPQW806lX9");
                br.getPage(download);
                final Map<String, Object> response = restoreFromString(br.getRequest().getHtmlCode(), TypeRef.MAP);
                dllink = (String) response.get("download_url");
                if (dllink == null) {
                    final String msg = (String) response.get("message");
                    if ("Unauthorized".equals(msg)) {
                        throw new AccountRequiredException();
                    } else if (!StringUtils.isEmpty(msg)) {
                        throw new PluginException(LinkStatus.ERROR_FATAL, msg);
                    }
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
            }
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, getMaxChunks(link));
            if (!this.looksLikeDownloadableContent(dl.getConnection())) {
                br.followConnection(true);
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown download error");
            }
            link.setProperty(PROPERTY_DIRECTURL, dllink);
        }
        dl.startDownload();
    }

    private int getMaxChunks(final DownloadLink link) {
        if (link.getPluginPatternMatcher().matches(TYPE_DIRECTLINK)) {
            return 0;
        } else {
            return 1;
        }
    }

    private boolean attemptStoredDownloadurlDownload(final DownloadLink link) throws Exception {
        final String url = link.getStringProperty(PROPERTY_DIRECTURL);
        if (StringUtils.isEmpty(url)) {
            return false;
        }
        try {
            final Browser brc = br.cloneBrowser();
            dl = new jd.plugins.BrowserAdapter().openDownload(brc, link, url, true, getMaxChunks(link));
            if (this.looksLikeDownloadableContent(dl.getConnection())) {
                return true;
            } else {
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
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        final Map<String, Object> user = login(account, true);
        ai.setUnlimitedTraffic();
        if (user != null && Boolean.TRUE.equals(user.get("premium"))) {
            account.setType(AccountType.PREMIUM);
        } else {
            account.setType(AccountType.FREE);
        }
        return ai;
    }

    private void handleUserVerify() throws Exception {
        synchronized (antiCaptchaCookies) {
            if (isUserVerifyNeeded()) {
                /* Handle login-captcha if required */
                final DownloadLink dlinkbefore = this.getDownloadLink();
                final DownloadLink dl_dummy;
                if (dlinkbefore != null) {
                    dl_dummy = dlinkbefore;
                } else {
                    /* E.g. captcha happens during accountcheck and not regular download. */
                    dl_dummy = new DownloadLink(this, "Account", this.getHost(), "https://" + br.getHost(), true);
                    this.setDownloadLink(dl_dummy);
                }
                Form captchaForm = br.getFormByInputFieldPropertyKeyValue("submit", "Submit");
                if (captchaForm == null) {
                    captchaForm = br.getForm(0);
                }
                if (captchaForm == null) {
                    logger.warning("Failed to find captchaForm");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                final String recaptchaV2Response = new CaptchaHelperHostPluginRecaptchaV2(this, br).getToken();
                if (dlinkbefore != null) {
                    this.setDownloadLink(dlinkbefore);
                }
                captchaForm.put("g-recaptcha-response", recaptchaV2Response);
                br.submitForm(captchaForm);
                antiCaptchaCookies.put(this.getHost(), this.br.getCookies(this.getHost()));
            }
        }
    }

    private boolean isUserVerifyNeeded() {
        return br.getURL().contains("verify-user.php");
    }

    @Override
    public String getAGBLink() {
        return "https://" + getHost() + "/terms-of-use";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception, PluginException {
        handleDownload(link, null);
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        /* First login, then availablecheck --> Avoids captchas in availablecheck! */
        login(account, false);
        handleDownload(link, account);
    }

    /**
     * Logs in and returns the parsed user information map, or null. </br>
     * On a fresh full login this returns the "user" object of the login response; on a validated token login it returns the "data"
     * object of the user-info response. Returns null when no validation was performed (validate == false with a stored token).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> login(final Account account, final boolean validate) throws Exception {
        synchronized (account) {
            final String storedToken = account.getStringProperty(PROPERTY_ACCOUNT_ACCESS_TOKEN);
            final String user_id_property = PROPERTY_ACCOUNT_USER_ID + "_" + account.getUser();
            if (storedToken != null) {
                logger.info("Attempting token login");
                setLoginHeader(storedToken);
                if (!validate) {
                    /* Do not validate token */
                    return null;
                }
                final long storedUserID = account.getLongProperty(user_id_property, -1);
                br.getPage("https://api." + getHost() + "/v3/users/" + storedUserID);
                if (br.getHttpConnection().getResponseCode() == 200) {
                    logger.info("Token login successful");
                    final Map<String, Object> response = restoreFromString(br.getRequest().getHtmlCode(), TypeRef.MAP);
                    return (Map<String, Object>) response.get("data");
                }
                logger.info("Token login failed");
                br.getHeaders().remove(HTTPConstants.HEADER_REQUEST_AUTHORIZATION);
            }
            logger.info("Performing full login");
            final Map<String, Object> postdata = new HashMap<String, Object>();
            postdata.put("user", account.getUser());
            postdata.put("pass", account.getPass());
            br.getPage(br.createJSonPostRequest("https://api." + getHost() + "/v3/auth/login", postdata));
            final Map<String, Object> response = restoreFromString(br.getRequest().getHtmlCode(), TypeRef.MAP);
            final String accessToken = (String) response.get("access_token");
            if (StringUtils.isEmpty(accessToken)) {
                throw new AccountInvalidException();
            }
            final Map<String, Object> user = (Map<String, Object>) response.get("user");
            final Number userID = (Number) user.get("id");
            if (userID == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            account.setProperty(PROPERTY_ACCOUNT_ACCESS_TOKEN, accessToken);
            account.setProperty(user_id_property, userID.longValue());
            setLoginHeader(accessToken);
            return user;
        }
    }

    private void setLoginHeader(final String accessToken) {
        br.getHeaders().put(HTTPConstants.HEADER_REQUEST_AUTHORIZATION, "Bearer " + accessToken);
    }

    @Override
    public boolean hasCaptcha(final DownloadLink link, final jd.plugins.Account acc) {
        return true;
    }
}