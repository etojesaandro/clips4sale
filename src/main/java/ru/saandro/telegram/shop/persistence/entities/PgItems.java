package ru.saandro.telegram.shop.persistence.entities;

import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.logging.Level;

import javax.sql.DataSource;

import com.google.common.collect.ImmutableList;
import com.jcabi.jdbc.JdbcSession;
import com.jcabi.jdbc.ListOutcome;

import ru.saandro.telegram.shop.controller.EnumWithDescription;
import ru.saandro.telegram.shop.controller.VideoGenres;
import ru.saandro.telegram.shop.core.*;
import ru.saandro.telegram.shop.logger.SimpleTelegramLogger;

public class PgItems implements Items {

    private final ShopBot provider;
    private final SimpleTelegramLogger logger;

    public PgItems(ShopBot provider, SimpleTelegramLogger logger)
    {
        this.provider = provider;
        this.logger = logger;
    }

    @Override
    public Iterable<Item> getPurchasedItemsByUser(long userId) {
        try {

            return new JdbcSession(provider.getSource())
                    .sql("SELECT i.id, i.title, i.description, i.author, g.name, i.price, i.preview_path, i.content_path FROM item i " +
                            "JOIN item_by_bot_user iu" +
                            "ON iu.item_id = i.id " +
                            "JOIN BOT_USER u" +
                            "ON u.uid = iu.id" +
                            "WHERE uid = ?")
                    .set(userId)
                    .select(
                            new ListOutcome<>(
                                    rset -> new PgItem(provider, rset.getLong(1),
                                            rset.getString(2),
                                            rset.getString(3),
                                            rset.getString(4),
                                            EnumWithDescription.parse(rset.getString(5), VideoGenres.class).orElse(VideoGenres.ALL),
                                            rset.getInt(6),
                                            Paths.get(rset.getString(7)),
                                            Paths.get(rset.getString(8)))
                            )
                    );
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Unable to browse the videos", e);
            return ImmutableList.of();
        }
    }

    @Override
    public Iterable<Item> browseItemsByGenre(VideoGenres genre) {
        try {
            String suffix = "";
            if (genre != VideoGenres.ALL) {
                suffix = "WHERE g.name = ?";
            }
            JdbcSession sql = new JdbcSession(provider.getSource())
                    .sql("SELECT i.id, i.title, i.description, g.name, i.price, i.preview_path, i.content_path FROM item i " +
                            "JOIN item_by_genre ig" +
                            "ON ig.item_id = i.id " +
                            "JOIN genre g" +
                            "ON g.id = ig.id" +
                            suffix);
            if (genre != VideoGenres.ALL) {
                sql.set(genre.getName());
            }
            return sql.select(
                    new ListOutcome<>(
                            rset -> new PgItem(provider, rset.getLong(1),
                                    rset.getString(2),
                                    rset.getString(3),
                                    rset.getString(4),
                                    EnumWithDescription.parse(rset.getString(5), VideoGenres.class).orElse(VideoGenres.ALL),
                                    rset.getInt(6),
                                    Paths.get(rset.getString(7)),
                                            Paths.get(rset.getString(8)))
                    )
            );
        } catch (Exception e) {
            logger.log(Level.WARNING, "Unable to browse the videos", e);
            return ImmutableList.of();
        }
    }
}