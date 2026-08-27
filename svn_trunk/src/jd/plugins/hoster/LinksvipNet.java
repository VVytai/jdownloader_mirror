//jDownloader - Downloadmanager
//Copyright (C) 2013  JD-Team support@jdownloader.org
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
import java.util.Locale;
import java.util.Map;

import org.appwork.storage.TypeRef;
import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.formatter.TimeFormatter;
import org.appwork.utils.parser.UrlQuery;
import org.jdownloader.plugins.controller.LazyPlugin;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.AccountInvalidException;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.MultiHostHost;
import jd.plugins.MultiHostHost.MultihosterHostStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.MultiHosterManagement;

@HostPlugin(revision = "$Revision: 53253 $", interfaceVersion = 3, names = {}, urls = {})
public class LinksvipNet extends PluginForHost {
    private static final String          NICE_HOST             = "linksvip.net";
    private static final String          NICE_HOSTproperty     = NICE_HOST.replaceAll("(\\.|\\-)", "");
    private static final boolean         USE_API               = false;
    private final String                 website_html_loggedin = "/login/logout\\.php";
    private static MultiHosterManagement mhm                   = new MultiHosterManagement("linksvip.net");

    public LinksvipNet(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://" + getHost() + "/premium.html");
    }

    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        ret.add(new String[] { "linksvip.net" });
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
            /* No regex. This is a multihoster. */
            ret.add("");
        }
        return ret.toArray(new String[0]);
    }

    @Override
    public String getAGBLink() {
        return "https://" + getHost() + "/";
    }

    @Override
    public Browser createNewBrowserInstance() {
        final Browser br = super.createNewBrowserInstance();
        br.setFollowRedirects(true);
        /**
         * 2026-08-27: Our default User-Agent is blocked <br>
         * When trying to login with blocked UA, we get this response: {"status":"0","message":"Sai t\u00ean \u0111\u0103ng nh\u1eadp
         * ho\u1eb7c m\u1eadt kh\u1ea9u"}
         */
        br.getHeaders().put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36");
        return br;
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws PluginException {
        return AvailableStatus.UNCHECKABLE;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
    }

    @Override
    public void handlePremium(DownloadLink link, Account account) throws Exception {
        /* handle premium should never be called */
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
    }

    @Override
    public boolean isResumeable(final DownloadLink link, final Account account) {
        return true;
    }

    public int getMaxChunks(final DownloadLink link, final Account account) {
        return 0;
    }

    @Override
    public LazyPlugin.FEATURE[] getFeatures() {
        return new LazyPlugin.FEATURE[] { LazyPlugin.FEATURE.MULTIHOST };
    }

    @Override
    public void handleMultiHost(final DownloadLink link, final Account account) throws Exception {
        loginWebsite(account, false);
        final String dllink = getDllink(link, account);
        if (StringUtils.isEmpty(dllink)) {
            mhm.handleErrorGeneric(account, link, "dllinknull", 2, 5 * 60 * 1000l);
        }
        handleDL(account, link, dllink);
    }

    private String getDllink(final DownloadLink link, final Account account) throws IOException, PluginException {
        String dllink = checkDirectLink(link, NICE_HOSTproperty + "directlink");
        if (dllink == null) {
            if (USE_API) {
                dllink = getDllinkAPI(link, account);
            } else {
                dllink = getDllinkWebsite(link, account);
            }
        }
        return dllink;
    }

    private String getDllinkAPI(final DownloadLink link, final Account account) throws IOException, PluginException {
        return null;
    }

    private String getDllinkWebsite(final DownloadLink link, final Account account) throws IOException, PluginException {
        br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        br.getHeaders().put("Accept", "application/json, text/javascript, */*; q=0.01");
        br.postPage("https://" + this.getHost() + "/GetLinkFs", "pass=undefined&hash=undefined&captcha=&link=" + Encoding.urlEncode(link.getDefaultPlugin().buildExternalDownloadURL(link, this)));
        final Map<String, Object> entries = this.restoreFromString(br.getRequest().getHtmlCode(), TypeRef.MAP);
        this.checkErrors(br, link, account, entries);
        final String dllink = (String) entries.get("linkvip");
        return dllink;
    }

    private void handleDL(final Account account, final DownloadLink link, final String dllink) throws Exception {
        link.setProperty(NICE_HOSTproperty + "directlink", dllink);
        try {
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, this.isResumeable(link, account), this.getMaxChunks(link, account));
            if (!this.looksLikeDownloadableContent(dl.getConnection())) {
                br.followConnection(true);
                mhm.handleErrorGeneric(account, link, "unknowndlerror", 2, 5 * 60 * 1000l);
            }
            this.dl.startDownload();
        } catch (final Exception e) {
            link.setProperty(NICE_HOSTproperty + "directlink", Property.NULL);
            throw e;
        }
    }

    private String checkDirectLink(final DownloadLink link, final String property) {
        final String dllink = link.getStringProperty(property);
        if (dllink != null) {
            URLConnectionAdapter con = null;
            try {
                final Browser br2 = br.cloneBrowser();
                br2.setFollowRedirects(true);
                con = br2.openHeadConnection(dllink);
                if (this.looksLikeDownloadableContent(con)) {
                    if (con.getCompleteContentLength() > 0) {
                        link.setVerifiedFileSize(con.getCompleteContentLength());
                    }
                    return dllink;
                } else {
                    throw new IOException();
                }
            } catch (final Exception e) {
                link.removeProperty(property);
                logger.log(e);
                return null;
            } finally {
                if (con != null) {
                    con.disconnect();
                }
            }
        }
        return null;
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai;
        if (USE_API) {
            ai = fetchAccountInfoAPI(account);
        } else {
            ai = fetchAccountInfoWebsite(account);
        }
        return ai;
    }

    public AccountInfo fetchAccountInfoWebsite(final Account account) throws Exception {
        /*
         * 2017-11-29: Lifetime premium not (yet) supported via website mode! But by the time we might need the website version again, they
         * might have stopped premium lifetime sales already as that has never been a good idea for any (M)OCH.
         */
        final AccountInfo ai = new AccountInfo();
        loginWebsite(account, true);
        br.getPage("https://" + this.getHost() + "/");
        /*
         * The host-support table lists availability per account tier in three columns: FREE, VIP and PRE (Premium). Determine which column
         * applies to the current account: 0 = FREE, 1 = VIP, 2 = PRE.
         */
        final int accountColumnIndex;
        if (br.containsHTML("class=\"badge\"[^>]+>Premium</span>")) {
            account.setType(AccountType.PREMIUM);
            accountColumnIndex = 2;
        } else if (br.containsHTML("class=\"badge\"[^>]+>VIP</span>")) {
            /* VIP is a paid tier below Premium. JDownloader has no dedicated VIP type -> treat it as premium. */
            account.setType(AccountType.PREMIUM);
            accountColumnIndex = 1;
        } else {
            account.setType(AccountType.FREE);
            accountColumnIndex = 0;
        }
        if (account.getType() == AccountType.PREMIUM) {
            final String expire = br.getRegex("Hạn dùng <span [^>]*?>(\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2} (?:AM|PM))</span>").getMatch(0);
            if (expire != null) {
                /* Only set expiredate if we find it */
                ai.setValidUntil(TimeFormatter.getMilliSeconds(expire, "dd/MM/yyyy hh:mm a", Locale.US), br);
            } else {
                logger.warning("Failed to find premium expire date");
            }
            ai.setUnlimitedTraffic();
        } else {
            ai.setTrafficLeft(0);
        }
        br.getPage("/host-support.html");
        final List<MultiHostHost> mhosts = parseSupportedHosts(br, accountColumnIndex);
        ai.setMultiHostSupportV2(this, mhosts);
        return ai;
    }

    /**
     * Parses the host-support table from /host-support.html into a list of {@link MultiHostHost} objects. </br>
     * Extracts online/offline status, availability for the current account tier and daily traffic limits.
     */
    private List<MultiHostHost> parseSupportedHosts(final Browser br, final int accountColumnIndex) throws PluginException {
        /*
         * First parse the "Daily Limit" table into a map keyed by lowercase domain. Each value holds [used, max, offline] where offline is
         * 1 when the hoster is marked as down. Hosts not listed here are unmetered.
         */
        final Map<String, long[]> dailyLimits = new HashMap<String, long[]>();
        String limitTableHTML = br.getRegex("<table class=\"table-striped dailylimit\"[^>]*>(.*?)</table>").getMatch(0);
        if (limitTableHTML != null) {
            /* Remove commented-out rows so they don't get parsed (e.g. an old fshare "50 links" entry). */
            limitTableHTML = limitTableHTML.replaceAll("(?s)<!--.*?-->", "");
            final String[] limitRows = limitTableHTML.split("<tr>");
            for (final String row : limitRows) {
                final String domain = new Regex(row, "alt=\"([^\"]+)\"").getMatch(0);
                final String maxStr = new Regex(row, "class=\"bw_max\">\\s*([^<]*?)\\s*</td>").getMatch(0);
                if (domain == null || maxStr == null || maxStr.length() == 0) {
                    /* Skip header row and commented-out entries without traffic info. */
                    continue;
                }
                final String usedStr = new Regex(row, "class=\"bw_used\">\\s*([^<]*?)\\s*</td>").getMatch(0);
                final long used = usedStr != null && usedStr.length() > 0 ? SizeFormatter.getSize(usedStr) : 0;
                final long max = SizeFormatter.getSize(maxStr);
                /* up.gif = hoster up, down.gif = hoster down. */
                final long offline = row.contains("down.gif") ? 1 : 0;
                dailyLimits.put(domain.toLowerCase(Locale.ROOT), new long[] { used, max, offline });
            }
        }
        /* Parse the per-host daily links limit, e.g. "Limit 50 links per host every 24 hours." */
        final String linksLimitStr = br.getRegex("Limit\\s*(\\d+)\\s*links per host").getMatch(0);
        final long linksLimitPerHost;
        if (linksLimitStr != null) {
            linksLimitPerHost = Long.parseLong(linksLimitStr);
        } else {
            logger.warning("Failed to find per-host daily links limit -> Using fallback value");
            linksLimitPerHost = 50;
        }
        /* Now parse the host-support table and merge in the daily limits. */
        String supportTableHTML = br.getRegex("<table class=\"table-striped\"[^>]*>(.*?)</table>").getMatch(0);
        if (supportTableHTML == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Failed to find host support table");
        }
        /* Remove commented-out rows so they don't get parsed. */
        supportTableHTML = supportTableHTML.replaceAll("(?s)<!--.*?-->", "");
        final String[] rows = supportTableHTML.split("<tr>");
        final List<MultiHostHost> mhosts = new ArrayList<MultiHostHost>();
        for (final String row : rows) {
            final String domain = new Regex(row, "<strong>\\s*([^<>]+?)\\s*</strong>").getMatch(0);
            if (domain == null) {
                /* Skip table header and empty fragments. */
                continue;
            }
            final String domainLower = domain.toLowerCase(Locale.ROOT);
            final MultiHostHost mhost = new MultiHostHost(domainLower);
            /* Availability cells in column order: FREE, VIP, PRE. */
            final String[] availabilityCells = new Regex(row, "<td>\\s*<center>(.*?)</center>\\s*</td>").getColumn(0);
            final String cellForThisAccount = availabilityCells != null && availabilityCells.length > accountColumnIndex ? availabilityCells[accountColumnIndex] : null;
            /* policy_02 = supported (checkmark), policy_01 = not supported (cross), "Limited" = supported with a daily links limit. */
            final boolean isLimited = cellForThisAccount != null && cellForThisAccount.contains("Limited");
            final boolean availableForThisAccountType = cellForThisAccount != null && (cellForThisAccount.contains("policy_02") || isLimited);
            /*
             * Online/offline: the spinner icons in this table are JS placeholders that only get their real state via AJAX after page load,
             * so they are always "online" in the raw html. The only reliable static status is the up.gif/down.gif in the daily limit table
             * (only available for metered hosts). Hosts without such info are assumed online.
             */
            boolean offline = false;
            /* Daily traffic limit; hosts not listed in the daily limit table are unmetered. */
            final long[] limit = dailyLimits.get(domainLower);
            if (limit != null) {
                offline = limit[2] == 1;
                mhost.setTrafficLeftAndMax(limit[1] - limit[0], limit[1]);
            } else {
                mhost.setUnlimitedTraffic(Boolean.TRUE);
            }
            if (isLimited) {
                /* Hosts flagged "Limited" only allow a fixed number of links per host per day. */
                mhost.setLinksLeftAndMax(linksLimitPerHost, linksLimitPerHost);
            }
            if (offline) {
                mhost.setStatus(MultihosterHostStatus.DEACTIVATED_MULTIHOST);
            } else if (!availableForThisAccountType) {
                mhost.setStatus(MultihosterHostStatus.DEACTIVATED_MULTIHOST_NOT_FOR_THIS_ACCOUNT_TYPE);
            }
            mhosts.add(mhost);
        }
        if (mhosts.isEmpty()) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Failed to find list of supported hosts");
        }
        return mhosts;
    }

    public AccountInfo fetchAccountInfoAPI(final Account account) throws Exception {
        return null;
    }

    private void loginWebsite(final Account account, final boolean force) throws Exception {
        final Cookies cookies = account.loadCookies("");
        if (cookies != null) {
            this.br.setCookies(this.getHost(), cookies);
            if (!force) {
                /* Do not check cookies */
                return;
            }
            /*
             * Even though login is forced first check if our cookies are still valid --> If not, force login!
             */
            br.getPage("https://" + this.getHost() + "/");
            if (this.isLoggedin(br)) {
                logger.info("Cookie login successful");
                account.saveCookies(this.br.getCookies(this.getHost()), "");
                return;
            }
            logger.info("Cookie login failed");
        }
        br.getPage("https://" + this.getHost() + "/");
        br.getHeaders().put("Accept", "application/json, text/javascript, */*; q=0.01");
        br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        br.getHeaders().put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        final UrlQuery query = new UrlQuery();
        query.appendEncoded("auto_login", "checked");
        query.appendEncoded("u", account.getUser());
        query.appendEncoded("p", account.getPass());
        br.postPage("/login/", query);
        final Map<String, Object> entries = this.restoreFromString(br.getRequest().getHtmlCode(), TypeRef.MAP);
        this.checkErrors(br, null, account, entries);
        final Object status = entries.get("status");
        if (status != null && !"1".equals(status.toString())) {
            throw new AccountInvalidException((String) entries.get("message"));
        }
        /* Login should be okay and we should get the cookies now! */
        br.getPage("/login/logined.php");
        if (!this.isLoggedin(br)) {
            throw new AccountInvalidException();
        }
        account.saveCookies(this.br.getCookies(this.getHost()), "");
    }

    /**
     * Checks the parsed json response for known error states. </br>
     * Currently a no-op placeholder - add handling for known API/website error responses here.
     */
    private void checkErrors(final Browser br, final DownloadLink link, final Account account, final Map<String, Object> entries) throws PluginException {
        /* TODO: Add error handling for known error responses here. */
    }

    private boolean isLoggedin(final Browser br) {
        return br.containsHTML(website_html_loggedin);
    }
}
