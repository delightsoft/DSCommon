package code.docflow.utils;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import play.Logger;
import play.Play;
import play.data.validation.Validation;
import play.mvc.Http;
import play.mvc.results.Redirect;
import play.mvc.results.RenderHtml;
import play.mvc.results.RenderText;

public class HttpUtil {

    public static void scriptRedirect(String redirectUrl) {
        String scriptRedirectParam = Play.configuration.getProperty("scriptRedirect", "no");
        if (BooleanUtil.parse(scriptRedirectParam))
            throw new RenderHtml("<html><head><script language=\"JavaScript\">window.location.href = \"" + redirectUrl + "\"</script></head><body></body></html>");
        else
            throw new Redirect(redirectUrl, false);
    }

    /**
     * Removes surrogate UTF-8 characters. Some surrogate characters may cause exception in JsonGenerator, and
     * by out observation are usally a trash created by some mobile clients.  Could smiles, hearts or some other icons,
     * which are not part of UTF-8 standard.
     */
    public static String removeSurrgateChars(String source) {
        if (source == null)
            return null;
        for (int i = 0; i < source.length(); i++) {
            final char c = source.charAt(i);
            if (Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) {
                final StringBuilder sb = new StringBuilder();
                for (int j = 0; j < source.length(); j++) {
                    final char c2 = source.charAt(j);
                    if (!Character.isHighSurrogate(c2) && !Character.isLowSurrogate(c2))
                        sb.append(c2);
                }
                return sb.toString();
            }
        }
        return source;
    }

    public static void returnErrors() {
        if (Validation.hasErrors()) {
            final StringBuilder sb = new StringBuilder();
            for (play.data.validation.Error error : Validation.errors())
                sb.append(error.getKey()).append(": ").append(error.message()).append("\n");
            final Http.Response response = Http.Response.current().current();
            response.status = Http.StatusCode.BAD_REQUEST;
            final String err = sb.toString();
            Logger.error("Bad HTTP request:\n%s", err);
            throw new RenderText(err);
        }
    }
}
