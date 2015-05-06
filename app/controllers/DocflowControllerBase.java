package controllers;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import play.Logger;
import play.data.validation.Validation;
import play.db.jpa.NoTransaction;
import play.exceptions.PlayException;
import play.exceptions.UnexpectedException;
import play.mvc.Before;
import play.mvc.Catch;
import play.mvc.Controller;
import play.mvc.Http;

public class DocflowControllerBase extends Controller {

    @Before
    public static void addCORSHeaders() {
        // Enable CORS (http://enable-cors.org/server.html)
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");
    }

    @Catch(value = Throwable.class)
    public static void processExceptions(Throwable throwable) {
        PlayException ex = throwable instanceof PlayException ?
                (PlayException) throwable :
                new UnexpectedException(throwable);
        Logger.error(ex, "Server failure");
        response.status = Http.StatusCode.INTERNAL_ERROR;
        renderText("Server error: " + ex.getId());
    }

    /**
     * Returns Play validation errors, as result of HTTP request.
     */
    static void returnIfErrors() {
        if (Validation.hasErrors()) {
            final StringBuilder sb = new StringBuilder();
            for (play.data.validation.Error error : Validation.errors())
                sb.append(error.getKey()).append(": ").append(error.message()).append("\n");

            response.status = Http.StatusCode.BAD_REQUEST;
            renderText(sb.toString());
        }
    }
}
