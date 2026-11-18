package edu.csuci.tweeter.data

import com.google.firebase.firestore.FieldPath

/**
 * Returns the Firestore path of each of [fields], in order, for reading a document's fields into
 * an array indexed like [fields].
 */
internal fun fieldPathsOf(fields: List<DocumentField>): Array<FieldPath> =
    Array(fields.size) { FieldPath.of(fields[it].key) }
