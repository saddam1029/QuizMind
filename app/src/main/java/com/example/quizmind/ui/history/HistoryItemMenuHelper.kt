package com.example.quizmind.ui.history

import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.quizmind.R
import com.example.quizmind.data.mapper.toQuizResultSummary
import com.example.quizmind.data.model.HistoryItem
import com.example.quizmind.utils.ResultImageGenerator
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Shared history overflow menu used by MainActivity and HistoryActivity.
 */
class HistoryItemMenuHelper(
    private val activity: AppCompatActivity,
    private val callbacks: Callbacks
) {

    interface Callbacks {
        fun onDownload(item: HistoryItem, onDone: (Result<Unit>) -> Unit)
        fun onRename(item: HistoryItem, newTitle: String, onDone: (Result<Unit>) -> Unit)
        fun onDelete(item: HistoryItem, onDone: (Result<Unit>) -> Unit)
        fun onOpenResult(item: HistoryItem)
        fun onPlay(item: HistoryItem)
    }

    fun show(item: HistoryItem) {
        val dialog = BottomSheetDialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_history_menu, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.tvMenuTitle).text = item.title
        view.findViewById<TextView>(R.id.tvMenuSubtitle).text = if (item.isCompleted) {
            "${item.totalQuestions} Questions · ${item.difficulty.ifBlank { "Mixed" }} · Latest score: ${item.finalScore}/${item.maxScore}"
        } else {
            "${item.totalQuestions} Questions · ${item.difficulty.ifBlank { "Mixed" }} · Downloaded"
        }

        val btnDownload = view.findViewById<View>(R.id.btnDownload)
        val tvDownloadLabel = view.findViewById<TextView>(R.id.tvDownloadLabel)
        val tvDownloadMeta = view.findViewById<TextView>(R.id.tvDownloadMeta)
        val btnGiveAgain = view.findViewById<View>(R.id.btnGiveAgain)
        val btnReview = view.findViewById<View>(R.id.btnReview)
        val btnShare = view.findViewById<View>(R.id.btnShare)

        if (item.isDownloaded) {
            tvDownloadLabel.text = "Already Downloaded"
            tvDownloadMeta.text = "Saved"
            btnDownload.isEnabled = false
            btnDownload.alpha = 0.5f
        }

        view.findViewById<View>(R.id.btnCloseMenu).setOnClickListener { dialog.dismiss() }

        if (item.isCompleted) {
            btnReview.visibility = View.VISIBLE
            btnShare.visibility = View.VISIBLE
            btnReview.setOnClickListener {
                dialog.dismiss()
                callbacks.onOpenResult(item)
            }
        } else {
            btnReview.visibility = View.GONE
            btnShare.visibility = View.GONE
        }

        btnGiveAgain.setOnClickListener {
            dialog.dismiss()
            callbacks.onPlay(item)
        }

        btnDownload.setOnClickListener {
            dialog.dismiss()
            callbacks.onDownload(item) { result ->
                result.onSuccess {
                    Toast.makeText(activity, "Quiz downloaded for offline play.", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(activity, it.localizedMessage ?: "Download failed.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnShare.setOnClickListener {
            dialog.dismiss()
            if (!item.isCompleted) return@setOnClickListener
            try {
                ResultImageGenerator.shareResult(activity, item.toQuizResultSummary())
            } catch (e: Exception) {
                Toast.makeText(activity, e.localizedMessage ?: "Share failed.", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.btnRename).setOnClickListener {
            dialog.dismiss()
            showRenameDialog(item)
        }

        view.findViewById<View>(R.id.btnDelete).setOnClickListener {
            dialog.dismiss()
            showDeleteDialog(item)
        }

        dialog.show()
    }

    private fun showRenameDialog(item: HistoryItem) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_rename_history, null)
        val input = view.findViewById<EditText>(R.id.etRename)
        input.setText(item.title)
        input.setSelection(input.text.length)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(view)
            .create()

        view.findViewById<TextView>(R.id.btnCancelRename).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.btnSaveRename).setOnClickListener {
            dialog.dismiss()
            callbacks.onRename(item, input.text.toString()) { result ->
                result.onSuccess {
                    Toast.makeText(activity, "Quiz renamed.", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(activity, it.localizedMessage ?: "Rename failed.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun showDeleteDialog(item: HistoryItem) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_delete_history, null)
        view.findViewById<TextView>(R.id.tvDeleteMessage).text =
            "${item.title} and its result will be permanently removed."

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(view)
            .create()

        view.findViewById<TextView>(R.id.btnCancelDelete).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.btnConfirmDelete).setOnClickListener {
            dialog.dismiss()
            callbacks.onDelete(item) { result ->
                result.onSuccess {
                    Toast.makeText(activity, "History item deleted.", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(activity, it.localizedMessage ?: "Delete failed.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }
}
