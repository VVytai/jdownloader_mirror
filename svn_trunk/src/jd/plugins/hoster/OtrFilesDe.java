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

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;

import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.TimeFormatter;
import org.appwork.utils.parser.UrlQuery;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.AccountInvalidException;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision: 53265 $", interfaceVersion = 2, names = {}, urls = {})
public class OtrFilesDe extends PluginForHost {
    public OtrFilesDe(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://www." + getHost() + "/");
    }

    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        ret.add(new String[] { "otr-files.de" });
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

    /** Regex fragment matching the required file-extension suffix, e.g. ".otrkey". */
    private static final String  EXTENSION_SUFFIX = "\\.(?:otrkey|otr2)";
    private static final Pattern PATTERN_OPTION   = Pattern.compile("/index\\.php\\?option=com_content\\&task=view\\&id=\\d+\\&Itemid=\\d+\\&server=\\d+\\&f=[^<>\"']+" + EXTENSION_SUFFIX);
    private static final Pattern PATTERN_FILE     = Pattern.compile("/\\?file=[^<>\"']+" + EXTENSION_SUFFIX);

    public static String[] buildAnnotationUrls(final List<String[]> pluginDomains) {
        final List<String> ret = new ArrayList<String>();
        for (final String[] domains : pluginDomains) {
            ret.add("https?://(?:www\\.)?" + buildHostsPatternPart(domains) + "/(" + PATTERN_OPTION.pattern().substring(1) + "|" + PATTERN_FILE.pattern().substring(1) + ")");
        }
        return ret.toArray(new String[0]);
    }

    private static final String LIMITREACHED = "(>\\s*Die maximale Anzahl Download Links pro Stunde|Versuche es in einer Stunde nochmal oder Spende dann kannst Du soviele Downloads|Limit erreicht<)";
    private static final String NOSLOTS      = "(Server ausgelastet,|>versuche es in ein paar Minuten noch einmal|Server voll)";

    @Override
    public String getAGBLink() {
        return "https://www." + getHost() + "/";
    }

    @Override
    public String getLinkID(final DownloadLink link) {
        /* Filename from URL doubles as a unique id here. */
        final String fname = getDefaultFileName(link);
        if (fname != null) {
            return this.getHost() + "://" + fname;
        } else {
            return super.getLinkID(link);
        }
    }

    @Override
    protected String getDefaultFileName(final DownloadLink link) {
        /* Offline links should also have nice filenames */
        try {
            return getFilenameFromURL(link);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return super.getDefaultFileName(link);
        }
    }

    /**
     * Returns the .otrkey filename from the URL parameter "file" or "f".
     *
     * @throws MalformedURLException
     */
    private String getFilenameFromURL(final DownloadLink link) throws MalformedURLException {
        final UrlQuery query = UrlQuery.parse(link.getPluginPatternMatcher());
        String filename;
        find_filename: {
            filename = query.get("file");
            if (filename != null) {
                break find_filename;
            }
            filename = query.get("f");
        }
        if (filename == null) {
            return null;
        }
        return Encoding.htmlDecode(filename);
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        return requestFileInformation(link, null);
    }

