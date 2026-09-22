package et.ut.einvoice.notifications.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/**
 * Standard RFC 5321 Email Transport Provider Adapter.
 * Supports TLS / STARTTLS, secure authentication, connect and read timeouts,
 * and high-fidelity operational diagnostics without exposing secrets.
 */
@Component
public class SmtpEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailProvider.class);

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String fromEmail;
    private final boolean isConfigured;
    private final String activeProfile;

    public record EmailDeliveryResult(
            boolean success,
            String status, // NOT_CONFIGURED, CONFIGURED, AUTHENTICATION_FAILED, CONNECTION_FAILED, TLS_FAILED, SENDER_REJECTED, SUBMISSION_FAILED, ACCEPTED
            String providerMessageId,
            String message
    ) {}

    public SmtpEmailProvider(
            @Value("${notifications.email.smtp.host:${SMTP_HOST:}}") String host,
            @Value("${notifications.email.smtp.port:${SMTP_PORT:587}}") int port,
            @Value("${notifications.email.smtp.username:${SMTP_USERNAME:}}") String username,
            @Value("${notifications.email.smtp.password:${SMTP_PASSWORD:}}") String password,
            @Value("${notifications.email.from:${NOTIFICATION_FROM_EMAIL:${SMTP_FROM:no-reply@mor.gov.et}}}") String fromEmail,
            @Value("${spring.profiles.active:dev}") String activeProfile
    ) {
        this.host = host != null ? host.trim() : "";
        this.port = port > 0 ? port : 587;
        this.username = username != null ? username.trim() : "";
        this.password = password != null ? password : "";
        this.fromEmail = (fromEmail != null && !fromEmail.isBlank()) ? fromEmail.trim() : "no-reply@ut-invoice.et";
        this.activeProfile = activeProfile != null ? activeProfile : "dev";
        this.isConfigured = !this.host.isBlank() && (!this.username.isBlank() || this.port == 25);

        if (this.isConfigured) {
            log.info("SmtpEmailProvider initialized with host: {}:{} (from: {})", this.host, this.port, this.fromEmail);
        } else {
            log.info("SmtpEmailProvider unconfigured (SMTP_HOST or SMTP_USERNAME missing). Active profile: {}", this.activeProfile);
        }
    }

    @Override
    public boolean sendEmail(String recipientEmail, String subject, String bodyText) {
        return sendEmailWithResult(recipientEmail, subject, bodyText).success();
    }

    public EmailDeliveryResult sendEmailWithResult(String recipientEmail, String subject, String bodyText) {
        if (!isConfigured) {
            if ("prod".equalsIgnoreCase(activeProfile) || "production".equalsIgnoreCase(activeProfile)) {
                log.error("Email dispatch rejected: SMTP provider credentials are not configured in production.");
                return new EmailDeliveryResult(false, "NOT_CONFIGURED", null, "SMTP provider credentials are not configured in production environment.");
            }
            log.info("[EMAIL-DEV-SIMULATION -> {}] [From: {}] Subject: {} | Body length: {}",
                    recipientEmail, fromEmail, subject, (bodyText != null ? bodyText.length() : 0));
            String devMessageId = "DEV-SIM-" + UUID.randomUUID();
            return new EmailDeliveryResult(true, "ACCEPTED", devMessageId, "Simulation mode dispatch accepted (dev profile).");
        }

        return executeSmtpTransmission(recipientEmail, subject, bodyText);
    }

    public EmailDeliveryResult checkConnection() {
        if (!isConfigured) {
            return new EmailDeliveryResult(false, "NOT_CONFIGURED", null, "SMTP provider host or credentials are not configured.");
        }
        return executeSmtpProbe();
    }

    private EmailDeliveryResult executeSmtpTransmission(String recipientEmail, String subject, String bodyText) {
        String messageId = "<" + UUID.randomUUID() + "@" + (host.isBlank() ? "ut-invoice.internal" : host) + ">";

        try (Socket rawSocket = new Socket()) {
            rawSocket.connect(new InetSocketAddress(host, port), 5000);
            rawSocket.setSoTimeout(5000);

            Socket activeSocket = rawSocket;
            if (port == 465) {
                SSLSocketFactory sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket sslSocket = (SSLSocket) sf.createSocket(rawSocket, host, port, true);
                sslSocket.startHandshake();
                activeSocket = sslSocket;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(activeSocket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(activeSocket.getOutputStream(), StandardCharsets.UTF_8));

            // 1. Initial Greeting
            String banner = readSmtpResponse(reader);
            if (!banner.startsWith("220")) {
                return new EmailDeliveryResult(false, "CONNECTION_FAILED", null, "Unexpected greeting banner: " + sanitize(banner));
            }

            // 2. EHLO
            sendLine(writer, "EHLO ut-invoice.platform");
            String ehloResp = readSmtpResponse(reader);

            // 3. STARTTLS for port 587 or port 25 if advertised
            if (port != 465 && ehloResp.toUpperCase(Locale.ROOT).contains("STARTTLS")) {
                sendLine(writer, "STARTTLS");
                String tlsResp = readSmtpResponse(reader);
                if (!tlsResp.startsWith("220")) {
                    return new EmailDeliveryResult(false, "TLS_FAILED", null, "STARTTLS handshake rejected by server: " + sanitize(tlsResp));
                }
                try {
                    SSLSocketFactory sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
                    SSLSocket sslSocket = (SSLSocket) sf.createSocket(activeSocket, host, port, true);
                    sslSocket.startHandshake();
                    activeSocket = sslSocket;
                    reader = new BufferedReader(new InputStreamReader(activeSocket.getInputStream(), StandardCharsets.UTF_8));
                    writer = new BufferedWriter(new OutputStreamWriter(activeSocket.getOutputStream(), StandardCharsets.UTF_8));

                    // Re-issue EHLO over TLS
                    sendLine(writer, "EHLO ut-invoice.platform");
                    ehloResp = readSmtpResponse(reader);
                } catch (Exception ex) {
                    return new EmailDeliveryResult(false, "TLS_FAILED", null, "TLS handshake failed: " + ex.getClass().getSimpleName());
                }
            }

            // 4. AUTH LOGIN if credentials present
            if (!username.isBlank() && !password.isBlank()) {
                sendLine(writer, "AUTH LOGIN");
                String authResp = readSmtpResponse(reader);
                if (!authResp.startsWith("334")) {
                    return new EmailDeliveryResult(false, "AUTHENTICATION_FAILED", null, "SMTP AUTH LOGIN rejected by server.");
                }
                sendLine(writer, Base64.getEncoder().encodeToString(username.getBytes(StandardCharsets.UTF_8)));
                String userResp = readSmtpResponse(reader);
                if (!userResp.startsWith("334")) {
                    return new EmailDeliveryResult(false, "AUTHENTICATION_FAILED", null, "SMTP username rejected by server.");
                }
                sendLine(writer, Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)));
                String passResp = readSmtpResponse(reader);
                if (!passResp.startsWith("235")) {
                    return new EmailDeliveryResult(false, "AUTHENTICATION_FAILED", null, "SMTP authentication credentials rejected.");
                }
            }

            // 5. MAIL FROM
            sendLine(writer, "MAIL FROM:<" + fromEmail + ">");
            String mailFromResp = readSmtpResponse(reader);
            if (!mailFromResp.startsWith("250")) {
                return new EmailDeliveryResult(false, "SENDER_REJECTED", null, "Sender address rejected by mail server.");
            }

            // 6. RCPT TO
            sendLine(writer, "RCPT TO:<" + recipientEmail + ">");
            String rcptResp = readSmtpResponse(reader);
            if (!rcptResp.startsWith("250") && !rcptResp.startsWith("251")) {
                return new EmailDeliveryResult(false, "SENDER_REJECTED", null, "Recipient address rejected by mail server.");
            }

            // 7. DATA
            sendLine(writer, "DATA");
            String dataResp = readSmtpResponse(reader);
            if (!dataResp.startsWith("354")) {
                return new EmailDeliveryResult(false, "SUBMISSION_FAILED", null, "DATA command rejected by mail server.");
            }

            writer.write("From: " + fromEmail + "\r\n");
            writer.write("To: " + recipientEmail + "\r\n");
            writer.write("Subject: " + (subject != null ? subject : "UT Invoice Notification") + "\r\n");
            writer.write("Message-ID: " + messageId + "\r\n");
            writer.write("Date: " + DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneId.of("UTC"))) + "\r\n");
            writer.write("MIME-Version: 1.0\r\n");
            writer.write("Content-Type: text/plain; charset=UTF-8\r\n");
            writer.write("Content-Transfer-Encoding: 8bit\r\n");
            writer.write("\r\n");
            if (bodyText != null) {
                writer.write(bodyText.replace("\n.", "\n.."));
            }
            writer.write("\r\n.\r\n");
            writer.flush();

            String submitResp = readSmtpResponse(reader);
            if (!submitResp.startsWith("250")) {
                return new EmailDeliveryResult(false, "SUBMISSION_FAILED", null, "Message submission rejected by mail server.");
            }

            try {
                sendLine(writer, "QUIT");
            } catch (Exception ignored) {}

            log.info("Email delivered via SMTP {}:{} to {} [ID: {}]", host, port, recipientEmail, messageId);
            return new EmailDeliveryResult(true, "ACCEPTED", messageId, "Email message accepted by SMTP mail server.");

        } catch (ConnectException | UnknownHostException | SocketTimeoutException ex) {
            log.error("Failed to connect to SMTP host {}:{}: {}", host, port, ex.getClass().getSimpleName());
            return new EmailDeliveryResult(false, "CONNECTION_FAILED", null, "Failed to connect to SMTP server: " + ex.getClass().getSimpleName());
        } catch (SSLException ex) {
            log.error("SMTP TLS/SSL failure: {}", ex.getClass().getSimpleName());
            return new EmailDeliveryResult(false, "TLS_FAILED", null, "TLS/SSL negotiation failed: " + ex.getClass().getSimpleName());
        } catch (Exception ex) {
            log.error("SMTP submission failure: {}", ex.getClass().getSimpleName());
            return new EmailDeliveryResult(false, "SUBMISSION_FAILED", null, "SMTP transmission error: " + ex.getClass().getSimpleName());
        }
    }

    private EmailDeliveryResult executeSmtpProbe() {
        try (Socket rawSocket = new Socket()) {
            rawSocket.connect(new InetSocketAddress(host, port), 4000);
            rawSocket.setSoTimeout(4000);

            Socket activeSocket = rawSocket;
            if (port == 465) {
                SSLSocketFactory sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket sslSocket = (SSLSocket) sf.createSocket(rawSocket, host, port, true);
                sslSocket.startHandshake();
                activeSocket = sslSocket;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(activeSocket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(activeSocket.getOutputStream(), StandardCharsets.UTF_8));

            String banner = readSmtpResponse(reader);
            if (!banner.startsWith("220")) {
                return new EmailDeliveryResult(false, "CONNECTION_FAILED", null, "Unexpected SMTP greeting banner.");
            }

            sendLine(writer, "EHLO ut-invoice.platform");
            String ehloResp = readSmtpResponse(reader);

            if (port != 465 && ehloResp.toUpperCase(Locale.ROOT).contains("STARTTLS")) {
                sendLine(writer, "STARTTLS");
                String tlsResp = readSmtpResponse(reader);
                if (!tlsResp.startsWith("220")) {
                    return new EmailDeliveryResult(false, "TLS_FAILED", null, "STARTTLS handshake rejected by server.");
                }
                SSLSocketFactory sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket sslSocket = (SSLSocket) sf.createSocket(activeSocket, host, port, true);
                sslSocket.startHandshake();
                activeSocket = sslSocket;
                reader = new BufferedReader(new InputStreamReader(activeSocket.getInputStream(), StandardCharsets.UTF_8));
                writer = new BufferedWriter(new OutputStreamWriter(activeSocket.getOutputStream(), StandardCharsets.UTF_8));

                sendLine(writer, "EHLO ut-invoice.platform");
                readSmtpResponse(reader);
            }

            if (!username.isBlank() && !password.isBlank()) {
                sendLine(writer, "AUTH LOGIN");
                String authResp = readSmtpResponse(reader);
                if (!authResp.startsWith("334")) {
                    return new EmailDeliveryResult(false, "AUTHENTICATION_FAILED", null, "SMTP AUTH LOGIN rejected.");
                }
                sendLine(writer, Base64.getEncoder().encodeToString(username.getBytes(StandardCharsets.UTF_8)));
                readSmtpResponse(reader);
                sendLine(writer, Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)));
                String passResp = readSmtpResponse(reader);
                if (!passResp.startsWith("235")) {
                    return new EmailDeliveryResult(false, "AUTHENTICATION_FAILED", null, "SMTP authentication credentials rejected.");
                }
            }

            try {
                sendLine(writer, "QUIT");
            } catch (Exception ignored) {}

            return new EmailDeliveryResult(true, "CONFIGURED", null, "SMTP server connection and authentication verified successfully.");

        } catch (ConnectException | UnknownHostException | SocketTimeoutException ex) {
            return new EmailDeliveryResult(false, "CONNECTION_FAILED", null, "Connection to SMTP server failed: " + ex.getClass().getSimpleName());
        } catch (SSLException ex) {
            return new EmailDeliveryResult(false, "TLS_FAILED", null, "TLS/SSL negotiation failed: " + ex.getClass().getSimpleName());
        } catch (Exception ex) {
            return new EmailDeliveryResult(false, "SUBMISSION_FAILED", null, "SMTP probe error: " + ex.getClass().getSimpleName());
        }
    }

    private String readSmtpResponse(BufferedReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
            if (line.length() >= 4 && line.charAt(3) == ' ') {
                break;
            }
            if (line.length() == 3) {
                break;
            }
        }
        return sb.toString().trim();
    }

    private void sendLine(BufferedWriter writer, String line) throws IOException {
        writer.write(line + "\r\n");
        writer.flush();
    }

    private String sanitize(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[\r\n]", " ").trim();
    }

    @Override
    public String getProviderName() {
        return "SMTP Email Transport";
    }

    @Override
    public boolean isConfigured() {
        return isConfigured;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public String getMaskedUsername() {
        if (username.isBlank()) return "NOT_SET";
        if (username.contains("@")) {
            String[] p = username.split("@", 2);
            return (p[0].length() <= 2 ? p[0].charAt(0) + "***" : p[0].substring(0, 2) + "***") + "@" + p[1];
        }
        return username.length() <= 3 ? "***" : username.substring(0, 2) + "***" + username.substring(username.length() - 1);
    }
}
