package edu.csuci.tweeter.data

/**
 * Why a user reports a post or another user. Firestore stores the constant's name, which
 * firestore.rules lists, so renaming a constant also changes the rules.
 */
enum class ReportReason {
    /** Advertising, scams or repeated unwanted posts. */
    SPAM,

    /** Bullying, threats or hate aimed at someone. */
    HARASSMENT,

    /** Nudity or sexual content. */
    SEXUAL_CONTENT,

    /** Violence or cruelty, including toward animals. */
    VIOLENCE,

    /** Anything else, explained in the report's details. */
    OTHER
}
