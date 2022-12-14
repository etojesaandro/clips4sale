package ru.saandro.telegram.shop.controller;

import java.util.Locale;
import java.util.Optional;

public enum VideoGenres implements EnumWithDescription {
    ALL("Все"),
    FOOT("Жесть"),
    SCARFING("Полная жесть"),
    BACK("Назад");


    public final String name;
    public final String descr;

    VideoGenres(String descr) {
        this.name = name().toLowerCase(Locale.ROOT);
        this.descr = descr;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return descr;
    }

    @Override
    public boolean isAdmin() {
        return false;
    }
}
