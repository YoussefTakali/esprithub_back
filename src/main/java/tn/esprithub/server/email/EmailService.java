package tn.esprithub.server.email;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${app.mail.from:noreply@esprithub.com}")
    private String mailFrom;

    public void sendCredentialsEmail(String to, String username, String password) {
        if (!canSendEmails()) {
            log.warn("Skipping credentials email for {} because SMTP is disabled or not configured", to);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);
            helper.setTo(to);
            helper.setSubject("Vos identifiants de connexion - espriHUb");
            helper.setText(buildCredentialsEmailTemplate(to, username, password), true);

            mailSender.send(message);
            log.info("Credentials email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send credentials email to {}", to, e);
        }
    }

    /**
     * Envoie une notification par email avec contenu HTML personnalisé
     */
    public void sendNotificationEmail(String to, String subject, String htmlContent) {
        if (!canSendEmails()) {
            log.warn("Skipping notification email for {} because SMTP is disabled or not configured", to);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true = contenu HTML

                        mailSender.send(message);
                        log.info("Notification email sent to {}", to);
        } catch (Exception e) {
                        log.error("Error sending notification email to {}", to, e);
        }
    }

    private boolean canSendEmails() {
        return mailEnabled && StringUtils.hasText(mailUsername) && StringUtils.hasText(mailPassword);
    }

        private String buildCredentialsEmailTemplate(String email, String username, String password) {
                return """
                                <!DOCTYPE html>
                                <html lang=\"fr\">
                                <head>
                                    <meta charset=\"UTF-8\">
                                    <style>
                                        body { font-family: 'Segoe UI', sans-serif; background-color: #f9f9f9; }
                                        .container { background-color: #ffffff; max-width: 600px; margin: 30px auto; padding: 20px; border-radius: 8px; }
                                        .header { background-color: #a71617; color: white; padding: 20px; text-align: center; }
                                        .credentials { background-color: #f1f1f1; padding: 15px; border-radius: 6px; margin: 20px 0; font-family: monospace; }
                                        .footer { background-color: #fafafa; color: #888888; text-align: center; padding: 15px; font-size: 12px; }
                                    </style>
                                </head>
                                <body>
                                    <div class=\"container\">
                                        <div class=\"header\"><h1>Bienvenue sur espritHUb</h1></div>
                                        <p>Bonjour,</p>
                                        <p>Votre compte a été créé avec succès. Voici vos identifiants :</p>
                                        <div class=\"credentials\">
                                            <p><strong>Email :</strong> %s</p>
                                            <p><strong>Nom d'utilisateur :</strong> %s</p>
                                            <p><strong>Mot de passe :</strong> %s</p>
                                        </div>
                                        <p>Merci et bienvenue !</p>
                                        <div class=\"footer\">&copy; 2025 espriHUb. Tous droits réservés.</div>
                                    </div>
                                </body>
                                </html>
                                """.formatted(email, username, password);
        }
}
