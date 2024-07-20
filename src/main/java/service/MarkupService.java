package service;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

public class MarkupService {

    public ReplyKeyboardMarkup getMarkup(String[][] buttons){
        List<KeyboardRow> rows = new ArrayList<>();
        ReplyKeyboardMarkup board = new ReplyKeyboardMarkup();
        board.setResizeKeyboard(true);
        board.setSelective(true);
        board.setOneTimeKeyboard(true);
        for (String[] knopka : buttons) {
            KeyboardRow row = new KeyboardRow();
            for (String s : knopka) {
                KeyboardButton button = new KeyboardButton(s);
                row.add(button);
            }
            rows.add(row);
        }
        board.setKeyboard(rows);
        return board;
    }

    static MarkupService markupService;

    public static MarkupService getMarkupService(){
        if (markupService == null) markupService = new MarkupService();
        return markupService;
    }
}
