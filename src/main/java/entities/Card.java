package entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class Card {
    private Long id;
    private String cardNumber;
    private Long userId;
    private double balance;
}
