package edu.csuci.tweeter.ui.voting

import edu.csuci.tweeter.data.FULL_AGREEMENT

/** How far the votes on a post's animal have identified it, which decides its name control. */
enum class Identification {
    /**
     * The post shows no animal: its poster didn't know what it is, and no animal has a majority of
     * the votes yet. The name control asks "What animal is this?".
     */
    UNKNOWN,

    /** Every vote names the animal the post shows. */
    AGREED,

    /** Some votes name another animal than the one the post shows. */
    DISPUTED;

    /** Whether the post shows an animal, which a vote can agree with. */
    val hasShownAnimal: Boolean
        get() = this != UNKNOWN

    /** Finds [Identification]s. */
    companion object {
        /**
         * Returns how far a post is identified that shows [shownAnimal]
         * ([edu.csuci.tweeter.data.shownAnimalOf]), blank for none, with [agreement]
         * ([edu.csuci.tweeter.data.agreementOf]).
         */
        fun of(shownAnimal: String, agreement: Double): Identification = when {
            shownAnimal.isBlank() -> UNKNOWN
            agreement == FULL_AGREEMENT -> AGREED
            else -> DISPUTED
        }
    }
}
