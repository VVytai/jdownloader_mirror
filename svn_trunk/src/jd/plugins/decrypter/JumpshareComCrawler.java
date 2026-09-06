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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.appwork.storage.TypeRef;
import org.appwork.utils.StringUtils;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DecrypterRetryException;
import jd.plugins.DecrypterRetryException.RetryReason;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision: 53316 $", interfaceVersion = 3, names = {}, urls = {})
public class JumpshareComCrawler extends PluginForDecrypt {
    public JumpshareComCrawler(PluginWrapper wrapper) {
        super(wrapper);
    }

    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForDecrypt, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "jumpshare.com" });
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

    /* Path patterns with capturing groups: group 1 = root bucket id, group 2 (subfolder only) = subfolder id, file group 1 = file id. */
    private static final Pattern PATTERN_FOLDER_PATH = Pattern.compile("/b/([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_FOLDER_NEW  = Pattern.compile("/folder/(([A-Za-z0-9]+)(/([A-Za-z0-9]+))?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_FILE_PATH   = Pattern.compile("/v/([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE);

    public static String[] buildAnnotationUrls(final List<String[]> pluginDomains) {
        final List<String> ret = new ArrayList<String>();
        for (final String[] domains : pluginDomains) {
            /* jmp.sh short-links (resolved via redirect) plus jumpshare.com folder- and subfolder-links. */
            ret.add("https?://(?:www\\.)?(?:jmp\\.sh/(?!v/)[A-Za-z0-9]+|" + buildHostsPatternPart(domains) + "(?:" + PATTERN_FOLDER_PATH.pattern() + "|" + PATTERN_FOLDER_NEW.pattern() + "))");
        }
        return ret.toArray(new String[0]);
    }

    /* Full-url patterns reuse the path patterns above so the paths are not duplicated. */
    private static final Pattern PATTERN_FOLDER = Pattern.compile("(?:" + PATTERN_FOLDER_PATH.pattern() + "|" + PATTERN_FOLDER_NEW.pattern() + ")", Pattern.CASE_INSENSITIVE);

    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
        final String contenturl = param.getCryptedUrl().replaceFirst("(?i)^http://", "https://");
        String path = param.getDownloadLink() != null ? param.getDownloadLink().getRelativeDownloadFolderPath() : null;
        if (new Regex(contenturl, PATTERN_FOLDER).patternFind()) {
            this.br.setFollowRedirects(true);
            br.getPage(contenturl);
            if (br.getHttpConnection().getResponseCode() == 404 || this.br.containsHTML("Folder Not Found|The folder you are looking for does not exist")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            final String folderpathFromURL = new Regex(contenturl, "([A-Za-z0-9]+)$").getMatch(0);
            /* The root bucket id is always group 1 of the folder- or subfolder-path pattern. */
            final String rootBucketID = getRootBucketID(br.getURL());
            final String folderUrlPath = getFolderURLPath(br.getURL());
            if (rootBucketID == null || folderUrlPath == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            final Form passwordForm = br.getFormbyProperty("id", "folder-unlock-form");
            if (passwordForm != null) {
                logger.warning("Password protected folders are not yet supported");
                throw new DecrypterRetryException(RetryReason.PASSWORD, "UNSUPPORTED_PASSWORD_PROTECTED_FOLDER_" + folderpathFromURL, "Password protected folders of this website aren't supported yet. Contact our support and ask for implementation!");
            }
            String thisFolderTitle = br.getRegex("property=\"og:title\" content=\"([^<>]+)\"").getMatch(0);
            if (thisFolderTitle == null) {
                thisFolderTitle = br.getRegex("id=\"bucket_name_header\"[^>]*data-gridname=\"([^\"]+)\"").getMatch(0);
            }
            if (thisFolderTitle != null) {
                thisFolderTitle = Encoding.htmlDecode(thisFolderTitle).trim();
                if (path != null) {
                    path += "/" + thisFolderTitle;
                } else {
                    path = thisFolderTitle;
                }
            }
            /*
             * The file/subfolder listing is paginated. Page 1 is part of the initial html while all following pages are loaded via ajax and
             * returned as JSON containing an "items" html snippet (same markup as page 1). We keep requesting pages until we get an
             * empty/short page.
             */
            br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            final HashSet<String> dupes = new HashSet<String>();
            String pageHTML = br.getRequest().getHtmlCode();
            final int itemsPerPage = 100;
            int page = 1;
            pagination: do {
                final int numberofNewItems = this.crawlFolderPage(ret, pageHTML, path, rootBucketID, folderUrlPath, dupes);
                logger.info("Crawled page " + page + " | New items on this page: " + numberofNewItems + " | Total items so far: " + ret.size());
                if (this.isAbort()) {
                    throw new InterruptedException();
                } else if (numberofNewItems == 0) {
                    /* No more items -> Stop */
                    logger.info("Stopping because current page didn't contain any new items");
                    break pagination;
                } else if (numberofNewItems < itemsPerPage) {
                    /* Current page contains less items than a full page -> This was the last page. */
                    logger.info("Stopping because current page contains less items than a full page: " + itemsPerPage);
                    break pagination;
                }
                page++;
                br.getPage(contenturl + "?page=" + page + "&_=" + System.currentTimeMillis());
                final Map<String, Object> pageJson = restoreFromString(br.getRequest().getHtmlCode(), TypeRef.MAP);
                final Object status = pageJson.get("status");
                if (!(status instanceof Number) || ((Number) status).intValue() != 1) {
                    logger.info("Stopping because of unexpected pagination status: " + status);
                    break pagination;
                }
                pageHTML = (String) pageJson.get("items");
                if (StringUtils.isEmpty(pageHTML)) {
                    logger.info("Stopping because current page didn't contain any items html");
                    break pagination;
                }
            } while (true);
            if (ret.isEmpty()) {
                if (br.containsHTML("class=\"content_fluid is-empty\"")) {
                    throw new DecrypterRetryException(RetryReason.EMPTY_FOLDER);
                } else {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
            }
            final FilePackage fp = FilePackage.getInstance();
            if (path != null) {
                fp.setName(path);
            } else {
                /* Fallback */
                fp.setName(folderpathFromURL);
            }
            fp.setPackageKey("jumpshare://folder/" + folderpathFromURL);
            fp.addLinks(ret);
        } else {
            /* Crawl short-link */
            this.br.setFollowRedirects(false);
            br.getPage(contenturl);
            if (br.getHttpConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            final String finallink = this.br.getRedirectLocation();
            if (finallink == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            /* Validate result */
            if (!new Regex(finallink, PATTERN_FOLDER).patternFind() && !new Regex(finallink, PATTERN_FILE_PATH).patternFind() && finallink.contains(this.getHost())) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            final DownloadLink singlefile = createDownloadlink(finallink);
            if (path != null) {
                singlefile.setRelativeDownloadFolderPath(path);
            }
            ret.add(singlefile);
        }
        return ret;
    }

    /** Returns the root bucket id (group 1 of the subfolder- or folder-path pattern) for a /b/ or /folder/ url. */
    private String getRootBucketID(final String url) {
        final String rootFromSubfolder = new Regex(url, PATTERN_FOLDER_NEW).getMatch(1);
        if (rootFromSubfolder != null) {
            return rootFromSubfolder;
        }
        return new Regex(url, PATTERN_FOLDER_PATH).getMatch(0);
    }

    private String getFolderURLPath(final String url) {
        final String rootFromSubfolder = new Regex(url, PATTERN_FOLDER_NEW).getMatch(0);
        if (rootFromSubfolder != null) {
            return rootFromSubfolder;
        }
        return new Regex(url, PATTERN_FOLDER_PATH).getMatch(0);
    }

    /**
     * Parses one page of a folder listing. Each entry is a &lt;li&gt; element (class "file" for files, "bucket-li" for subfolders) carrying
     * its metadata as data-* attributes. data-type "folder"/"bucket" marks a subfolder, everything else is a downloadable file. Returns the
     * number of new items that have been added to given list.
     */
    private int crawlFolderPage(final ArrayList<DownloadLink> ret, final String html, final String path, final String rootBucketID, final String folderUrlPath, final HashSet<String> dupes) throws Exception {
        final String[] items = new Regex(html, "<li[^>]*class=\"[^\"]*(?:file|bucket-li)[^\"]*\"[^>]*>").getColumn(-1);
        if (items == null || items.length == 0) {
            return 0;
        }
        int numberofNewItems = 0;
        for (final String item : items) {
            final String itemID = new Regex(item, "data-id=\"([A-Za-z0-9]+)\"").getMatch(0);
            if (itemID == null) {
                /* Skip unexpected/malformed entry. */
                continue;
            } else if (!dupes.add(itemID)) {
                /* Skip duplicate. */
                continue;
            }
            numberofNewItems++;
            final String itemType = new Regex(item, "data-type=\"([^\"]+)\"").getMatch(0);
            final DownloadLink link;
            if ("folder".equalsIgnoreCase(itemType) || "bucket".equalsIgnoreCase(itemType)) {
                /* Subfolder -> let this crawler pick it up again. Subfolders live at /folder/<rootBucketID>/<subfolderID>. */
                link = createDownloadlink(br.getURL("/folder/" + rootBucketID + "/" + itemID).toExternalForm());
            } else {
                final String url = br.getURL("/share/" + itemID).toExternalForm();
                /* Contenturl for the user to copy - let's use the same urls they use in browser. */
                final String url_file_view_with_folder = br.getURL("/share/" + itemID + "?b=" + folderUrlPath).toExternalForm();
                link = createDownloadlink(url);
                link.setContentUrl(url_file_view_with_folder);
                final String filename = new Regex(item, "data-gridname=\"([^\"]+)\"").getMatch(0);
                if (filename != null) {
                    link.setName(Encoding.htmlDecode(filename).trim());
                }
                final String filesize = new Regex(item, "data-size=\"(\\d+)\"").getMatch(0);
                if (filesize != null) {
                    if (item.contains("data-download-status=\"enabled\"")) {
                        link.setVerifiedFileSize(Long.parseLong(filesize));
                    } else {
                        /* We might not be able to download this item or at least not the original file -> Do not set verifiedFilesize. */
                        link.setDownloadSize(Long.parseLong(filesize));
                    }
                }
                link.setAvailable(true);
            }
            if (path != null) {
                link.setRelativeDownloadFolderPath(path);
            }
            ret.add(link);
            distribute(link);
        }
        return numberofNewItems;
    }
}
