package service;

import lombok.SneakyThrows;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

public class MyBot extends TelegramLongPollingBot {

    @Override
    public void onUpdateReceived(Update update) {
        BotService botService = BotService.getBotService();

            if (update.hasMessage()){
                botService.messageHandler(update);
            }
            else if (update.hasCallbackQuery()){
                botService.callbackHandler(update);
            }
    }
    @SneakyThrows
    public void smsSender(SendMessage message){
        execute(message);
    }

    @Override
    public String getBotUsername() {
        return "https://t.me/module6_examBot";
    }

    @Override
    public String getBotToken() {
        return "7460626901:AAGMtwr0dR9iDKbFGmjTcxOzNVadZJd5rcQ";
    }

    static MyBot myBot;
    public static MyBot getMyBot(){
        if (myBot == null) myBot = new MyBot();
        return myBot;
    }
}
