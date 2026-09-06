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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.appwork.utils.StringUtils;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginDependencies;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.hoster.ChipDe;
import jd.plugins.hoster.DirectHTTP;

@DecrypterPlugin(revision = "$Revision: 53325 $", interfaceVersion = 3, names = {}, urls = {})
@PluginDependencies(dependencies = { ChipDe.class })
public class ChipDeDecrypter extends PluginForDecrypt {
    public ChipDeDecrypter(PluginWrapper wrapper) {
        super(wrapper);
    }

    public static List<String[]> getPluginDomains() {
        return ChipDe.getPluginDomains();
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

    /** The only supported url-type: image galleries e.g. /bildergalerie/<title>_<id>.html */
    private static final Pattern PATTERN_PICTURES = Pattern.compile("/bildergalerie/([^/]+)_(\\d+)\\.html", Pattern.CASE_INSENSITIVE);

    public static String[] buildAnnotationUrls(final List<String[]> pluginDomains) {
        final List<String> ret = new ArrayList<String>();
        for (final String[] domains : pluginDomains) {
            ret.add("https?://(?:[a-z0-9\\-]+\\.)?" + buildHostsPatternPart(domains) + PATTERN_PICTURES.pattern());
        }
        return ret.toArray(new String[0]);
    }

    @SuppressWarnings("unchecked")
    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
        final String contenturl = param.getCryptedUrl();
        final Regex urlinfo = new Regex(contenturl, PATTERN_PICTURES);
        final String titleFromURL = urlinfo.getMatch(0);
        final String galleryID = urlinfo.getMatch(1);
        br.setFollowRedirects(true);
        URLConnectionAdapter con = null;
        try {
            con = br.openGetConnection(contenturl);
            if (con.getResponseCode() == 404 || con.getResponseCode() == 410) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            br.followConnection();
        } finally {
            try {
                con.disconnect();
            } catch (final Throwable e) {
            }
        }
        /*
         * The gallery images are exposed via a schema.org "NewsArticle" JSON-LD block. Its "image" array lists all pictures (with
         * captions). A single picture can appear multiple times with different crop-transforms in the URL - we only want the first (=
         * highest resolution) variant per picture thus we de-duplicate by the base url (part before the '?').
         */
        final String[] ldBlocks = br.getRegex("<script[^>]*type=\"application/ld\\+json\"[^>]*>\\s*(\\{.*?\\})\\s*</script>").getColumn(0);
        if (ldBlocks == null || ldBlocks.length == 0) {
            if (br.getHttpConnection().getResponseCode() == 200) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            } else {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        String title = null;
        List<Object> images = null;
        for (final String ldBlock : ldBlocks) {
            final Object jsonO = JavaScriptEngineFactory.jsonToJavaObject(ldBlock);
            if (!(jsonO instanceof Map)) {
                continue;
            }
            final Map<String, Object> entries = (Map<String, Object>) jsonO;
            if (!"NewsArticle".equals(entries.get("@type"))) {
                continue;
            }
            title = (String) entries.get("headline");
            final Object imageO = entries.get("image");
            if (imageO instanceof List) {
                images = (List<Object>) imageO;
            } else if (imageO instanceof Map) {
                /* Single picture galleries */
                images = new ArrayList<Object>();
                images.add(imageO);
            }
            break;
        }
        if (images == null || images.isEmpty()) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (!StringUtils.isEmpty(title)) {
            title = Encoding.htmlDecode(title).trim();
            title = title.replaceFirst("Bildergalerie: ", "");
        } else {
            /* Fallback */
            title = titleFromURL;
        }
        final DecimalFormat df = new DecimalFormat("000");
        final Set<String> dupes = new HashSet<String>();
        int counter = 1;
        for (final Object imageO : images) {
            final Map<String, Object> imageEntry = (Map<String, Object>) imageO;
            final String url = (String) imageEntry.get("url");
            if (StringUtils.isEmpty(url)) {
                continue;
            }
            final String urlBase = url.contains("?") ? url.substring(0, url.indexOf("?")) : url;
            if (!dupes.add(urlBase)) {
                /* Skip crop-variants of a picture we already added */
                continue;
            }
            final DownloadLink link = createDownloadlink(DirectHTTP.createURLForThisPlugin(url));
            String ext = new Regex(urlBase, "(\\.[A-Za-z0-9]+)$").getMatch(0);
            if (ext == null) {
                ext = ".jpg";
            }
            if (!StringUtils.isEmpty(title)) {
                link.setFinalFileName(Encoding.htmlDecode(title).trim() + "_" + df.format(counter) + ext);
            }
            final String caption = (String) imageEntry.get("caption"); // optional
            if (!StringUtils.isEmpty(caption)) {
                link.setComment(Encoding.htmlDecode(caption).trim());
            }
            link.setAvailable(true);
            ret.add(link);
            counter++;
        }
        final FilePackage fp = FilePackage.getInstance();
        if (!StringUtils.isEmpty(title)) {
            fp.setName(title);
        } else {
            /* Fallback */
            fp.setName(galleryID);
        }
        fp.setPackageKey("chipde://gallery/" + galleryID);
        fp.addLinks(ret);
        return ret;
    }

    @Override
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }
}
