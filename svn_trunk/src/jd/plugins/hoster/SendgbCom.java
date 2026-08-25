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
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.appwork.storage.TypeRef;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision: 53205 $", interfaceVersion = 3, names = {}, urls = {})
public class SendgbCom extends PluginForHost {
    public SendgbCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    /** Property set by the crawler (SendgbComFolder) to identify which file of a transfer this DownloadLink represents. */
    public static final String PROPERTY_SELECTION_ID = "selection_id";

    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForHost, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "sendgb.com" });
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

    private static final Pattern PATTERN_DOWNLOAD = Pattern.compile("/(?:[a-z]{2}/)?download/([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_OLD      = Pattern.compile("/(?:upload/\\?utm_source=)?([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE);

    public static String[] buildAnnotationUrls(final List<String[]> pluginDomains) {
        final List<String> ret = new ArrayList<String>();
        for (final String[] domains : pluginDomains) {
            ret.add("https?://(?:www\\.)?" + buildHostsPatternPart(domains) + "/(" + PATTERN_DOWNLOAD.pattern().substring(1) + "|" + PATTERN_OLD.pattern().substring(1) + ")");
        }
        return ret.toArray(new String[0]);
    }

    @Override
    public String getAGBLink() {
        return "https://www." + getHost() + "/en/terms-of-use.html";
    }

    @Override
    public boolean isResumeable(final DownloadLink link, final Account account) {
        /* Direct downloads are served from Cloudflare R2 storage which supports range requests. */
        return true;
    }

    public int getMaxChunks(final DownloadLink link, final Account account) {
        return 0;
    }

    @Override
    public Browser createNewBrowserInstance() {
        final Browser ret = super.createNewBrowserInstance();
        ret.setCookie(getHost(), "l_code_3", "en");
        return ret;
    }

    @Override
    public String getLinkID(final DownloadLink link) {
        final String fid = getFID(link);
        final String selectionID = link.getStringProperty(PROPERTY_SELECTION_ID);
        if (fid != null && selectionID != null) {
            return this.getHost() + "://" + fid + "/" + selectionID;
        } else if (fid != null) {
            return this.getHost() + "://" + fid;
        } else {
            return super.getLinkID(link);
        }
    }

    private String getFID(final DownloadLink link) {
        final String url = link.getPluginPatternMatcher();
        String fid = new Regex(url, "(?i)https?://[^/]+" + PATTERN_DOWNLOAD.pattern()).getMatch(0);
        if (fid == null) {
            fid = new Regex(url, "(?i)https?://[^/]+" + PATTERN_OLD.pattern()).getMatch(0);
        }
        return fid;
    }

    @Override
    protected String getDefaultFileName(final DownloadLink link) {
        return this.getFID(link);
    }

    /** Contains the storage-key of the selected file after requestFileInformation was called; needed to presign the download. */
    private String selectedFileKey = null;

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        final String fid = this.getFID(link);
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        final Browser brc = br.cloneBrowser();
        final String password = link.getDownloadPassword();
        String apiurl = "https://api." + getHost() + "/api/download/" + fid;
        if (password != null) {
            apiurl += "?password=" + Encoding.urlEncode(password);
        }
        brc.getPage(apiurl);
        if (brc.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final Map<String, Object> response = restoreFromString(brc.getRequest().getHtmlCode(), TypeRef.MAP);
        if (!Boolean.TRUE.equals(response.get("ok"))) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final Map<String, Object> file = findSelectedFile(link, response);
        if (file == null) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        /* Locked/protected files are treated as offline. */
        final Object unlockedFlag = file.containsKey("is_unlocked") ? file.get("is_unlocked") : response.get("is_unlocked");
        if (!Boolean.TRUE.equals(unlockedFlag)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final Object name = file.get("name");
        if (name != null) {
            link.setFinalFileName(name.toString());
        }
        final Object size = file.get("size");
        if (size != null) {
            link.setVerifiedFileSize(((Number) size).longValue());
        }
        this.selectedFileKey = (String) file.get("key");
        return AvailableStatus.TRUE;
    }

    /** Returns the file of the given transfer that this DownloadLink represents (matched via the crawler-set selection_id). */
    @SuppressWarnings("unchecked")
    private Map<String, Object> findSelectedFile(final DownloadLink link, final Map<String, Object> response) {
        final List<Map<String, Object>> files = (List<Map<String, Object>>) response.get("files");
        if (files == null || files.isEmpty()) {
            return null;
        }
        final String selectionID = link.getStringProperty(PROPERTY_SELECTION_ID);
        if (selectionID == null) {
            /* Link was not created by the crawler -> only usable if the transfer contains exactly one file. */
            return files.size() == 1 ? files.get(0) : null;
        }
        for (final Map<String, Object> file : files) {
            if (selectionID.equals(file.get("selection_id"))) {
                return file;
            }
        }
        return null;
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception, PluginException {
        requestFileInformation(link);
        if (this.selectedFileKey == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final String fid = this.getFID(link);
        /* Ask the API to presign a direct download url for the selected file. */
        final Browser brc = br.cloneBrowser();
        String presignurl = "https://api." + getHost() + "/api/download/" + fid + "/presign?key=" + Encoding.urlEncode(this.selectedFileKey);
        final String password = link.getDownloadPassword();
        if (password != null) {
            presignurl += "&password=" + Encoding.urlEncode(password);
        }
        brc.getPage(presignurl);
        final Map<String, Object> response = restoreFromString(brc.getRequest().getHtmlCode(), TypeRef.MAP);
        if (!Boolean.TRUE.equals(response.get("ok"))) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final Object url = response.get("url");
        if (url == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, url.toString(), this.isResumeable(link, null), this.getMaxChunks(link, null));
        if (!this.looksLikeDownloadableContent(dl.getConnection())) {
            br.followConnection(true);
            handleConnectionErrors(br, dl.getConnection());
        }
        dl.startDownload();
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return Integer.MAX_VALUE;
    }
}
