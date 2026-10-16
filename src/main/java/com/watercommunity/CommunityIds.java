package com.watercommunity;

import java.security.SecureRandom;

/**
 * Business ids are 16 characters: a one-letter type prefix followed by 15 lowercase base36 characters.
 * Migrated rows keep their old numeric id zero-padded, for example {@code u000000000000001}.
 */
final class CommunityIds {
    static final int LENGTH = 16;

    static final char USER = 'u';
    static final char ASSET = 'a';
    static final char PROJECT = 'p';
    static final char LIKE = 'l';
    static final char COMMENT = 'c';
    static final char FOLLOW = 'f';
    static final char SHARE = 's';

    private static final char[] ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private CommunityIds() {
    }

    static String next(char prefix) {
        char[] id = new char[LENGTH];
        id[0] = prefix;
        for (int i = 1; i < LENGTH; i++) {
            id[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(id);
    }
}
