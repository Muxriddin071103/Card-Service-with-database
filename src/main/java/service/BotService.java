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
    private final Map<Long, String[]> transferDetails = new HashMap<>();
    private final Map<Long, String[]> depositDetails = new HashMap<>();

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
                    message.setReplyMarkup(markupService.getMarkup(new String[][]{
                            {"My Cards", "Add Card"},
                            {"Transfer", "Deposit"},
                            {"History"}
                    }));
                    break;
                case "My Cards":
                    User user = getUserByChatId(chatId);
                    if (user != null) {
                        List<Card> cards = cardService.getCardsByUserId(user.getId());
                        StringBuilder cardsInfo = new StringBuilder("Your cards:\n");
                        for (Card card : cards) {
                            cardsInfo.append("Card ID: ").append(card.getId())
                                    .append(") Card Number: ").append(card.getCardNumber())
                                    .append(", Card Balance: $").append(card.getBalance())
                                    .append("\n");
                        }
                        message.setText(cardsInfo.toString());
                    } else {
                        message.setText("User not found.");
                    }
                    break;
                case "Add Card":
                    message.setText("Please enter the card number.");
                    userStates.put(chatId, "waiting_for_card_number");
                    break;
                case "Transfer":
                    message.setText("Please select the card number to transfer from.");
                    userStates.put(chatId, "waiting_for_from_card");
                    showCardSelection(message, chatId, "transfer");
                    break;
                case "History":
                    message.setText("Please select the card number to view history:");
                    userStates.put(chatId, "waiting_for_history_card_number");
                    showCardSelection(message, chatId, "history");
                    break;
                case "Deposit":
                    message.setText("Please select the card number to deposit into.");
                    userStates.put(chatId, "waiting_for_deposit_card_number");
                    showCardSelection(message, chatId, "deposit");
                    break;
                default:
                    message.setText("Unknown command!");
            }

            myBot.smsSender(message);
        }
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
                    sendMainMenu(message);
                } else {
                    message.setText("Invalid card number. Please enter a valid 16-digit card number.");
                }
                break;

            case "waiting_for_from_card":
                if (messageText.matches("\\d{16}")) {
                    transferDetails.put(chatId, new String[]{messageText, null, null});
                    message.setText("Please enter the card number to transfer to.");
                    userStates.put(chatId, "waiting_for_to_card");
                } else {
                    message.setText("Invalid card number. Please enter a valid 16-digit card number.");
                }
                break;

            case "waiting_for_to_card":
                if (messageText.matches("\\d{16}")) {
                    String[] details = transferDetails.get(chatId);
                    details[1] = messageText;
                    message.setText("Please enter the amount to transfer.");
                    userStates.put(chatId, "waiting_for_amount");
                } else {
                    message.setText("Invalid card number. Please enter a valid 16-digit card number.");
                }
                break;

            case "waiting_for_amount":
                try {
                    double amount = Double.parseDouble(messageText);
                    String[] details = transferDetails.get(chatId);
                    cardService.transfer(
                            (int) getCardIdByNumber(details[0]),
                            (int) getCardIdByNumber(details[1]),
                            amount
                    );
                    message.setText("Transfer completed successfully.");
                    userStates.remove(chatId);
                    transferDetails.remove(chatId);
                    sendMainMenu(message);
                } catch (NumberFormatException e) {
                    message.setText("Invalid amount. Please enter a numeric value.");
                }
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
                    userStates.remove(chatId);
                    sendMainMenu(message);
                } else {
                    message.setText("Invalid card number. Please enter a valid 16-digit card number.");
                }
                break;

            case "waiting_for_deposit_card_number":
                if (messageText.matches("\\d{16}")) {
                    depositDetails.put(chatId, new String[]{messageText, null});
                    message.setText("Please enter the amount to deposit.");
                    userStates.put(chatId, "waiting_for_deposit_amount");
                } else {
                    message.setText("Invalid card number. Please enter a valid 16-digit card number.");
                }
                break;

            case "waiting_for_deposit_amount":
                try {
                    double amount = Double.parseDouble(messageText);
                    String[] details = depositDetails.get(chatId);
                    cardService.deposite(
                            (int) getCardIdByNumber(details[0]),
                            amount
                    );
                    message.setText("Deposit completed successfully.");
                    userStates.remove(chatId);
                    depositDetails.remove(chatId);
                    sendMainMenu(message);
                } catch (NumberFormatException e) {
                    message.setText("Invalid amount. Please enter a numeric value.");
                }
                break;

            default:
                message.setText("Unknown state. Please try again.");
        }

        myBot.smsSender(message);
    }

    private void showCardSelection(SendMessage message, Long chatId, String operation) {
        User user = getUserByChatId(chatId);
        if (user != null) {
            List<Card> cards = cardService.getCardsByUserId(user.getId());
            String[][] buttons = new String[cards.size()][1];
            for (int i = 0; i < cards.size(); i++) {
                buttons[i][0] = cards.get(i).getCardNumber();
            }
            message.setReplyMarkup(markupService.getMarkup(buttons));
        } else {
            message.setText("User not found.");
        }
    }

    private void sendMainMenu(SendMessage message) {
        message.setReplyMarkup(markupService.getMarkup(new String[][]{
                {"My Cards", "Add Card"},
                {"Transfer", "Deposit"},
                {"History"}
        }));
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
                    List<Card> cards = cardService.getCardsByUserId(user.getId());
                    StringBuilder cardsInfo = new StringBuilder("Your cards:\n");
                    for (Card card : cards) {
                        cardsInfo.append("Card Number: ").append(card.getCardNumber())
                                .append(", Balance: $").append(card.getBalance())
                                .append("\n");
                    }
                    message.setText(cardsInfo.toString());
                } else {
                    message.setText("User not found.");
                }
                break;

            default:
                message.setText("Unknown callback data!");
        }

        myBot.smsSender(message);
    }

    private boolean userExists(Long chatId) {
        String sql = "SELECT 1 FROM users WHERE id = ?";
        try (Connection conn = TestConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
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
            pstmt.setString(2, "User" + chatId);
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

    private static BotService botService;

    public static BotService getBotService() {
        if (botService == null) botService = new BotService();
        return botService;
    }
}
