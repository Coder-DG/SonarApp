package com.dginzbourg.sonarapp

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface

fun getAlertDialog(
    activity: Activity,
    message: String,
    title: String,
    posButtonText: String,
    posFunction: (dialog: DialogInterface, id: Int) -> Unit,
    negButtonText: String? = null,
    negFunction: (dialog: DialogInterface, id: Int) -> Unit = { _, _ -> }
): AlertDialog? {
    val builder: AlertDialog.Builder = AlertDialog.Builder(activity)

    builder.setMessage(message)
        .setTitle(title)
        .setCancelable(false)
        .setPositiveButton(posButtonText) { dialog, id -> posFunction(dialog, id) }
    if (negButtonText != null) builder.setNegativeButton(negButtonText) { dialog, id -> negFunction(dialog, id) }
    return builder.create()
}