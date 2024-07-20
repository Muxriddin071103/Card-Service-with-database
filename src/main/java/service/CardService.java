package service;

import connection.TestConnection;
import entities.Card;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CardService {
    private static CardService cardService;

    private CardService() {
    }

    public static CardService getInstance() {
        if (cardService == null) {
            cardService = new CardService();
        }
        return cardService;
    }

    public void addCard(Card card) {
        String sql = "INSERT INTO card (card_number, user_id, balance) VALUES (?, ?, ?)";
        try (Connection conn = TestConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, card.getCardNumber());
            pstmt.setLong(2, card.getUserId());
            pstmt.setDouble(3, card.getBalance());
            pstmt.executeUpdate();
            System.out.println("Card added successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Card> getCardsByUserId(Long userId) {
        List<Card> cards = new ArrayList<>();
        String sql = "SELECT * FROM card WHERE user_id = ?";
        try (Connection conn = TestConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                long id = rs.getLong("id");
                String cardNumber = rs.getString("card_number");
                double balance = rs.getDouble("balance");
                cards.add(new Card(id, cardNumber, userId, balance));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return cards;
    }

    public void transfer(int fromCardId, int toCardId, double amount) {
        String sqlCheckBalance = "SELECT balance FROM card WHERE id = ?";
        String sqlTransfer = "INSERT INTO transfer (from_card, to_card) VALUES (?, ?)";
        String sqlUpdateFromCard = "UPDATE card SET balance = balance - ? WHERE id = ?";
        String sqlUpdateToCard = "UPDATE card SET balance = balance + ? WHERE id = ?";

        try (Connection conn = TestConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);

            double fromCardBalance;
            try (PreparedStatement pstmtCheckBalance = conn.prepareStatement(sqlCheckBalance)) {
                pstmtCheckBalance.setInt(1, fromCardId);
                ResultSet rs = pstmtCheckBalance.executeQuery();
                if (rs.next()) {
                    fromCardBalance = rs.getDouble("balance");
                } else {
                    throw new SQLException("From card not found");
                }
            }

            if (amount > fromCardBalance) {
                System.out.println("Transfer cannot complete. Amount of money in card is small");
                conn.rollback();
                return;
            }

            try (PreparedStatement pstmtTransfer = conn.prepareStatement(sqlTransfer);
                 PreparedStatement pstmtUpdateFromCard = conn.prepareStatement(sqlUpdateFromCard);
                 PreparedStatement pstmtUpdateToCard = conn.prepareStatement(sqlUpdateToCard)) {

                pstmtTransfer.setInt(1, fromCardId);
                pstmtTransfer.setInt(2, toCardId);
                pstmtTransfer.executeUpdate();

                pstmtUpdateFromCard.setDouble(1, amount);
                pstmtUpdateFromCard.setInt(2, fromCardId);
                pstmtUpdateFromCard.executeUpdate();

                pstmtUpdateToCard.setDouble(1, amount);
                pstmtUpdateToCard.setInt(2, toCardId);
                pstmtUpdateToCard.executeUpdate();

                conn.commit();
                System.out.println("Transfer completed successfully.");
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deposite(int cardId, double amount) {
        String sql = "UPDATE card SET balance = balance + ? WHERE id = ?";
        try (Connection conn = TestConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, amount);
            pstmt.setInt(2, cardId);
            pstmt.executeUpdate();
            System.out.println("Deposit completed successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> getHistory(int cardId) {
        List<String> history = new ArrayList<>();
        String sql = "SELECT * FROM transfer WHERE from_card = ? OR to_card = ?";
        try (Connection conn = TestConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, cardId);
            pstmt.setInt(2, cardId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id");
                int fromCard = rs.getInt("from_card");
                int toCard = rs.getInt("to_card");
                history.add("Transfer ID: " + id + ", From Card: " + fromCard + ", To Card: " + toCard);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return history;
    }
}
