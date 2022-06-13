package ru.volleyplay;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMultipart;
import java.io.IOException;

public class MailUtils {
  public static String getMessageText(Message message) {
    try {
      if (message.isMimeType("text/plain")) {
        return message.getContent().toString();
      } else if (message.isMimeType("multipart/*")) {
        return getMessageTextFromMimeMultipart((MimeMultipart) message.getContent());
      } else {
        throw new IllegalStateException("Unsupported message type %s".formatted(message.getContentType()));
      }
    } catch (MessagingException | IOException e) {
      throw new RuntimeException("Failed to get message text", e);
    }
  }

  private static String getMessageTextFromMimeMultipart(MimeMultipart mimeMultipart) throws MessagingException, IOException{
    StringBuilder result = new StringBuilder();

    for (int i = 0; i < mimeMultipart.getCount(); i++) {
      BodyPart bodyPart = mimeMultipart.getBodyPart(i);
      if (bodyPart.isMimeType("text/plain") || bodyPart.isMimeType("text/html")) {
        result.append(bodyPart.getContent());
      } else {
        throw new IllegalStateException("Unsupported multipart type %s".formatted(bodyPart.getContentType()));
      }
    }

    return result.toString();
  }
}
