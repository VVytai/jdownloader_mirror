package org.jdownloader.downloader.hls;

import java.io.IOException;
import java.util.Arrays;

import org.appwork.net.protocol.http.HTTPConstants;
import org.appwork.utils.ByteArrayUtils;
import org.appwork.utils.StringUtils;

import jd.http.Request;
import jd.http.URLConnectionAdapter;
import jd.plugins.Plugin;
import jd.plugins.PluginBrowser;

public class HLSPluginBrowser<T extends Plugin> extends PluginBrowser<T> {
    public HLSPluginBrowser(final T plugin) {
        super(plugin);
    }

    @Override
    protected Request onRequestRead(Request request) throws IOException {
        final Request ret = super.onRequestRead(request);
        final URLConnectionAdapter con = ret.getHttpConnection();
        if (con != null && ret.getResponseBytes() != null && StringUtils.startsWithCaseInsensitive(con.getContentType(), "image")) {
            if (ByteArrayUtils.startsWith(ret.getResponseBytes(), "#EXTM3U".getBytes("UTF-8"))) {
                final String extm3u8 = new String(ret.getResponseBytes(), "UTF-8");
                ret.setHtmlCode(extm3u8);
                ret.getHttpConnection().getHeaderFields().put("X-AUTO-CORRECTED-" + HTTPConstants.HEADER_REQUEST_CONTENT_TYPE, Arrays.asList(new String[] { con.getContentType() }));
                ret.getHttpConnection().getHeaderFields().put(HTTPConstants.HEADER_REQUEST_CONTENT_TYPE, Arrays.asList(new String[] { "application/vnd.apple.mpegurl" }));
            }
        }
        return ret;
    }
}
