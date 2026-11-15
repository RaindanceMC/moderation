package de.raindancer.modules.moderation.model;

/** One thing a new account did that bots do. None of them alone says much; together they do. */
public enum BotSignal {
    SHARED_NETWORK("joined with other new accounts from one network"),
    SHARED_NAMES("joined with other new accounts with similar names"),
    STILL("did not move or look around"),
    SPAM("spammed the chat"),
    NO_BRAND("sent no client brand"),
    BOT_BRAND("joined with a bot client");

    private final String words;

    BotSignal(String words) {
        this.words = words;
    }

    /** What staff read. */
    public String words() {
        return words;
    }
}
