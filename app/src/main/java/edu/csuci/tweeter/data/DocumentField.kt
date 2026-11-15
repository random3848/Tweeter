package edu.csuci.tweeter.data

/**
 * A field stored in a Firestore document, such as [PostDocument.Field] or
 * [ProfileDocument.Field].
 *
 * A document's fields are an `enum`, read into an array whose indices are their ordinals.
 */
interface DocumentField {
    /** The field's name in the document. */
    val key: String
}
