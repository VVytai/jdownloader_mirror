package jd.plugins;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.regex.Pattern;

import org.appwork.net.protocol.http.HTTPConstants;
import org.appwork.storage.TypeRef;
import org.appwork.utils.DebugMode;
import org.appwork.utils.Files;
import org.appwork.utils.net.HTTPHeader;
import org.appwork.utils.net.httpconnection.DNSResolver;
import org.appwork.utils.net.httpconnection.HTTPConnection.RequestMethod;
import org.appwork.utils.net.httpconnection.HTTPConnectionImpl;
import org.appwork.utils.net.httpconnection.HTTPConnectionUtils.IPVERSION;
import org.appwork.utils.net.httpconnection.HTTPProxy;
import org.jdownloader.controlling.filter.CompiledFiletypeFilter;
import org.jdownloader.controlling.filter.CompiledFiletypeFilter.CompiledFiletypeExtension;

import jd.http.Browser;
import jd.http.Request;
import jd.http.RequestHeader;
import jd.http.URLConnectionAdapter;
import jd.parser.Regex;

public class PluginBrowser<T extends Plugin> extends Browser {
    private final T plugin;

    public T getPlugin() {
        return plugin;
    }

    public PluginBrowser(final T plugin) {
        super();
        this.plugin = plugin;
    }

    @Override
    public Browser createNewBrowserInstance() {
        return getPlugin().createNewBrowserInstance();
    }

    public Map<String, Object> getVideoObject() {
        final String[] ldJSONs = getRegex("<script type\\s*=\\s*\"application/ld\\+json\"[^>]*>\\s*(\\{.*?\\})\\s*</script>").getColumn(0);
        for (final String ldJSON : ldJSONs) {
            try {
                final Map<String, Object> map = getPlugin().restoreFromString(ldJSON, TypeRef.MAP);
                if (map == null) {
                    continue;
                }
                // https://schema.org/VideoObject
                if ("VideoObject".equals(map.get("@type"))) {
                    return map;
                }
            } catch (Exception e) {
                getPlugin().getLogger().log(e);
            }
        }
        return null;
    }

    @Override
    protected void autoCompleteHeaders(Request request) {
        if (request == null) {
            return;
        }
        final RequestHeader requestHeaders = request.getHeaders();
        boolean addSecHeaders = false;
        final String firefoxVersionString = new Regex(requestHeaders.getValue("User-Agent"), "FireFox/(\\d+)").getMatch(0);
        final int firefoxVersion = firefoxVersionString != null ? Integer.parseInt(firefoxVersionString) : -1;
        if (firefoxVersion >= 90) {
            addSecHeaders = true;
        }
        final String chromeVersionString = new Regex(requestHeaders.getValue("User-Agent"), "Chrome/(\\d+)").getMatch(0);
        final int chromeVersion = chromeVersionString != null ? Integer.parseInt(chromeVersionString) : -1;
        if (chromeVersion >= 76) {
            addSecHeaders = true;
        }
        final String operaVersionString = new Regex(requestHeaders.getValue("User-Agent"), "OPR/(\\d+)").getMatch(0);
        final int operaVersion = operaVersionString != null ? Integer.parseInt(operaVersionString) : -1;
        if (operaVersion >= 63) {
            addSecHeaders = true;
        }
        final String edgeVersionString = new Regex(requestHeaders.getValue("User-Agent"), "Edg/(\\d+)").getMatch(0);
        final int edgeVersion = edgeVersionString != null ? Integer.parseInt(edgeVersionString) : -1;
        if (edgeVersion >= 79) {
            addSecHeaders = true;
        }
        if (!addSecHeaders) {
            return;
        }
        if (getHeaders().getHeader(HTTPConstants.HEADER_REQUEST_SEC_FETCH_SITE) == null) {
            requestHeaders.put(new HTTPHeader(HTTPConstants.HEADER_REQUEST_SEC_FETCH_SITE, "same-origin"));
        }
        if (getHeaders().getHeader(HTTPConstants.HEADER_REQUEST_SEC_FETCH_DEST) == null) {
            final String path = request.getURL().getFile();
            final CompiledFiletypeExtension extension = CompiledFiletypeFilter.getExtensionsFilterInterface(Files.getExtension(path, true));
            if (CompiledFiletypeFilter.AudioExtensions.MP3.isSameExtensionGroup(extension)) {
                requestHeaders.put(new HTTPHeader(HTTPConstants.HEADER_REQUEST_SEC_FETCH_DEST, "audio"));
            } else if (CompiledFiletypeFilter.VideoExtensions.MP4.isSameExtensionGroup(extension)) {
                requestHeaders.put(new HTTPHeader(HTTPConstants.HEADER_REQUEST_SEC_FETCH_DEST, "video"));
            } else if (CompiledFiletypeFilter.ImageExtensions.BMP.isSameExtensionGroup(extension)) {
                requestHeaders.put(new HTTPHeader(HTTPConstants.HEADER_REQUEST_SEC_FETCH_DEST, "image"));
            } else {
                requestHeaders.put(new HTTPHeader(HTTPConstants.HEADER_REQUEST_SEC_FETCH_DEST, "document"));
            }
        }
    }

