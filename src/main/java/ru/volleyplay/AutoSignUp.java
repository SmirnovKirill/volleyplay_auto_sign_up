package ru.volleyplay;

import jakarta.mail.*;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class AutoSignUp {
  private static final Logger GENERAL_LOGGER = LoggerFactory.getLogger("general");
  private static final Logger IMPORTANT_EVENTS_LOGGER = LoggerFactory.getLogger("important");
  private static final long SLEEP_PERIOD_MS = 2000;
  private static final Properties MAIL_SESSION_PROPERTIES;

  static {
    MAIL_SESSION_PROPERTIES = new Properties();
    MAIL_SESSION_PROPERTIES.put("mail.smtp.connectiontimeout", 1000);
    MAIL_SESSION_PROPERTIES.put("mail.smtp.timeout", 1000);
  }

  public static void main(String[] args) throws IOException {
    ScriptProperties scriptProperties = ScriptProperties.readProperties();

    long iteration = 1;
    while (true) {
      try {
        GENERAL_LOGGER.info("starting auto sign-up iteration #{}", iteration);
        performAutoSignUpIteration(scriptProperties);
      } catch (Exception e) {
        IMPORTANT_EVENTS_LOGGER.error("auto sign-up iteration #{} has failed", iteration, e);
      } finally {
        iteration++;
        try {
          Thread.sleep(SLEEP_PERIOD_MS);
        } catch (InterruptedException e) {
          GENERAL_LOGGER.info("terminating script");
          System.exit(0);
        }
      }
    }
  }

  private static void performAutoSignUpIteration(ScriptProperties scriptProperties) throws MessagingException {
    Session session = Session.getDefaultInstance(MAIL_SESSION_PROPERTIES);

    try (Store store = session.getStore("imaps")) {
      store.connect(scriptProperties.imapHost(), scriptProperties.imapPort(), scriptProperties.username(), scriptProperties.password());
      try (Folder inbox = store.getFolder("INBOX")) {
        inbox.open(Folder.READ_WRITE);

        Message[] messages = inbox.search(
            new ReceivedDateTerm(ComparisonTerm.GE, Date.from(LocalDate.now().atStartOfDay(scriptProperties.mailServerTimeZone()).toInstant()))
        );

        handleMessages(messages);
      }
    }
  }

  private static void handleMessages(Message[] messages) {
    List<Message> volleyPlaySignUpMessages = Arrays.stream(messages)
        .filter(AutoSignUp::isUnseenVolleyPlaySignUpMessage)
        .toList();
    GENERAL_LOGGER.info("got {} unseen messages from volleyplay", volleyPlaySignUpMessages.size());

    volleyPlaySignUpMessages.forEach(AutoSignUp::handleVolleyPlaySignUpMessage);
  }

  private static boolean isUnseenVolleyPlaySignUpMessage(Message message) {
    boolean seen = false;
    try {
      seen = message.getFlags().contains(Flags.Flag.SEEN);
    } catch (MessagingException e) {
      IMPORTANT_EVENTS_LOGGER.error("failed to get flags for message, will treat message as not seen", e);
    }

    if (seen) {
      return false;
    }

    String subject;
    try {
      subject = message.getSubject();
    } catch (MessagingException e) {
      throw new RuntimeException("failed to get message subject", e);
    }

    Address[] from;
    try {
      from = message.getFrom();
    } catch (MessagingException e) {
      throw new RuntimeException("failed to get message subject", e);
    }

    return Arrays.stream(from).anyMatch(address -> address.toString().contains("volleyplay"))
        && subject.startsWith("Освободилось место на занятие");
  }

  private static void handleVolleyPlaySignUpMessage(Message message) {
    try {
      String messageText = MailUtils.getMessageText(message);
      findLinkAndAutoSignUp(messageText);
    } catch (Exception e) {
      IMPORTANT_EVENTS_LOGGER.error("failed to handle volleyplay sign-up message, will try to set seen flag to false", e);
      try {
        message.setFlag(Flags.Flag.SEEN, false);
      } catch (MessagingException ex) {
        throw new RuntimeException("failed to set seen flag to false", e);
      }
      IMPORTANT_EVENTS_LOGGER.info("seen flag has been set to false successfully");
    }
  }

  private static void findLinkAndAutoSignUp(String messageText) throws IOException {
    Document parsedMessage = Jsoup.parse(messageText);

    Elements links = parsedMessage.getElementsByTag("a");
    List<Element> signUpLinks = links.stream()
        .filter(link -> link.attr("href").contains("entryPoint=write"))
        .toList();
    if (signUpLinks.size() != 1) {
      IMPORTANT_EVENTS_LOGGER.error("unexpected sign-up message format, have to rewrite the script");
      System.exit(1);
    }

    String signUpLink = signUpLinks.get(0).attr("href");
    autoSignUp(signUpLink);
  }

  private static void autoSignUp(String signUpLink) throws IOException {
    try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
      HttpGet httpget = new HttpGet(signUpLink);
      HttpResponse httpresponse = httpclient.execute(httpget);
      String responseText = EntityUtils.toString(httpresponse.getEntity());
      if (responseText.contains("К сожалению, мест больше нет")) {
        IMPORTANT_EVENTS_LOGGER.warn("script was too slow and failed to sign you up");
      } else if (responseText.contains("Вы успешно записались на занятие")) {
        IMPORTANT_EVENTS_LOGGER.info("script was fast enough and successfully signed you up");
      } else {
        IMPORTANT_EVENTS_LOGGER.error("unexpected sign-up response message format, have to rewrite the script, full text %s".formatted(responseText));
        System.exit(1);
      }
    }
  }
}
