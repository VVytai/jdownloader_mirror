package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DecrypterRetryException;
import jd.plugins.DecrypterRetryException.RetryReason;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginDependencies;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.hoster.Rule34videoCom;

@DecrypterPlugin(revision = "$Revision: 53247 $", interfaceVersion = 3, names = {}, urls = {})
@PluginDependencies(dependencies = { Rule34videoCom.class })
public class Rule34VideoPlaylist extends PluginForDecrypt {
    public Rule34VideoPlaylist(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public Browser createNewBrowserInstance() {
        final Browser br = super.createNewBrowserInstance();
        br.setFollowRedirects(true);
        return br;
    }

    public static List<String[]> getPluginDomains() {
        return Rule34videoCom.getPluginDomains();
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

    private static final Pattern PATTERN_PLAYLIST = Pattern.compile("/playlists/(\\d+)/([^/]+)/?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_MODEL    = Pattern.compile("/models/([^/]+)/?", Pattern.CASE_INSENSITIVE);

    public static String[] buildAnnotationUrls(final List<String[]> pluginDomains) {
        final List<String> ret = new ArrayList<String>();
        for (final String[] domains : pluginDomains) {
            ret.add("https?://(?:[a-z0-9]+\\.)?" + buildHostsPatternPart(domains) + "/(" + PATTERN_PLAYLIST.pattern().substring(1) + "|" + PATTERN_MODEL.pattern().substring(1) + ")");
        }
        return ret.toArray(new String[0]);
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(CryptedLink parameter, ProgressController progress) throws Exception {
        final String contenturl = parameter.getCryptedUrl();
        if (new Regex(contenturl, PATTERN_PLAYLIST).patternFind()) {
            return crawlPlaylist(contenturl);
        } else if (new Regex(contenturl, PATTERN_MODEL).patternFind()) {
            return crawlModel(contenturl);
        } else {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
    }

    private ArrayList<DownloadLink> crawlPlaylist(final String contenturl) throws Exception {
        final ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
        final String playlist_id = new Regex(contenturl, PATTERN_PLAYLIST).getMatch(0);
        br.getPage(contenturl);
        if (br.getRequest().getHttpConnection().getResponseCode() == 404) {
            throw new DecrypterRetryException(RetryReason.FILE_NOT_FOUND);
        }
        final String title = br.getRegex("class\\s*=\\s*\"title_video\"[^>]*>\\s*(.*?)\\s*</h1>").getMatch(0);
        final FilePackage fp = FilePackage.getInstance();
        if (title != null) {
            fp.setName(Encoding.htmlDecode(title));
        }
        fp.setPackageKey(getHost() + "://playlist/" + playlist_id);
        final HashSet<String> dupes = new HashSet<String>();
        int page = 1;
        int nextPage = 2;
        do {
            final String playListItems[] = br.getRegex("data-playlist-item\\s*=\\s*\"(.*?)\"").getColumn(0);
            if (playListItems == null || playListItems.length == 0) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            int numberofNewItems = 0;
            for (final String playListItem : playListItems) {
                if (!dupes.add(playListItem)) {
                    continue;
                }
                numberofNewItems++;
                final DownloadLink item = createDownloadlink(playListItem);
                fp.add(item);
                ret.add(item);
                distribute(item);
            }
            logger.info("Crawled page " + page + ", found Elements so far: " + ret.size());
            if (numberofNewItems == 0) {
                /* Fail-safe: no new items found -> we reached the end. */
                logger.info("Stopping because: No new items found on current page");
                break;
            }
            if (!br.containsHTML("from:0?" + nextPage)) {
                logger.info("Stopping because: Reached last page");
                break;
            }
            br.getPage("?mode=async&function=get_block&block_id=playlist_view_playlist_view&sort_by=added2fav_date&from=" + nextPage + "&_=" + System.currentTimeMillis());
            if (br.getRequest().getHttpConnection().getResponseCode() == 404) {
                logger.info("Stopping because: Got http response 404");
                break;
            }
            page++;
            nextPage++;
        } while (!isAbort());
        return ret;
    }

    private ArrayList<DownloadLink> crawlModel(final String contenturl) throws Exception {
        final ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
        final String modelName = new Regex(contenturl, PATTERN_MODEL).getMatch(0);
        br.getPage(contenturl);
        if (br.getRequest().getHttpConnection().getResponseCode() == 404) {
            throw new DecrypterRetryException(RetryReason.FILE_NOT_FOUND);
        }
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(Encoding.htmlDecode(modelName));
        fp.setPackageKey(getHost() + "://model/" + modelName);
        final HashSet<String> dupes = new HashSet<String>();
        int page = 1;
        int from = 1;
        do {
            final String[] videoURLs = br.getRegex("href\\s*=\\s*\"(https?://[^\"]+/video/\\d+/[^\"]+)\"").getColumn(0);
            if (videoURLs == null || videoURLs.length == 0) {
                if (from == 1) {
                    /* First page must contain at least one item. */
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                logger.info("Stopping because: No items found on current page");
                break;
            }
            int numberofNewItems = 0;
            for (final String videoURL : videoURLs) {
                if (!dupes.add(videoURL)) {
                    continue;
                }
                numberofNewItems++;
                final DownloadLink item = createDownloadlink(videoURL);
                fp.add(item);
                ret.add(item);
                distribute(item);
            }
            logger.info("Crawled page " + page + ", found Elements so far: " + ret.size());
            if (numberofNewItems == 0) {
                /* Fail-safe: no new items found -> we reached the end. */
                logger.info("Stopping because: No new items found on current page");
                break;
            }
            page++;
            from++;
            br.getPage("?mode=async&function=get_block&block_id=custom_list_videos_common_videos&sort_by=post_date&from=" + from + "&_=" + System.currentTimeMillis());
            if (br.getRequest().getHttpConnection().getResponseCode() == 404) {
                logger.info("Stopping because: Got http response 404");
                break;
            }
        } while (!isAbort());
        return ret;
    }
}