    private AvailableStatus requestFileInformation(final DownloadLink link, final Account account) throws Exception {
        if (account != null) {
            login(account, false);
        }
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getPage(link.getPluginPatternMatcher());
        if (!StringUtils.containsIgnoreCase(br.getURL(), "?otr-files.de/index.php?option=")) {
            br.getPage(findOptionsLink());
        }
        if (!br.containsHTML(">\\s*Verf\\&uuml;gbare Formate auf otr\\-files") && !br.containsHTML(LIMITREACHED) && !br.containsHTML(NOSLOTS) && findDirectDownloadlink(br, account) == null) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (account != null && !this.isLoggedin(br)) {
            throw new AccountInvalidException("Session expired?");
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception, PluginException {
        handleDownload(link, null);
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        handleDownload(link, account);
    }

    /**
     * Property under which the reusable direct-url is stored. Anonymous downloads, and downloads via different account types, get different
     * direct-urls, so they must not share the same cached link.
     */
    private String getDirecturlProperty(final Account account) {
        if (account == null) {
            return "freelink";
        } else {
            return "directurl_account_" + account.getType().name();
        }
    }

    private void handleDownload(final DownloadLink link, final Account account) throws Exception, PluginException {
        final String directurlproperty = getDirecturlProperty(account);
        String dllink = link.getStringProperty(directurlproperty);
        if (dllink != null) {
            try {
                final Browser br2 = br.cloneBrowser();
                final URLConnectionAdapter con = br2.openGetConnection(dllink);
                if (!this.looksLikeDownloadableContent(con)) {
                    link.setProperty(directurlproperty, Property.NULL);
                    dllink = null;
                }
                con.disconnect();
            } catch (Exception e) {
                link.setProperty(directurlproperty, Property.NULL);
                dllink = null;
            }
        }
        if (dllink == null) {
            logger.info("Generating fresh directurl");
            requestFileInformation(link, account);
            if (br.containsHTML(NOSLOTS)) {
                throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Keine freien Slots verfügbar!", (1 + new Random().nextInt(7)) * 60 * 1000l);
            }
            if (br.containsHTML(LIMITREACHED)) {
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, 60 * 60 * 1000l);
            }
            dllink = findDirectDownloadlink(br, account);
            if (dllink == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, 1);
        this.handleConnectionErrors(br, dl.getConnection());
        link.setProperty(directurlproperty, dllink);
        dl.startDownload();
    }

    public void login(final Account account, final boolean validate) throws Exception {
        synchronized (account) {
            br.setCookiesExclusive(true);
            final Cookies cookies = account.loadCookies("");
            if (cookies != null) {
                br.setCookies(cookies);
                if (!validate) {
                    return;
                }
                br.getPage("https://www." + getHost() + "/index.php");
                if (this.isLoggedin(br)) {
                    logger.info("Cookie login successful");
                    account.saveCookies(br.getCookies(br.getHost()), "");
                    return;
                }
                logger.info("Cookie login failed");
                br.clearCookies(null);
            }
            logger.info("Performing full login");
            br.setFollowRedirects(true);
            br.getPage("https://www." + getHost() + "/index.php");
            final Form loginform = br.getFormbyProperty("name", "login");
            if (loginform == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            loginform.put("username", Encoding.urlEncode(account.getUser()));
            loginform.put("passwd", Encoding.urlEncode(account.getPass()));
            loginform.put("remember", "yes");
            br.submitForm(loginform);
            if (!this.isLoggedin(br)) {
                throw new AccountInvalidException();
            }
            account.saveCookies(br.getCookies(br.getHost()), "");
        }
    }

    private boolean isLoggedin(final Browser br) {
        return br.containsHTML("name=\"logout\"");
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        login(account, true);
        final String expire = br.getRegex("Account Ablaufdatum:.*?<font[^>]*>\\s*(\\d{1,2}\\.[A-Za-z]+\\.\\d{4})").getMatch(0);
        if (expire != null) {
            /* The parsed date is at 00:00 of that day; the account is valid until the end of that day (23:59:59). */
            final long endOfDayOffset = (23 * 60 * 60 + 59 * 60 + 59) * 1000l;
            ai.setValidUntil(TimeFormatter.getMilliSeconds(expire.trim(), "dd.MMM.yyyy", Locale.ENGLISH) + endOfDayOffset);
            account.setType(AccountType.PREMIUM);
            ai.setStatus("Spender"); // Donator account
        } else {
            account.setType(AccountType.FREE);
            /* Free accounts have no benefits compared to anonymous downloading. */
            ai.setExpired(true);
        }
        ai.setUnlimitedTraffic();
        return ai;
    }

    private String findOptionsLink() throws Exception {
        String optlink = br.getRegex("\"(https?://(www\\.)?otr-files\\.de/index\\.php\\?option=com_content(?:&amp;|&)task=view(?:&amp;|&)id=\\d+(?:&amp;|&)Itemid=\\d+(?:&amp;|&)server=[a-z0-9]*(?:&amp;|&)f=[^<>\"\\']+" + EXTENSION_SUFFIX + ")\"").getMatch(0);
        if (optlink == null) {
            optlink = br.getRegex("\"(\\.?/index\\.php\\?option=com_content(?:&amp;|&)task=view(?:&amp;|&)id=\\d+(?:&amp;|&)Itemid=\\d+(?:&amp;|&)server=[a-z0-9]*(?:&amp;|&)f=[^<>\"\\']+" + EXTENSION_SUFFIX + ")\"").getMatch(0);
            if (optlink != null) {
                optlink = br.getURL(optlink).toExternalForm();
            }
        }
        if (optlink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        return Encoding.htmlDecode(optlink);
    }

    private String findDirectDownloadlink(final Browser br, final Account account) {
        String dllink;
        if (account != null && AccountType.PREMIUM.equals(account.getType())) {
            /* Premium */
            dllink = br.getRegex("\"(https?://download\\.otr\\-files\\.(de|net)/dl\\d+/\\d+/[a-z0-9]+/[^\"]+" + EXTENSION_SUFFIX + ")\"").getMatch(0);
            return dllink;
        }
        dllink = br.getRegex("\"(https?://otr\\-files\\.(de|net)/dl\\-slot/\\d+/[a-z0-9]+/[^\"]+" + EXTENSION_SUFFIX + ")\"").getMatch(0);
        return dllink;
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        /* See list of premium limits: when accessing a download in premium mode, there is an orange hint with extra information. */
        return 3;
    }
}
