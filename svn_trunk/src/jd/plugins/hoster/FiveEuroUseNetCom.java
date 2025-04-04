package jd.plugins.hoster;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.TimeFormatter;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperHostPluginRecaptchaV2;
import org.jdownloader.plugins.components.usenet.UsenetAccountConfigInterface;
import org.jdownloader.plugins.components.usenet.UsenetServer;

import jd.PluginWrapper;
import jd.http.Cookies;
import jd.nutils.encoding.Encoding;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;

@HostPlugin(revision = "$Revision: 50772 $", interfaceVersion = 3, names = { "5eurousenet.com" }, urls = { "" })
public class FiveEuroUseNetCom extends UseNet {
    public FiveEuroUseNetCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://www.5eurousenet.com/en/cart/checkout");
    }

    @Override
    public String getAGBLink() {
        return "https://www.5eurousenet.com/en/general-terms";
    }

    public static interface FiveEuroUseNetComConfigInterface extends UsenetAccountConfigInterface {
    };

    @Override
    public AccountInfo fetchAccountInfo(Account account) throws Exception {
        setBrowserExclusive();
        final AccountInfo ai = new AccountInfo();
        br.setFollowRedirects(true);
        final Cookies cookies = account.loadCookies("");
        boolean freshLogin = true;
        if (cookies != null) {
            br.setCookies(getHost(), cookies);
            br.getPage("https://www.5eurousenet.com/en/user");
            final Form login = br.getFormbyActionRegex("/login");
            if (login != null && login.containsHTML("name") && login.containsHTML("pass")) {
                logger.info("Cookie login failed");
                freshLogin = true;
            } else if (!br.containsHTML("/user/logout")) {
                logger.info("Cookie login failed");
                freshLogin = true;
            } else {
                freshLogin = false;
            }
        }
        if (freshLogin) {
            account.clearCookies("");
            final String userName = account.getUser();
            br.getPage("https://www.5eurousenet.com/en/user/login");
            Form login = br.getFormbyActionRegex(".*?user/login\\?");
            if (login == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            login.put("name", Encoding.urlEncode(userName));
            login.put("pass", Encoding.urlEncode(account.getPass()));
            if (login.containsHTML("g-recaptcha")) {
                final CaptchaHelperHostPluginRecaptchaV2 rc2 = new CaptchaHelperHostPluginRecaptchaV2(this, br);
                final String code = rc2.getToken();
                if (StringUtils.isEmpty(code)) {
                    throw new PluginException(LinkStatus.ERROR_CAPTCHA);
                } else {
                    login.put("g-recaptcha-response", Encoding.urlEncode(code));
                }
            }
            br.submitForm(login);
            login = br.getFormbyActionRegex(".*?user/login");
            if (login != null && login.containsHTML("name") && login.containsHTML("pass")) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
            } else if (!br.containsHTML("/user/logout")) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        }
        account.saveCookies(br.getCookies(br.getHost()), "");
        final String accountType = br.getRegex("Account type\\s*:\\s*</div>\\s*<div[^<]*>\\s*(.*?)</").getMatch(0);
        ai.setStatus(accountType);
        final String endDate = br.getRegex("<div[^<]*>\\s*End date\\s*:\\s*</div>\\s*<div[^<]*>\\s*(.*?)\\s*</").getMatch(0);
        if (endDate != null) {
            final long validUnitl = TimeFormatter.getMilliSeconds(endDate, "MMM' 'dd' 'yyyy' - 'HH':'mm", Locale.ENGLISH);
            ai.setValidUntil(validUnitl);
        }
        final boolean isExpired = br.containsHTML(">\\s*Your account has expired\\.?\\s*<");
        if (ai.isExpired() == false && isExpired) {
            ai.setExpired(true);
        }
        account.setMaxSimultanDownloads(20);
        account.setRefreshTimeout(5 * 60 * 60 * 1000l);
        ai.setMultiHostSupport(this, Arrays.asList(new String[] { "usenet" }));
        return ai;
    }

    @Override
    public List<UsenetServer> getAvailableUsenetServer() {
        final List<UsenetServer> ret = new ArrayList<UsenetServer>();
        ret.addAll(UsenetServer.createServerList("reader.5eurousenet.com", false, 119));
        ret.addAll(UsenetServer.createServerList("reader.5eurousenet.com", true, 563, 443, 89));
        return ret;
    }
}
