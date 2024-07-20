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

    public List<Card> getCardsByUserId(long userId) {
        List<Card> cards = new ArrayList<>();
        String sql = "SELECT * FROM card WHERE user_id = ? ORDER BY id";
        try (Connection conn = TestConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                cards.add(new Card(
                        rs.getLong("id"),
                        rs.getString("card_number"),
                        rs.getLong("user_id"),
                        rs.getDouble("balance")
                ));
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
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> getHistory(long cardId) {
        List<String> history = new ArrayList<>();
        String sql = "SELECT * FROM transfer WHERE from_card = ?";
        try (Connection conn = TestConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, cardId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                history.add("Transfer ID: " + rs.getLong("id") +
                        ", From Card: " + rs.getLong("from_card") +
                        ", To Card: " + rs.getLong("to_card"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return history;
    }
}
