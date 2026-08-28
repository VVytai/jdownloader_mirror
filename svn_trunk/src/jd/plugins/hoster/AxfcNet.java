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
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.net.URLHelper;
import org.appwork.utils.parser.UrlQuery;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.download.HashInfo;

@HostPlugin(revision = "$Revision: 53263 $", interfaceVersion = 3, names = {}, urls = {})
public class AxfcNet extends PluginForHost {
    public AxfcNet(PluginWrapper wrapper) {
        super(wrapper);
    }

    private final String PROPERTY_ALLOW_DOWNLOAD_PASSWORD_FROM_URL = "allow_download_password_from_url";

    @Override
    public Browser createNewBrowserInstance() {
        final Browser br = super.createNewBrowserInstance();
        br.setFollowRedirects(true);
        return br;
    }

    @Override
    public String getAGBLink() {
        return "https://" + getHost();
    }

    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForHost, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "axfc.net" });
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
            /* 2026-08-28: Also allow subdomains like "www2." RE forum 98943 */
            ret.add("https?://(?:www\\d*\\.)?" + buildHostsPatternPart(domains) + PATTERN_FILE.pattern());
        }
        return ret.toArray(new String[0]);
    }

    private static final Pattern PATTERN_FILE = Pattern.compile("/(u|uploader/[^/]+/so)/(\\d+)[^/]*");

    @Override
    public String getPluginContentURL(final DownloadLink link) {
        final String pw = link.getDownloadPassword();
        if (pw != null) {
            /**
             * Assume that we got a valid download password -> Return url with password parameter. <br>
             * This also fixes problems with user-added invalid URLs where parameters start with "&".
             */
            final Regex urlinfo = new Regex(link.getPluginPatternMatcher(), PATTERN_FILE);
            final String url = "https://" + this.getHost() + "/" + urlinfo.getMatch(0) + "/" + urlinfo.getMatch(1) + "?key=" + Encoding.urlEncode(pw);
            return url;
        }
        return super.getPluginContentURL(link);
    }

    @Override
    public String getLinkID(final DownloadLink link) {
        final String fid = getFID(link);
        if (fid != null) {
            return this.getHost() + "://" + fid;
        } else {
            return super.getLinkID(link);
        }
    }

    private String getFID(final DownloadLink link) {
        return new Regex(link.getPluginPatternMatcher(), PATTERN_FILE).getMatch(1);
    }

    @Override
    protected String getDefaultFileName(final DownloadLink link) {
        return this.getFID(link);
    }

    @Override
    public boolean isResumeable(final DownloadLink link, final Account account) {
        return true;
    }

    public int getMaxChunks(final DownloadLink link, final Account account) {
        return 1;
    }

    private String getDownloadPasswordFromURL(final DownloadLink link) {
        final boolean allowPassCodeFromURL = link.getBooleanProperty(PROPERTY_ALLOW_DOWNLOAD_PASSWORD_FROM_URL, true);
        if (!allowPassCodeFromURL) {
            return null;
        }
        try {
            /*
             * Workaround for bug in UrlQuery which prevents it from parsing URLs where parameters start with "&" instead of "?" while it is
             * supposed to be able to parse such URLs.
             */
            String url = link.getPluginPatternMatcher();
            if (!url.contains("?") && url.contains("&")) {
                url = url.replaceFirst("&", "?");
            }
            String passCodeFromURL = UrlQuery.parse(url).get("key");
            if (passCodeFromURL == null) {
                return null;
            }
            passCodeFromURL = Encoding.htmlDecode(passCodeFromURL);
            return passCodeFromURL;
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String getUrlWithoutParams(String url) {
        try {
            String urlnew = URLHelper.getUrlWithoutParams(url);
            /* Cover edge case: parameters starting with "&" -> cut off at the first "&" as that marks the beginning of the parameters. */
            final int andIndex = urlnew.indexOf("&");
            if (andIndex != -1) {
                return urlnew.substring(0, andIndex);
            }
            return urlnew;
        } catch (final MalformedURLException e) {
            return url;
        }
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        /* Important: Use URL without parameters here to avoid http response 403 due to invalid content in "key" parameter */
        br.getPage(getUrlWithoutParams(link.getPluginPatternMatcher()));
        if (br.getHttpConnection().getResponseCode() == 403) {
            /* Invalid url e.g. /u/3791809&key=%E3%81%BC%E3%81%9F%E3%82%82%E3%81%A1.%20Read%20More.%20%5B0%5D%20Likes.%20Rating:%20N */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND, "Invalid url");
        }
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("name=\"download\"[^>]*>\\s*</a>\\s*<h2>Download \\d+\\.[^\\(]*\\(([^<]+)\\)\\s*</h2>").getMatch(0);
        final String filesize = br.getRegex("<b>\\s*Size\\s*</b>\\s*</span>\\s*<span[^>]*>([^<]+)</span>").getMatch(0);
        if (filename != null) {
            filename = Encoding.htmlDecode(filename).trim();
            /**
             * Set final filename here because they return bad content-disposition header e.g. <br>
             * Content-Disposition: attachment; filename*=UTF-8''
             */
            link.setFinalFileName(filename);
        } else {
            logger.warning("Failed to find filename");
        }
        if (filesize != null) {
            link.setDownloadSize(SizeFormatter.getSize(filesize));
        } else {
            logger.warning("Failed to find filesize");
        }
        final List<HashInfo> hashInfos = new ArrayList<HashInfo>();
        final String hash_md5 = br.getRegex("'MD5 HASH',\\s*'([a-f0-9]{32})").getMatch(0);
        if (hash_md5 != null) {
            hashInfos.add(HashInfo.newInstanceSafe(hash_md5, HashInfo.TYPE.MD5));
        } else {
            logger.info("Failed to find hash_md5");
        }
        final String hash_sha1 = br.getRegex("'SHA-1 HASH',\\s*'([a-f0-9]{40})").getMatch(0);
        if (hash_sha1 != null) {
            hashInfos.add(HashInfo.newInstanceSafe(hash_sha1, HashInfo.TYPE.SHA1));
        } else {
            logger.info("Failed to find hash_sha1");
        }
        final String hash_sha256 = br.getRegex("'SHA-256 HASH',\\s*'([a-f0-9]{64})").getMatch(0);
        if (hash_sha256 != null) {
            hashInfos.add(HashInfo.newInstanceSafe(hash_sha256, HashInfo.TYPE.SHA256));
        } else {
            logger.info("Failed to find hash_sha256");
        }
        link.setHashInfos(hashInfos);
        if (StringUtils.isEmpty(link.getComment())) {
            final String description = br.getRegex("<h3>\\s*File description\\s*</h3>\\s*<p>([^<]+)</p>").getMatch(0);
            if (description != null) {
                link.setComment(Encoding.htmlDecode(description).trim());
            }
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception, PluginException {
        handleDownload(link);
    }

    private void handleDownload(final DownloadLink link) throws Exception, PluginException {
        requestFileInformation(link);
        final Form dlform = getDownloadform(br);
        if (dlform == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final String downloadPasswordFromURL = this.getDownloadPasswordFromURL(link);
        String passCode = null;
        if (dlform.hasInputFieldByName("keyword")) {
            link.setPasswordProtected(true);
            passCode = downloadPasswordFromURL;
            if (passCode == null) {
                passCode = link.getDownloadPassword();
            }
            if (passCode == null) {
                passCode = getUserInput("Password?", link);
            }
            dlform.put("keyword", Encoding.urlEncode(passCode));
        } else {
            link.setPasswordProtected(false);
        }
        /* Captcha is not always required */
        final String captchaURL = br.getRegex("\"(/u/captcha\\.pl[^\"]+)").getMatch(0);
        if (captchaURL != null) {
            final String code = this.getCaptchaCode(captchaURL, link);
            dlform.put("cpt", Encoding.urlEncode(code));
        }
        br.submitForm(dlform);
        /* Check for invalid captcha */
        if (captchaURL != null && br.containsHTML(">\\s*Captcha authentication failed")) {
            throw new PluginException(LinkStatus.ERROR_CAPTCHA);
        }
        /* Check for invalid password */
        if (passCode != null) {
            if (getDownloadform(br) != null || br.containsHTML(">\\s*キーワードが正しくありません|>\\s*入力されたキーワードが設定されたキーワードと相違しています")) {
                /* Wrong password was entered */
                if (downloadPasswordFromURL != null && !link.hasProperty(PROPERTY_ALLOW_DOWNLOAD_PASSWORD_FROM_URL)) {
                    /* Password in url parameter is wrong -> Do not try that one again! */
                    logger.info("Password in url parameter 'key' is invalid -> Do not retry that one again!");
                    link.setProperty(PROPERTY_ALLOW_DOWNLOAD_PASSWORD_FROM_URL, false);
                }
                link.setDownloadPassword(null);
                throw new PluginException(LinkStatus.ERROR_RETRY, "Wrong password entered");
            }
            /* Correct password was entered -> Save it */
            link.setDownloadPassword(passCode);
        }
        final String continuelink = br.getRegex("a href=\"([^\"]+)\"[^>]*>\\s*＜\\s*Download").getMatch(0);
        if (StringUtils.isEmpty(continuelink)) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        br.getPage(continuelink);
        final String dllink = br.getRegex("<a href=\"([^\"]+)\"[^>]*>\\s*To start download").getMatch(0);
        if (StringUtils.isEmpty(dllink)) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, this.isResumeable(link, null), this.getMaxChunks(link, null));
        if (!this.looksLikeDownloadableContent(dl.getConnection())) {
            br.followConnection(true);
            if (dl.getConnection().getResponseCode() == 403) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 5 * 60 * 1000l);
            } else if (dl.getConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 5 * 60 * 1000l);
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    final Form getDownloadform(final Browser br) {
        return br.getFormbyActionRegex(".*dl2\\.pl");
    }

    @Override
    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        return false;
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return Integer.MAX_VALUE;
    }
}