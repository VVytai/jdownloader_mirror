//jDownloader - Downloadmanager
//Copyright (C) 2013  JD-Team support@jdownloader.org
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
import java.util.List;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.plugins.HostPlugin;

@HostPlugin(revision = "$Revision: 49465 $", interfaceVersion = 3, names = {}, urls = {})
public class KernelVideoSharingLove4pornCom extends KernelVideoSharingComV2 {
    public KernelVideoSharingLove4pornCom(final PluginWrapper wrapper) {
        super(wrapper);
    }

    /** Add all KVS hosts to this list that fit the main template without the need of ANY changes to this class. */
    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForHost, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "love4porn.com" });
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
        return KernelVideoSharingComV2.buildAnnotationUrlsDefaultVideosPattern(getPluginDomains());
    }

    @Override
    protected String generateContentURL(final String host, final String fuid, final String urlTitle) {
        return generateContentURLDefaultVideosPattern(host, fuid, urlTitle);
    }

    @Override
    protected boolean isOfflineWebsite(final Browser br) {
        final boolean isOffline = super.isOfflineWebsite(br);
        if (isOffline) {
            return true;
        }
        /* Looks to be online -> Check deeper */
        final String fuid = this.getFUIDFromURL(br.getURL());
        if (fuid != null && !br.containsHTML("/embed/" + fuid)) {
            /* 2024-07-30 */
            /**
             * Example: https://love4porn.com/videos/32026/jizzorama-chubby </br>
             * -> Does not display any kind of error message but item is offline.
             */
            return true;
        } else {
            return false;
        }
    }
}