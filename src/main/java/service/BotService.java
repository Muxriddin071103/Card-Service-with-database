package service;

import connection.TestConnection;
import entities.Card;
import entities.User;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BotService {
    private Long chatId;
    private final MarkupService markupService = MarkupService.getMarkupService();
    private final MyBot myBot = MyBot.getMyBot();
    private final CardService cardService = CardService.getInstance();

    private final Map<Long, String> userStates = new HashMap<>();

    public void messageHandler(Update update) {
        chatId = update.getMessage().getChatId();
        String messageText = update.getMessage().getText();
        SendMessage message = new SendMessage();
        message.setChatId(chatId);

        if (userStates.containsKey(chatId)) {
            handleUserInput(update, message);
        } else {
            switch (messageText) {
                case "/start":
                    if (!userExists(chatId)) {
                        addUser(chatId);
                    }
                    message.setText("Welcome to the bot!");
                    message.setReplyMarkup(markupService.getMarkup(new String[][]{{"my cards", "add card"}, {"transfer", "deposit"}, {"history"}}));
                    break;
                case "my cards":
                    User user = getUserByChatId(chatId);
                    if (user != null) {
                        StringBuilder cardsInfo = new StringBuilder("Your cards:\n");
                        List<Card> cards = cardService.getCardsByUserId(user.getId());
                        for (Card card : cards) {
                            cardsInfo.append("Card ID: ").append(card.getId()).append(") Card Number: ").append(card.getCardNumber()).append(",  Card Balance: ").append(card.getBalance()).append("\n");
                        }
                        message.setText(cardsInfo.toString());
                    } else {
                        message.setText("User not found.");
                    }
                    break;
                case "add card":
                    message.setText("Please enter the card number.");
                    userStates.put(chatId, "waiting_for_card_number");
                    break;
                case "transfer":
                    message.setText("Please enter the card number to transfer from, card number to transfer to, and amount in the format: fromCardNumber toCardNumber amount");
                    userStates.put(chatId, "waiting_for_transfer_details");
                    break;
                case "history":
                    message.setText("Please enter the card number to view history:");
                    userStates.put(chatId, "waiting_for_history_card_number");
                    break;
                case "deposit":
                    message.setText("Please enter the card number to deposit into and amount in the format: cardNumber amount");
                    userStates.put(chatId, "waiting_for_deposit_details");
                    break;
                default:
                    message.setText("Unknown command!");
            }
        }

        myBot.smsSender(message);
    }

    private void handleUserInput(Update update, SendMessage message) {
        String state = userStates.get(chatId);
        String messageText = update.getMessage().getText();

        if (messageText == null) {
            message.setText("Received null message text.");
            myBot.smsSender(message);
            return;
        }

        switch (state) {
            case "waiting_for_card_number":
                if (messageText.matches("\\d{16}")) {
                    User user = getUserByChatId(chatId);
                    if (user != null) {
                        Card newCard = new Card(0L, messageText, user.getId(), 100.0); // Default balance
                        cardService.addCard(newCard);
                        message.setText("Card added successfully.");
                    } else {
                        message.setText("User not found.");
                    }
                    userStates.remove(chatId);
                } else {
                    message.setText("Invalid card number. Please enter a valid 16-digit card number.");
                }
                break;

            case "waiting_for_transfer_details":
                String[] transferDetails = messageText.split(" ");
                if (transferDetails.length == 3) {
                    try {
                        String fromCardNumber = transferDetails[0];
                        String toCardNumber = transferDetails[1];
                        double amount = Double.parseDouble(transferDetails[2]);

                        long fromCardId = getCardIdByNumber(fromCardNumber);
                        long toCardId = getCardIdByNumber(toCardNumber);

                        if (fromCardId != -1 && toCardId != -1) {
                            cardService.transfer((int) fromCardId, (int) toCardId, amount);
                            message.setText("Transfer completed successfully.");
                        } else {
                            message.setText("Invalid card numbers. Please check and try again.");
                        }
                    } catch (NumberFormatException e) {
                        message.setText("Invalid input format. Please provide valid card numbers and amount.");
                    }
                } else {
                    message.setText("Invalid format. Please provide details in the format: fromCardNumber toCardNumber amount");
                }
                userStates.remove(chatId);
                break;

            case "waiting_for_history_card_number":
                if (messageText.matches("\\d{16}")) {
                    try {
                        long cardId = getCardIdByNumber(messageText);
                        if (cardId != -1) {
                            StringBuilder historyInfo = new StringBuilder("Transfer history:\n");
                            List<String> history = cardService.getHistory((int) cardId);
                            for (String record : history) {
                                historyInfo.append(record).append("\n");
                            }
                            message.setText(historyInfo.toString());
                        } else {
                            message.setText("Invalid card number. Please enter a valid 16-digit card number.");
                        }
                    } catch (NumberFormatException e) {
                        message.setText("Invalid card number. Please enter a valid 16-digit card number.");
                    }
                } else {
                    message.setText("Invalid card number. Please enter a valid 16-digit card number.");
                }
                userStates.remove(chatId);
                break;

            case "waiting_for_deposit_details":
                String[] depositDetails = messageText.split(" ");
                if (depositDetails.length == 2) {
                    try {
                        String cardNumber = depositDetails[0];
                        double amount = Double.parseDouble(depositDetails[1]);
                        long cardId = getCardIdByNumber(cardNumber);
                        if (cardId != -1) {
                            cardService.deposite((int) cardId, amount);
                            message.setText("Deposit completed successfully.");
                        } else {
                            message.setText("Invalid card number. Please enter a valid 16-digit card number.");
                        }
                    } catch (NumberFormatException e) {
                        message.setText("Invalid input format. Please provide a valid card number and amount.");
                    }
                } else {
                    message.setText("Invalid format. Please provide details in the format: cardNumber amount");
                }
                userStates.remove(chatId);
                break;
        }
    }

    public void callbackHandler(Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        String callbackData = callbackQuery.getData();
        Long callbackChatId = callbackQuery.getMessage().getChatId();
        SendMessage message = new SendMessage();
        message.setChatId(callbackChatId);

        switch (callbackData) {
            case "show_cards":
                User user = getUserByChatId(callbackChatId);
                if (user != null) {
                    StringBuilder cardsInfo = new StringBuilder("Your cards:\n");
                    List<Card> cards = cardService.getCardsByUserId(user.getId());
                    for (Card card : cards) {
                        cardsInfo.append("Card Number: ").append(card.getCardNumber()).append("\n");
                    }
                    message.setText(cardsInfo.toString());
                } else {
                    message.setText("User not found.");
                }
                break;
            case "add_new_card":
                message.setText("Please enter the card number.");
                userStates.put(callbackChatId, "waiting_for_card_number");
                break;
            case "transfer":
                message.setText("Please enter the card number to transfer from, card number to transfer to, and amount in the format: fromCardNumber toCardNumber amount");
                userStates.put(callbackChatId, "waiting_for_transfer_details");
                break;
            case "history":
                message.setText("Please enter the card number to view history:");
                userStates.put(callbackChatId, "waiting_for_history_card_number");
                break;
            case "deposite":
                message.setText("Please enter the card number to deposit into and amount in the format: cardNumber amount");
                userStates.put(callbackChatId, "waiting_for_deposit_details");
                break;
            default:
                message.setText("Unknown callback!");
        }

        myBot.smsSender(message);
    }

    private boolean userExists(Long chatId) {
        String sql = "SELECT COUNT(*) FROM users WHERE id = ?";
        try (Connection conn = TestConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void addUser(Long chatId) {
        String sql = "INSERT INTO users (id, name) VALUES (?, ?)";
        try (Connection conn = TestConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, "User " + chatId); // Placeholder name
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private User getUserByChatId(Long chatId) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = TestConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new User(rs.getLong("id"), rs.getString("name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private long getCardIdByNumber(String cardNumber) {
        String sql = "SELECT id FROM card WHERE card_number = ?";
        try (Connection conn = TestConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, cardNumber);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    static BotService botService;

    public static BotService getBotService() {
        if (botService == null) botService = new BotService();
        return botService;
    }
}