    @Override
    public Regex getRegex(Pattern compile) {
        final Request request = getRequest();
        if (DebugMode.TRUE_IN_IDE_ELSE_FALSE && request != null && RequestMethod.HEAD.equals(request.getRequestMethod())) {
            getPlugin().getLogger().log(new Exception("FIXME: getRegex on HEAD request"));
        }
        return super.getRegex(compile);
    }

    @Override
    public Regex getRegex(String string) {
        final Request request = getRequest();
        if (DebugMode.TRUE_IN_IDE_ELSE_FALSE && request != null && RequestMethod.HEAD.equals(request.getRequestMethod())) {
            getPlugin().getLogger().log(new Exception("FIXME: getRegex on HEAD request"));
        }
        return super.getRegex(string);
    }

    @Override
    public boolean containsHTML(String regex) {
        final Request request = getRequest();
        if (DebugMode.TRUE_IN_IDE_ELSE_FALSE && request != null && RequestMethod.HEAD.equals(request.getRequestMethod())) {
            getPlugin().getLogger().log(new Exception("FIXME: containsHTML on HEAD request"));
        }
        return super.containsHTML(regex);
    }

    @Override
    public URLConnectionAdapter createHTTPConnection(final Request request, HTTPProxy proxy) throws IOException {
        final class LocalHostChecker {
            public void check(final InetAddress remoteIP) throws IOException {
                if (!remoteIP.isLoopbackAddress()) {
                    if (remoteIP.isAnyLocalAddress()) {
                        throw new BlockedByException(request, GenericSupportedBlockTypes.GENERIC_LOCALHOST_DNS);
                    }
                    return;
                }
                {
                    if ("localhost".equalsIgnoreCase(request.getURL().getHost())) {
                        return;
                    }
                    final InetAddress[] rawIPInetAddress = HTTPConnectionImpl.resolveLiteralIP(request.getURL().getHost());
                    if (rawIPInetAddress != null && rawIPInetAddress[0].isLoopbackAddress()) {
                        return;
                    }
                }
                if (remoteIP.getAddress().toString().startsWith("127.42.")) {
                    throw new BlockedByException(request, GenericSupportedBlockTypes.MALWAREBYTES_LOCALHOST_DNS);
                } else {
                    throw new BlockedByException(request, GenericSupportedBlockTypes.GENERIC_LOCALHOST_DNS);
                }
            }
        }
        final DNSResolver resolver = new DNSResolver() {
            final LocalHostChecker localHostChecker = new LocalHostChecker();

            @Override
            public InetAddress[] resolveDomain(REQUESTOR requestor, IPVERSION ipVersion, String domain) throws IOException {
                final InetAddress[] ret = DEFAULT.resolveDomain(requestor, ipVersion, domain);
                if (ret != null) {
                    for (final InetAddress inetAddress : ret) {
                        localHostChecker.check(inetAddress);
                    }
                }
                return ret;
            }
        };
        final URLConnectionAdapter ret = super.createHTTPConnection(request, proxy);
        ret.setDNSResolver(resolver);
        return ret;
    }
}
