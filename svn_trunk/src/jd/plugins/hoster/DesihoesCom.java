//jDownloader - Downloadmanager
//Copyright (C) 2017  JD-Team support@jdownloader.org
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

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.appwork.storage.TypeRef;
import org.appwork.utils.StringUtils;
import org.appwork.utils.encoding.Base64;
import org.appwork.utils.formatter.HexFormatter;
import org.jdownloader.plugins.controller.LazyPlugin;

import jd.PluginWrapper;
import jd.http.URLConnectionAdapter;
import jd.parser.Regex;
import jd.plugins.AccountRequiredException;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision: 53307 $", interfaceVersion = 3, names = {}, urls = {})
public class DesihoesCom extends PluginForHost {
    public DesihoesCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public LazyPlugin.FEATURE[] getFeatures() {
        return new LazyPlugin.FEATURE[] { LazyPlugin.FEATURE.XXX };
    }

    /* DEV NOTES */
    // Tags: Porn plugin
    // other:
    /* Connection stuff */
    private static final boolean free_resume       = true;
    private static final int     free_maxchunks    = 0;
    private String               dllink            = null;
    private static final String  TYPE_EMBED        = "(?i)https?://[^/]+/embed/([a-f0-9]+)";
    private static final String  TYPE_NORMAL       = "(?i)https?://[^/]+/video/(\\d+)(/([a-z0-9\\-]+))?";
    private static final String  PROPERTY_VIDEO_ID = "video_id";

