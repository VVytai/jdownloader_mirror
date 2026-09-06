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

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.appwork.storage.TypeRef;
import org.appwork.utils.StringUtils;
import org.appwork.utils.encoding.Base64;
import org.appwork.utils.encoding.URLEncode;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision: 53308 $", interfaceVersion = 3, names = {}, urls = {})
public class VidbasicTop extends PluginForDecrypt {
    public VidbasicTop(PluginWrapper wrapper) {
        super(wrapper);
    }

    /* AES-256-CBC key and IV used by the /3rdplayer.html player JS to obfuscate the video- and subtitle URLs. */
    private static final String CRYPTO_KEY = "94588293375053432799222445521289";
    private static final String CRYPTO_IV  = "5259228356829423";

    @Override
    public Browser createNewBrowserInstance() {
        final Browser br = super.createNewBrowserInstance();
        br.setFollowRedirects(true);
        return br;
    }

    private static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForDecrypt, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "vidbasic.top" });
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

    public static String[] buildAnnotationUrls(final List<String[]> pluginDomains) {
        final List<String> ret = new ArrayList<String>();
        for (final String[] domains : pluginDomains) {
            ret.add("https?://(?:www\\.)?" + buildHostsPatternPart(domains) + "/embed/([a-zA-Z0-9]{8,})");
        }
        return ret.toArray(new String[0]);
    }

    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
        final String contenturl = param.getCryptedUrl();
        final String content_id = new Regex(contenturl, this.getSupportedLinks()).getMatch(0);
        br.getPage("https://" + getHost() + "/embed/" + content_id + "?json");
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (br.getHttpConnection().getRequest().getHtmlCode().equals("[]")) {
            /* Broken or offline stream */
            /* Try way without json */
            logger.info("json handling failed, trying non-json path");
            br.getPage(contenturl);
            final String selfembed_url = br.getRegex("id=\"embedvideo\" src=\"([^\"]+)").getMatch(0);
            if (selfembed_url == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            /*
             * The player embed contains the video- and subtitle URLs AES encrypted inside the "key" and "sub" parameters of the
             * /3rdplayer.html iframe. The player JS decrypts them via AES-256-CBC with a fixed key/IV.
             */
            final String encryptedVideo = new Regex(selfembed_url, "[?&](?:amp;)?key=([^&\"]+)").getMatch(0);
            if (encryptedVideo == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            final String hls_master = decryptPlayerValue(encryptedVideo);
            if (!StringUtils.startsWithCaseInsensitive(hls_master, "http")) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            ret.add(createDownloadlink(hls_master));
        } else {
            /* Map with key value where key is the name of a filehost and value to URL to the mirror. */
            final Map<String, Object> entries = restoreFromString(br.getRequest().getHtmlCode(), TypeRef.MAP);
            if (entries.isEmpty()) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            for (final Object obj : entries.values()) {
                final String url = obj.toString();
                if (!StringUtils.startsWithCaseInsensitive(url, "http")) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                ret.add(createDownloadlink(url));
            }
        }
        return ret;
    }

    /**
     * Java equivalent of the AES decryption performed by the /3rdplayer.html player JS. </br>
     * Input is the URL-encoded, base64 encoded ciphertext ("key" or "sub" iframe parameter), output is the plaintext URL.
     */
    private String decryptPlayerValue(final String urlEncodedBase64) throws Exception {
        final String base64 = URLEncode.decodeURIComponent(urlEncodedBase64);
        final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(CRYPTO_KEY.getBytes("UTF-8"), "AES"), new IvParameterSpec(CRYPTO_IV.getBytes("UTF-8")));
        return new String(cipher.doFinal(Base64.decode(base64)), "UTF-8");
    }

    @Override
    public boolean hasCaptcha(CryptedLink link, Account acc) {
        return false;
    }
}
