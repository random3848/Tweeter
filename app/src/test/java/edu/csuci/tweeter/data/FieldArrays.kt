package edu.csuci.tweeter.data

// The [values] Firestore reads into the array for these fields, indexed like the fields.
internal fun List<DocumentField>.readFrom(values: Map<String, Any?>): Array<Any?> =
    Array(size) { values[this[it].key] }
