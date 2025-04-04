//    jDownloader - Downloadmanager
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

import org.appwork.utils.formatter.SizeFormatter;
import org.jdownloader.plugins.controller.LazyPlugin;

import jd.PluginWrapper;
import jd.nutils.encoding.Encoding;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision: 49171 $", interfaceVersion = 2, names = { "primemusic.ru" }, urls = { "https?://(?:www\\.)?(primemusic\\.ru|prime-music\\.net|primemusic\\.cc|primemusic\\.me|freshmusic\\.club|newhit\\.me|(?:[a-z0-9]+\\.)?new-hits\\.ru)/Media\\-page\\-\\d+\\.html" })
public class PrimeMusicRu extends PluginForHost {
    @Override
    public String[] siteSupportedNames() {
        return new String[] { "primemusic.ru", "prime-music.net", "primemusic.cc", "primemusic.me", "freshmusic.club", "newhit.me" };
    }

    public PrimeMusicRu(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public LazyPlugin.FEATURE[] getFeatures() {
        return new LazyPlugin.FEATURE[] { LazyPlugin.FEATURE.AUDIO_STREAMING };
    }

    @Override
    public String getAGBLink() {
        return "https://primemusic.me";
    }

    /** 2019-01-18: This website GEO-blocks german IPs */
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.setAllowedResponseCodes(new int[] { 451 });
        String url = link.getPluginPatternMatcher();
        if (!url.matches("(?i)^https?://www\\..+")) {
            /* 2024-06-19: www. is required! */
            url = url.replaceFirst("(?i)https?://", "https://www.");
        }
        br.getPage(url);
        final boolean offlineForLegalReasons = br.getHttpConnection().getResponseCode() == 451;
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (offlineForLegalReasons) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (br.containsHTML("<h1 class=\"radio_title\">Композиция не найдена</h1>|>Композиция удалена")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (br.getURL().contains("/index.php")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String finalfilename = br.getRegex("<h2[^<>]*>Слушать\\s*([^<>\"]*?)\\s*(\\.mp3|онлайн)</h2>").getMatch(0);
        if (finalfilename == null) {
            finalfilename = br.getRegex("<div class=\"caption\">[\t\n\r ]+<h\\d+[^<>]*>([^<>\"]*?)\\s*(скачать песню)?</h\\d+>").getMatch(0);
        }
        String filesize = br.getRegex("<b>Размер:?</b>:?([^<>\"]*?)</span>").getMatch(0);
        if (finalfilename != null) {
            link.setFinalFileName(Encoding.htmlDecode(finalfilename.trim()) + ".mp3");
        }
        if (filesize != null) {
            link.setDownloadSize(SizeFormatter.getSize(filesize.replace(",", ".")));
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception, PluginException {
        requestFileInformation(link);
        br.setFollowRedirects(false);
        br.getPage(br.getURL().replaceFirst("(?i)/Media-page-", "/Media-download-"));
        if (br.getHttpConnection().getResponseCode() == 500) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Download page is broken");
        }
        String finallink = br.getRedirectLocation();
        if (finallink == null) {
            br.getRegex("<a class=\"download\" href=(https?://[^<>\"]*?\\.mp3)\"").getMatch(0);
            if (finallink == null) {
                finallink = br.getRegex("class=\"download_link\" href=\"(https?://[^<>\"]*?)\"").getMatch(0);
                if (finallink == null) {
                    finallink = br.getRegex("\"(https?://[a-z0-9]+\\.(primemusic\\.ru|prime\\-music\\.net|primemusic\\.cc|primemusic\\.me|freshmusic\\.club|newhit\\.me)/dl\\d+/[^<>\"]*?)\"").getMatch(0);
                }
            }
        }
        if (finallink == null) {
            if (br.getHttpConnection().getResponseCode() == 500) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Download page is broken", 3 * 60 * 60 * 1000);
            } else {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, finallink, true, 0);
        if (!this.looksLikeDownloadableContent(dl.getConnection())) {
            br.followConnection(true);
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}