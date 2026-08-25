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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.utils.StringUtils;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginDependencies;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.hoster.SendgbCom;

@DecrypterPlugin(revision = "$Revision: 53205 $", interfaceVersion = 3, names = {}, urls = {})
@PluginDependencies(dependencies = { SendgbCom.class })
public class SendgbComFolder extends PluginForDecrypt {
    public SendgbComFolder(PluginWrapper wrapper) {
        super(wrapper);
    }

    public static List<String[]> getPluginDomains() {
        return SendgbCom.getPluginDomains();
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
    public Browser createNewBrowserInstance() {
        final Browser br = super.createNewBrowserInstance();
        br.setFollowRedirects(true);
        return br;
    }

    private String getFID(final String url) {
        String fid = new Regex(url, "(?i)https?://[^/]+" + PATTERN_DOWNLOAD.pattern()).getMatch(0);
        if (fid == null) {
            fid = new Regex(url, "(?i)https?://[^/]+" + PATTERN_OLD.pattern()).getMatch(0);
        }
        return fid;
    }

    @SuppressWarnings("unchecked")
    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
        final String contenturl = param.getCryptedUrl();
        final String fid = getFID(contenturl);
        if (fid == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final String apibase = "https://api." + getHost() + "/api/download/" + fid;
        br.getPage(apibase);
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        Map<String, Object> response = restoreFromString(br.getRequest().getHtmlCode(), TypeRef.MAP);
        if (!Boolean.TRUE.equals(response.get("ok"))) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String password = null;
        if (Boolean.TRUE.equals(response.get("is_protected")) && !Boolean.TRUE.equals(response.get("is_unlocked"))) {
            /* Password protected folder -> unlock it before we can access the file list. */
            password = param.getDecrypterPassword();
            boolean pw_success = false;
            for (int i = 0; i <= 2; i++) {
                if (password == null) {
                    password = getUserInput("Password?", param);
                }
                final Map<String, Object> pwPost = new HashMap<String, Object>();
                pwPost.put("password", password);
                final Browser brc = br.cloneBrowser();
                brc.getHeaders().put("Content-Type", "application/json");
                brc.postPageRaw(apibase + "/verify-password", JSonStorage.serializeToJson(pwPost));
                final Map<String, Object> verify = restoreFromString(brc.getRequest().getHtmlCode(), TypeRef.MAP);
                if (Boolean.TRUE.equals(verify.get("ok"))) {
                    pw_success = true;
                    break;
                }
                logger.info("Wrong password entered: " + password);
                password = null;
            }
            if (!pw_success) {
                throw new DecrypterException(DecrypterException.PASSWORD);
            }
            /* Re-fetch the folder json with the correct password to obtain the now unlocked file list. */
            br.getPage(apibase + "?password=" + Encoding.urlEncode(password));
            response = restoreFromString(br.getRequest().getHtmlCode(), TypeRef.MAP);
            if (!Boolean.TRUE.equals(response.get("ok"))) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
        }
        final List<Map<String, Object>> files = (List<Map<String, Object>>) response.get("files");
        if (files == null || files.isEmpty()) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final boolean transferUnlocked = Boolean.TRUE.equals(response.get("is_unlocked"));
        final SendgbCom hosterplugin = (SendgbCom) this.getNewPluginForHostInstance(this.getHost());
        /* All files of a transfer share the same content-url; they are distinguished via the selection_id property. */
        final String contentURL = "https://www." + getHost() + "/download/" + fid;
        for (final Map<String, Object> file : files) {
            final String selectionID = (String) file.get("selection_id");
            final DownloadLink link = new DownloadLink(hosterplugin, this.getHost(), contentURL);
            final Object name = file.get("name");
            if (name != null) {
                link.setName(name.toString());
            }
            final Object size = file.get("size");
            if (size != null) {
                link.setDownloadSize(((Number) size).longValue());
            }
            if (selectionID != null) {
                link.setProperty(SendgbCom.PROPERTY_SELECTION_ID, selectionID);
            }
            if (password != null) {
                /* Store the folder password so the host plugin can reuse it for the API/presign calls. */
                link.setDownloadPassword(password);
            }
            /* Per-file lock state, falling back to the transfer-wide flag. Locked files are treated as offline. */
            final boolean unlocked = file.containsKey("is_unlocked") ? Boolean.TRUE.equals(file.get("is_unlocked")) : transferUnlocked;
            link.setAvailable(unlocked);
            ret.add(link);
        }
        final String message = (String) response.get("message");
        final Object transferName = response.get("transfer_name");
        String packageName = transferName != null ? transferName.toString() : null;
        if (packageName == null || packageName.length() == 0) {
            /* Fallback */
            packageName = fid;
        }
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(packageName);
        fp.setPackageKey("sendgb://" + fid);
        if (!StringUtils.isEmpty(message)) {
            fp.setComment(message);
        }
        fp.addLinks(ret);
        return ret;
    }
}