    @Override
    public String getAGBLink() {
        return "https://www." + getHost() + "/static/terms";
    }

    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForHost, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "desihoes.com" });
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
            ret.add("https?://(?:www\\.)?" + buildHostsPatternPart(domains) + "/(?:video/\\d+/[a-z0-9\\-]+|embed/[a-f0-9]+)");
        }
        return ret.toArray(new String[0]);
    }

    @Override
    public String getLinkID(final DownloadLink link) {
        String fid = link.getStringProperty(PROPERTY_VIDEO_ID);
        if (fid == null) {
            fid = getFID(link);
        }
        if (fid != null) {
            return this.getHost() + "://" + fid;
        } else {
            return super.getLinkID(link);
        }
    }

    private String getFID(final DownloadLink link) {
        if (link == null || link.getPluginPatternMatcher() == null) {
            return null;
        } else if (link.getPluginPatternMatcher().matches(TYPE_EMBED)) {
            return new Regex(link.getPluginPatternMatcher(), TYPE_EMBED).getMatch(0);
        } else {
            return new Regex(link.getPluginPatternMatcher(), TYPE_NORMAL).getMatch(1);
        }
    }

    private String getURLTitle(final DownloadLink link) {
        return getURLTitle(link.getPluginPatternMatcher());
    }

    private String getWeakFilename(final DownloadLink link) {
        final String urlTitle = getURLTitle(link.getPluginPatternMatcher());
        if (urlTitle != null) {
            return urlTitle.replace("-", " ").trim() + ".mp4";
        } else {
            return this.getFID(link) + ".mp4";
        }
    }

    private String getURLTitle(final String url) {
        return new Regex(url, TYPE_NORMAL).getMatch(2);
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        return requestFileInformation(link, false);
    }

    public AvailableStatus requestFileInformation(final DownloadLink link, final boolean isDownload) throws Exception {
        dllink = null;
        this.setBrowserExclusive();
        br.setAllowedResponseCodes(new int[] { 500 });
        br.setFollowRedirects(true);
        br.getPage(link.getPluginPatternMatcher());
        if (br.getHttpConnection().getResponseCode() == 404 || br.getHttpConnection().getResponseCode() == 500) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (!this.canHandle(br.getURL())) {
            /* E.g. redirect to /notfound/video_missing */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        boolean findRealVideoID = false;
        if (br.getURL().matches(TYPE_EMBED)) {
            final String realVideoURL = br.getRegex(TYPE_NORMAL).getMatch(-1);
            if (realVideoURL == null) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            link.setPluginPatternMatcher(realVideoURL);
            br.getPage(realVideoURL);
        }
        final Regex urlinfo = new Regex(br.getURL(), TYPE_NORMAL);
        if (findRealVideoID) {
            final String realVideoID = urlinfo.getMatch(0);
            if (realVideoID != null) {
                link.setLinkID(this.getHost() + "://" + realVideoID);
                link.setProperty(PROPERTY_VIDEO_ID, realVideoID);
            }
        }
        final String titleByURL = urlinfo.getMatch(2);
        if (titleByURL != null) {
            link.setFinalFileName(titleByURL.replace("-", " ").trim() + ".mp4");
        }
        /**
         * 2026-09-03: Video URLs are AES encrypted inside a "vid_files" JSON blob. </br>
         * Key/IV are derived from a per-pageload "vitem" seed: SHA256(part0)[0:32] = key, SHA256(part1)[0:16] = iv. </br>
         * The "src" value is base64(base64(AES-CBC-ciphertext)); the decrypted plaintext is the direct mp4 URL.
         */
        dllink = getEncryptedDllink();
        if (dllink == null) {
            /* Fallback: older/plain pages */
            dllink = br.getRegex("<source src=\"(https?://[^\"]+\\.mp4)\"").getMatch(0);
        }
        if (dllink == null) {
            /* E.g. desihoes.com */
            dllink = br.getRegex("\"(https?://[^\"]+\\.mp4)\"").getMatch(0);
        }
        if (!StringUtils.isEmpty(dllink) && !isDownload && !link.isSizeSet()) {
            URLConnectionAdapter con = null;
            try {
                con = br.openHeadConnection(this.dllink);
                if (!this.looksLikeDownloadableContent(con)) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown server error", 10 * 60 * 1000l);
                } else {
                    if (con.getCompleteContentLength() > 0) {
                        if (con.isContentDecoded()) {
                            link.setDownloadSize(con.getCompleteContentLength());
                        } else {
                            link.setVerifiedFileSize(con.getCompleteContentLength());
                        }
                    }
                }
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception {
        requestFileInformation(link, true);
        if (br.containsHTML(">\\s*This is a private video")) {
            /* 2021-11-25: You must be friends with user blabla to be able to view this content... */
            throw new AccountRequiredException("Private video");
        } else if (StringUtils.isEmpty(dllink)) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, free_resume, free_maxchunks);
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
        dl.startDownload();
    }

    /**
     * Extracts and decrypts the highest quality video URL from the encrypted "vid_files" blob. </br>
     * Returns null if the required values are missing so the caller can fall back to legacy parsing.
     */
    @SuppressWarnings("unchecked")
    private String getEncryptedDllink() throws Exception {
        final String vitem = br.getRegex("var\\s+vitem\\s*=\\s*\"([^\"]+)\"").getMatch(0);
        if (vitem == null) {
            return null;
        }
        final String[] parts = vitem.split("\\.");
        if (parts.length != 2) {
            return null;
        }
        /* Grab the complete "vid_files" blob. In the HTML it is built via JS string concatenation ('a'+'b'+...). */
        final String vidFilesRaw = br.getRegex("let\\s+vid_files\\s*=\\s*'(.*?)';").getMatch(0);
        if (vidFilesRaw == null) {
            return null;
        }
        /* Merge the concatenated JS string fragments and remove trailing commas so it becomes valid JSON. */
        String json = vidFilesRaw.replaceAll("'\\s*\\+\\s*'", "");
        json = json.replaceAll(",\\s*]", "]");
        json = json.replaceAll(",\\s*}", "}");
        final Map<String, Object> root = restoreFromString(json, TypeRef.MAP);
        final List<Object> vidFiles = (List<Object>) root.get("vid_files");
        if (vidFiles == null || vidFiles.isEmpty()) {
            return null;
        }
        /* Pick the highest available resolution. */
        String bestSrc = null;
        int bestRes = -1;
        for (final Object o : vidFiles) {
            final Map<String, Object> entry = (Map<String, Object>) o;
            final int res = Integer.parseInt(entry.get("res").toString());
            if (res > bestRes) {
                bestRes = res;
                bestSrc = entry.get("src").toString();
            }
        }
        if (bestSrc == null) {
            return null;
        }
        final String decrypted = decryptVideoURL(bestSrc, parts[0], parts[1]);
        if (decrypted != null && decrypted.matches("(?i)https?://.+")) {
            return decrypted;
        }
        return null;
    }

    /** Java equivalent of the site's player_decrypt JS function (AES-256-CBC). */
    private String decryptVideoURL(final String src, final String secretKey, final String secretIv) throws Exception {
        final MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        final String keyStr = HexFormatter.byteArrayToHex(sha256.digest(secretKey.getBytes("UTF-8"))).substring(0, 32);
        sha256.reset();
        final String ivStr = HexFormatter.byteArrayToHex(sha256.digest(secretIv.getBytes("UTF-8"))).substring(0, 16);
        /* src = base64( base64( AES ciphertext ) ) */
        final byte[] innerBytes = Base64.decode(src);
        final byte[] cipherBytes = Base64.decode(new String(innerBytes, "ISO-8859-1"));
        final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyStr.getBytes("UTF-8"), "AES"), new IvParameterSpec(ivStr.getBytes("UTF-8")));
        return new String(cipher.doFinal(cipherBytes), "UTF-8");
    }

    @Override
    protected String getDefaultFileName(final DownloadLink link) {
        return getWeakFilename(link);
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return Integer.MAX_VALUE;
    }
}
