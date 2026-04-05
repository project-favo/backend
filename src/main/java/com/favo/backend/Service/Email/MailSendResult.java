package com.favo.backend.Service.Email;

/**
 * SMTP doğrulama maili sonucu (503 cevabında failureCode ile teşhis için).
 */
public record MailSendResult(boolean sent, String failureCode, String smtpDetail) {

    public static MailSendResult ok() {
        return new MailSendResult(true, null, null);
    }

    public static MailSendResult fail(String code) {
        return new MailSendResult(false, code, null);
    }

    public static MailSendResult fail(String code, String smtpDetail) {
        return new MailSendResult(false, code, smtpDetail);
    }
}
