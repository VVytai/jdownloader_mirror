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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.appwork.utils.StringUtils;
import org.jdownloader.plugins.controller.LazyPlugin;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.parser.Regex;
import jd.plugins.AccountRequiredException;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginDependencies;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision: 53223 $", interfaceVersion = 3, names = {}, urls = {})
@PluginDependencies(dependencies = { jd.plugins.decrypter.StileProjectComDecrypter.class })
public class StileProjectCom extends PluginForHost {
    private String                        dllink          = null;
    private static final Pattern          PATTERN_EMBED   = Pattern.compile("/embed/(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern          PATTERN_NORMAL  = Pattern.compile("/video/([a-z0-9\\-_]+)-(\\d+)\\.html", Pattern.CASE_INSENSITIVE);
    private static final Pattern          PATTERN_NORMAL2 = Pattern.compile("/videos/(\\d+)/([a-z0-9\\-_]+)/?", Pattern.CASE_INSENSITIVE);

    public StileProjectCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public LazyPlugin.FEATURE[] getFeatures() {
        return new LazyPlugin.FEATURE[] { LazyPlugin.FEATURE.XXX };
    }

    public static List<String[]> getPluginDomains() {
        return jd.plugins.decrypter.StileProjectComDecrypter.getPluginDomains();
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

    public static String[] buildAnnotationUrls(final List<String[]> pluginDomains) {
        final List<String> ret = new ArrayList<String>();
        for (final String[] domains : pluginDomains) {
            ret.add("https?://(?:www\\.)?" + buildHostsPatternPart(domains) + "/(" + PATTERN_NORMAL.pattern().substring(1) + "|" + PATTERN_EMBED.pattern().substring(1) + "|" + PATTERN_NORMAL2.pattern().substring(1) + ")");
        }
        return ret.toArray(new String[0]);
    }

    @Override
    public String getAGBLink() {
        return "https://www." + getHost() + "/contact";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return Integer.MAX_VALUE;
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

    private String getFID(final DownloadLink link) {
        final String url = link != null ? link.getPluginPatternMatcher() : null;
        if (url == null) {
            return null;
        }
        String fid = new Regex(url, PATTERN_EMBED).getMatch(0);
        if (fid == null) {
            fid = new Regex(url, PATTERN_NORMAL).getMatch(1);
        }
        return fid;
    }

    @Override
    protected String getDefaultFileName(final DownloadLink link) {
        return getWeakFilename(link);
    }

    private String getWeakFilename(final DownloadLink link) {
        final String urlTitle = getURLTitleCleaned(link.getPluginPatternMatcher());
        if (urlTitle != null) {
            return urlTitle.replace("-", " ").trim() + ".mp4";
        } else {
            return this.getFID(link) + ".mp4";
        }
    }

    public static String getURLTitleCleaned(final String url) {
        String title = new Regex(url, PATTERN_NORMAL).getMatch(0);
        if (title == null) {
            title = new Regex(url, PATTERN_NORMAL2).getMatch(1);
        }
        if (title != null) {
            return title.replace("-", " ").trim();
        } else {
            return null;
        }
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        return requestFileInformation(link, false);
    }

    private AvailableStatus requestFileInformation(final DownloadLink link, final boolean isDownload) throws Exception {
        final String extDefault = ".mp4";
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getHeaders().put("Referer", "https://www." + this.getHost());
        br.getPage(link.getPluginPatternMatcher());
        if (isOffline(br)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (new Regex(br.getURL(), PATTERN_EMBED).patternFind()) {
            final String realVideoURL = br.getRegex(PATTERN_NORMAL).getMatch(-1);
            if (realVideoURL == null || !realVideoURL.contains(this.getFID(link))) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            br.getPage(realVideoURL);
            /* Double-check */
            if (isOffline(br)) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            if (this.canHandle(br.getURL())) {
                link.setPluginPatternMatcher(br.getURL());
            }
        }
        String titleByURL = getURLTitleCleaned(br.getURL());
        if (titleByURL == null) {
            titleByURL = getURLTitleCleaned(link.getPluginPatternMatcher());
        }
        if (titleByURL != null) {
            titleByURL = titleByURL.replace("-", " ").trim();
            link.setFinalFileName(titleByURL + extDefault);
        }
        this.dllink = this.getdllink();
        if (!StringUtils.isEmpty(dllink) && !isDownload) {
            this.basicLinkCheck(br, br.createHeadRequest(this.dllink), link, titleByURL, extDefault);
        }
        return AvailableStatus.TRUE;
    }

    public static boolean isOffline(final Browser br) {
        if (br.getHttpConnection().getResponseCode() == 404) {
            return true;
        } else if (br.containsHTML("(?i)>\\s*404 Error Page") || br.containsHTML("video_removed_dmca\\.jpg\"|error\">We're sorry")) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception {
        requestFileInformation(link, true);
        if (StringUtils.isEmpty(dllink)) {
            if (br.containsHTML("class=\"sponsor\"") && br.containsHTML(">\\s*VISIT OFFICIAL SITE")) {
                /* 2026-06-10: e.g. fapbox.com, freeviewmovies.com, nakedtube.com */
                throw new AccountRequiredException("Sponsored content or unavailable video");
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = new jd.plugins.BrowserAdapter().openDownload(br, link, dllink, true, 0);
        if (!this.looksLikeDownloadableContent(dl.getConnection())) {
            br.followConnection(true);
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    private String getdllink() throws Exception {
        /*
         * 2026-08-25: The site switched to the KVS (Kernel Video Sharing) player. Download URLs are now stored in the player flashvars as
         * obfuscated "function/0/...get_file/..." entries and need to be decrypted via the "license_code" (logic ported from
         * KernelVideoSharingComV2).
         */
        final String kvsDllink = getKVSDownloadURL();
        if (!StringUtils.isEmpty(kvsDllink)) {
            return kvsDllink;
        }
        /* Legacy fallbacks for older page-/player versions. */
        String directurl = br.getRegex("<source src=\"(https?://[^<>\"]+)[^<>]+type='video/mp4'").getMatch(0);
        if (!StringUtils.isEmpty(directurl)) {
            return directurl;
        }
        directurl = br.getRegex("var desktopFile\\s*=\\s*'(https?://[^<>\"\\']+)").getMatch(0);
        if (!StringUtils.isEmpty(directurl)) {
            return directurl;
        }
        /* 2026-08-25: Not working anymore (url leads to plaintext error page). */
        // directurl = br.getRegex("\"contentUrl\"\\s*:\\s*\"(http[^\"]+)").getMatch(0);
        // if (!StringUtils.isEmpty(directurl)) {
        // return directurl;
        // }
        final Regex videoMETA = br.getRegex("(VideoFile|VideoMeta)_(\\d+)");
        final String type = videoMETA.getMatch(0);
        final String id = videoMETA.getMatch(1);
        final String cb = br.getRegex("\\?cb=(\\d+)\\'").getMatch(0);
        if (type == null || id == null || cb == null) {
            return null;
        }
        final String postData = "cacheBuster=" + System.currentTimeMillis() + "&jsonRequest=%7B%22path%22%3A%22" + type + "%5F" + id + "%22%2C%22cb%22%3A%22" + cb + "%22%2C%22loaderUrl%22%3A%22http%3A%2F%2Fcdn1%2Estatic%2Eatlasfiles%2Ecom%2Fplayer%2Fmemberplayer%2Eswf%3Fcb%3D" + cb + "%22%2C%22returnType%22%3A%22json%22%2C%22file%22%3A%22" + type + "%5F" + id + "%22%2C%22htmlHostDomain%22%3A%22www%2Estileproject%2Ecom%22%2C%22height%22%3A%22508%22%2C%22appdataurl%22%3A%22http%3A%2F%2Fwww%2Estileproject%2Ecom%2Fgetcdnurl%2F%22%2C%22playerOnly%22%3A%22true%22%2C%22request%22%3A%22getAllData%22%2C%22width%22%3A%22640%22%7D";
        br.postPage("/getcdnurl/", postData);
        return br.getRegex("\"file\": \"(http://[^<>\"]*?)\"").getMatch(0);
    }

    /**
     * Extracts the best available download URL from the KVS player flashvars, decrypting obfuscated "function/0/..." URLs when needed.
     * </br>
     * Ported from KernelVideoSharingComV2.
     */
    private String getKVSDownloadURL() {
        final String licenseCode = br.getRegex("license_code\\s*:\\s*'(.+?)'").getMatch(0);
        final TreeMap<Integer, String> qualityMap = new TreeMap<Integer, String>();
        /* Find qualities via their "*_text" labels, e.g. video_url_text : '480p', video_alt_url_text : '720p'. */
        final String[][] videoInfos = br.getRegex("([a-z0-9_]+_text)\\s*:\\s*'(\\d+)p'").getMatches();
        for (final String[] vidInfo : videoInfos) {
            final String varNameText = vidInfo[0];
            final int videoQuality = Integer.parseInt(vidInfo[1]);
            final String varNameVideoURL = varNameText.replace("_text", "");
            final String videoURL = br.getRegex(varNameVideoURL + "\\s*:\\s*'((?:http|/|function/0/)[^<>\"']*?)'").getMatch(0);
            final String dllinkTmp = decryptKVSURL(videoURL, licenseCode);
            if (dllinkTmp != null) {
                qualityMap.put(videoQuality, dllinkTmp);
            }
        }
        if (!qualityMap.isEmpty()) {
            /* Return highest quality available. */
            return qualityMap.get(qualityMap.lastKey());
        }
        /* Fallback: grab any obfuscated get_file URL and use the first one that decrypts successfully. */
        final String[] cryptedURLs = br.getRegex("(function/0/https?://[A-Za-z0-9\\.\\-/]+/get_file/[^<>\"']*?)(?:\\&amp|'|\")").getColumn(0);
        if (cryptedURLs != null) {
            for (final String cryptedURL : cryptedURLs) {
                final String dllinkTmp = decryptKVSURL(cryptedURL, licenseCode);
                if (dllinkTmp != null) {
                    return dllinkTmp;
                }
            }
        }
        return null;
    }

    /** Decrypts an obfuscated "function/0/..." video URL; returns plain URLs unchanged. */
    private String decryptKVSURL(final String videoUrl, final String licenseCode) {
        if (videoUrl == null) {
            return null;
        } else if (videoUrl.startsWith("function")) {
            if (licenseCode == null) {
                return null;
            }
            return decryptHash(videoUrl, licenseCode, "16");
        } else {
            /* Already a plain URL */
            return videoUrl;
        }
    }

    private static String decryptHash(final String videoUrl, final String licenseCode, final String hashRange) {
        String result = null;
        final List<String> videoUrlPart = new ArrayList<String>();
        Collections.addAll(videoUrlPart, videoUrl.split("/"));
        // hash
        String hash = videoUrlPart.get(7).substring(0, 2 * Integer.parseInt(hashRange));
        final String nonConvertHash = videoUrlPart.get(7).substring(2 * Integer.parseInt(hashRange));
        final String seed = calcSeed(licenseCode, hashRange);
        final String[] seedArray = new String[seed.length()];
        for (int i = 0; i < seed.length(); i++) {
            seedArray[i] = seed.substring(i, i + 1);
        }
        if (seed != null && hash != null) {
            for (int k = hash.length() - 1; k >= 0; k--) {
                final String[] hashArray = new String[hash.length()];
                for (int i = 0; i < hash.length(); i++) {
                    hashArray[i] = hash.substring(i, i + 1);
                }
                int l = k;
                for (int m = k; m < seedArray.length; m++) {
                    l += Integer.parseInt(seedArray[m]);
                }
                for (; l >= hashArray.length;) {
                    l -= hashArray.length;
                }
                final StringBuffer n = new StringBuffer();
                for (int o = 0; o < hashArray.length; o++) {
                    n.append(o == k ? hashArray[l] : o == l ? hashArray[k] : hashArray[o]);
                }
                hash = n.toString();
            }
            videoUrlPart.set(7, hash + nonConvertHash);
            for (final String string : videoUrlPart.subList(2, videoUrlPart.size())) {
                if (result == null) {
                    result = string;
                } else {
                    result = result + "/" + string;
                }
            }
            /* 2020-12-10: E.g. porndr.com */
            if (videoUrl.endsWith("/") && !result.endsWith("/")) {
                result += "/";
            }
        }
        return result;
    }

    private static String calcSeed(final String licenseCode, final String hashRange) {
        final StringBuffer fb = new StringBuffer();
        final String[] licenseCodeArray = new String[licenseCode.length()];
        for (int i = 0; i < licenseCode.length(); i++) {
            licenseCodeArray[i] = licenseCode.substring(i, i + 1);
        }
        for (final String c : licenseCodeArray) {
            if (c.equals("$")) {
                continue;
            }
            final int v = Integer.parseInt(c);
            fb.append(v != 0 ? c : "1");
        }
        final String f = fb.toString();
        final int j = f.length() / 2;
        final int k = Integer.parseInt(f.substring(0, j + 1));
        final int l = Integer.parseInt(f.substring(j));
        int g = l - k;
        g = Math.abs(g);
        int fi = g;
        g = k - l;
        g = Math.abs(g);
        fi += g;
        fi *= 2;
        final String s = String.valueOf(fi);
        final String[] fArray = new String[s.length()];
        for (int i = 0; i < s.length(); i++) {
            fArray[i] = s.substring(i, i + 1);
        }
        final int i = Integer.parseInt(hashRange) / 2 + 2;
        final StringBuffer m = new StringBuffer();
        for (int g2 = 0; g2 < j + 1; g2++) {
            for (int h = 1; h <= 4; h++) {
                int n = Integer.parseInt(licenseCodeArray[g2 + h]) + Integer.parseInt(fArray[g2]);
                if (n >= i) {
                    n -= i;
                }
                m.append(String.valueOf(n));
            }
        }
        return m.toString();
    }
}