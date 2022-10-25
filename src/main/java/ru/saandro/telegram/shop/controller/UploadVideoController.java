package ru.saandro.telegram.shop.controller;

import static ru.saandro.telegram.shop.controller.UploadState.CONFIRMATION;
import static ru.saandro.telegram.shop.controller.UploadState.CONTENT;
import static ru.saandro.telegram.shop.controller.UploadState.DESCRIPTION;
import static ru.saandro.telegram.shop.controller.UploadState.DONE;
import static ru.saandro.telegram.shop.controller.UploadState.GENRE;
import static ru.saandro.telegram.shop.controller.UploadState.PREVIEW;
import static ru.saandro.telegram.shop.controller.UploadState.PRICE;
import static ru.saandro.telegram.shop.controller.UploadState.TITLE;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.File;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.Video;
import com.pengrad.telegrambot.request.GetFile;
import com.pengrad.telegrambot.response.GetFileResponse;

import ru.saandro.telegram.shop.core.ShopBot;
import ru.saandro.telegram.shop.session.UserSession;

public class UploadVideoController extends AbstractScreenController {

    // https://api.telegram.org/file/bot<token>/<file_path>
    private static final String FILE_ADDRESS = "https://api.telegram.org/file/bot";

    private volatile UploadState uploadState = TITLE;

    private final ThickItemBuilder itemBuilder;

    public UploadVideoController(ShopBot bot, UserSession session, Long chatId) {
        super(bot, session, chatId);
        itemBuilder = new ThickItemBuilder(bot.getConfiguration());
    }


    @Override
    public void processCallback(CallbackQuery callbackQuery) {

        if (uploadState == GENRE) {
            String data = callbackQuery.data();
            itemBuilder.genre(EnumWithDescription.parse(data, VideoGenres.class).orElse(VideoGenres.ALL));
            uploadState = PRICE;
            prepareAndSendMenu("Введите стоимость видео.");
        }
    }

    @Override
    public void processMessage(Message message) {
        switch (uploadState) {
            case TITLE -> {
                itemBuilder.title(message.text());
                uploadState = DESCRIPTION;
                prepareAndSendMenu("Введите описание видео.");
            }
            case DESCRIPTION -> {
                itemBuilder.description(message.text());
                uploadState = GENRE;
                prepareAndSendMenu("Выберите жанр видео.", VideoGenres.class);
            }

            case PRICE -> {
                String priceString = message.text();
                try {
                    int price = Integer.parseInt(priceString);
                    itemBuilder.price(price);
                    uploadState = PREVIEW;
                    prepareAndSendMenu("Загрузите превью файл в виде изображения или видео.");
                } catch (NumberFormatException e) {
                    prepareAndSendMenu("Некорректная стоимость.");
                }

            }
            case PREVIEW -> {
                try {
                    ContentFile preview = tryToReadFile(message);
                    if (preview.isInvalid()) {
                        return;
                    }
                    itemBuilder.preview(preview);
                } catch (IOException e) {
                    prepareAndSendMenu("Произошла ошибка. Повторите позднее... Ну или напишите мне, что я облажался хд.");
                    bot.getLogger().log(Level.WARNING, "Preview loading error", e);
                    return;
                }
                uploadState = CONTENT;
                prepareAndSendMenu("Загрузите контент.");
            }
            case CONTENT -> {
                try {
                    ContentFile content = tryToReadFile(message);
                    if (content.isInvalid()) {
                        return;
                    }
                    itemBuilder.content(content);
                } catch (IOException e) {
                    prepareAndSendMenu("Произошла ошибка. Повторите позднее... Ну или напишите мне, что я облажался хд.");
                    bot.getLogger().log(Level.WARNING, "Content loading error", e);
                    return;
                }

                uploadState = CONFIRMATION;
                prepareAndSendMenu("Введите Да/Нет для подтверждения/отмены загрузки.");
            }
            case CONFIRMATION -> {
                uploadState = DONE;
                if (isConfirmed(message.text())) {
                    try {
                        itemBuilder.build(bot.getDataSource(), session.getUser().name(), bot.getLogger()).store();
                        prepareAndSendMenu("Видео успешно загружено и доступно для покупки!");
                    } catch (IOException e) {
                        prepareAndSendMenu("Произошла ошибка. Повторите позднее... Ну или напишите мне, что я облажался хд.");
                        bot.getLogger().log(Level.WARNING, "Storage error", e);
                    }
                } else {
                    prepareAndSendMenu("Загрузка отменена.");
                }
            }
            case DONE -> {
                uploadState = DONE;

            }
        }
    }

    private ContentFile tryToReadFile(Message message) throws IOException {
        GetFile getFile = null;
        Video video = message.video();
        if (message.photo() != null) {
            PhotoSize[] photo = message.photo();
            getFile = new GetFile(photo[0].fileId());
        } else if (message.video() != null) {
            getFile = new GetFile(video.fileId());
        } else {
            prepareAndSendMenu("Некорректный файл.");
            return ContentFile.INVALID;
        }

        GetFileResponse execute = bot.execute(getFile);
        File file = execute.file();
        String fileUrl = FILE_ADDRESS + bot.getToken() + "/" + file.filePath();
        byte[] bytes = downloadFile(fileUrl, file.fileId(), file.fileSize());
        return new ContentFile(file, bytes);
    }

    private byte[] downloadFile(String address, String fileName, long fileSize) throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(new URL(address).openStream());
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream((int) fileSize)) {
            ByteStreams.copy(in, byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        }
    }


    private boolean isConfirmed(String text) {
        return "Да".equalsIgnoreCase(text) || "Y".equalsIgnoreCase(text) || "Yes".equalsIgnoreCase(text);
    }

    @Override
    public void onStart() {
        uploadState = TITLE;
        prepareAndSendMenu("Введите название видео.");
    }
}
