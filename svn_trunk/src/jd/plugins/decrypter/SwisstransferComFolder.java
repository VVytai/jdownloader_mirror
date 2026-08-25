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
package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.appwork.utils.StringUtils;
import org.appwork.utils.Time;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.hoster.SwisstransferCom;

@DecrypterPlugin(revision = "$Revision: 53206 $", interfaceVersion = 3, names = {}, urls = {})
public class SwisstransferComFolder extends PluginForDecrypt {
    public SwisstransferComFolder(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public Browser createNewBrowserInstance() {
        final Browser br = super.createNewBrowserInstance();
        br.setFollowRedirects(true);
        br.setLoadLimit(Integer.MAX_VALUE);
        return br;
    }

    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForDecrypt, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "swisstransfer.com" });
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

    private static final Pattern PATTERN_DL = Pattern.compile("/dl?/([a-z0-9\\-]+)", Pattern.CASE_INSENSITIVE);

    public static String[] buildAnnotationUrls(final List<String[]> pluginDomains) {
        final List<String> ret = new ArrayList<String>();
        for (final String[] domains : pluginDomains) {
            ret.add("https?://(?:www\\.)?" + buildHostsPatternPart(domains) + PATTERN_DL.pattern());
        }
        return ret.toArray(new String[0]);
    }

    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
        final String contenturl = param.getCryptedUrl();
        final String linkUUID = new Regex(contenturl, PATTERN_DL).getMatch(0);
        if (linkUUID == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final SwisstransferCom hosterplugin = (SwisstransferCom) this.getNewPluginForHostInstance(this.getHost());
        String passCode = param.getDecrypterPassword();
        Map<String, Object> transfer = null;
        int pwcounter = 0;
        do {
            if (pwcounter > 0) {
                passCode = getUserInput("Password?", param);
            }
            /*
             * The website is an Inertia.js single page application: the transfer metadata is embedded as JSON inside the "/dl/..." HTML
             * page (script tag with data-page="app").
             */
            br.getPage("https://www." + this.getHost() + "/dl/" + linkUUID);
            Map<String, Object> page = SwisstransferCom.parseInertiaPage(br);
            final String component = (String) page.get("component");
            if (component != null && component.startsWith("errors/")) {
                /* E.g. "errors/transfer/not-found" */
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            Map<String, Object> props = (Map<String, Object>) page.get("props");
            transfer = props != null ? (Map<String, Object>) props.get("transfer") : null;
            if (transfer != null) {
                /* Success */
                break;
            }
            /* No transfer object present -> transfer is password protected */
            if (passCode != null) {
                page = hosterplugin.submitPassword(br, linkUUID, passCode, (String) page.get("version"));
                props = (Map<String, Object>) page.get("props");
                transfer = props != null ? (Map<String, Object>) props.get("transfer") : null;
                if (transfer != null) {
                    break;
                }
            }
            pwcounter++;
        } while (pwcounter <= 2);
        if (transfer == null) {
            throw new DecrypterException(DecrypterException.PASSWORD);
        }
        final List<Map<String, Object>> ressourcelist = (List<Map<String, Object>>) transfer.get("files");
        if (ressourcelist == null || ressourcelist.isEmpty()) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final String containerUUID = (String) transfer.get("id");
        final Number expiresAt = (Number) transfer.get("expires_at");
        boolean isExpired = false;
        if (expiresAt != null && expiresAt.longValue() > 0 && expiresAt.longValue() * 1000L < Time.timestamp()) {
            isExpired = true;
        }
        final FilePackage fp = FilePackage.getInstance();
        final String title = (String) transfer.get("title");
        final String message = (String) transfer.get("message");
        if (!StringUtils.isEmpty(title)) {
            fp.setName(title.trim());
        } else if (!StringUtils.isEmpty(message)) {
            fp.setName(message.trim());
        } else {
            /* Fallback */
            fp.setName(linkUUID);
        }
        fp.setPackageKey(this.getHost() + "/linkUUID/" + linkUUID);
        for (final Map<String, Object> file : ressourcelist) {
            final String filename = (String) file.get("path");
            final String fileid = (String) file.get("id");
            final Number filesize = (Number) file.get("size");
            final DownloadLink link = createDownloadlink("");
            link.setDefaultPlugin(hosterplugin);
            link.setHost(this.getHost());
            link.setProperty(SwisstransferCom.PROPERTY_FILE_ID, fileid);
            link.setProperty(SwisstransferCom.PROPERTY_LINK_UUID, linkUUID);
            link.setProperty(SwisstransferCom.PROPERTY_CONTAINER_UUID, containerUUID);
            if (filesize != null) {
                link.setVerifiedFileSize(filesize.longValue());
            }
            if (!StringUtils.isEmpty(filename)) {
                link.setFinalFileName(filename);
            }
            link.setContentUrl(contenturl);
            if (passCode != null) {
                link.setDownloadPassword(passCode);
            }
            if (isExpired) {
                link.setAvailable(false);
                link.setProperty(SwisstransferCom.PROPERTY_PERMANENTLY_OFFLINE, true);
            } else {
                link.setAvailable(true);
            }
            link._setFilePackage(fp);
            ret.add(link);
        }
        logger.info("Found file items: " + ressourcelist.size());
        return ret;
    }
}
