package in.edu.kjc.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;

public class MailService {

    private final MailClient mailClient;
    private final String fromEmail;

    public MailService(Vertx vertx) {
        this.fromEmail = "lohithm978@gmail.com";
        String appPassword = "hiden for security";

        MailConfig config = new MailConfig()
                .setHostname("smtp.gmail.com")
                .setPort(587)
                .setStarttls(io.vertx.ext.mail.StartTLSOptions.REQUIRED)
                .setUsername(fromEmail)
                .setPassword(appPassword)
                .setTrustAll(true);

        this.mailClient = MailClient.createShared(vertx, config);
    }

    public Future<Void> sendEmail(String to, String subject, String bodyText) {
        MailMessage message = new MailMessage()
                .setFrom(fromEmail)
                .setTo(to)
                .setSubject(subject)
                .setText(bodyText);

        return mailClient.sendMail(message).mapEmpty();
    }
}
