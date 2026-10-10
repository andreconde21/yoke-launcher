package com.outsmartis.yoke.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import com.outsmartis.yoke.R
import com.outsmartis.yoke.backup.BackupException
import com.outsmartis.yoke.backup.VaultBackup
import com.outsmartis.yoke.backup.YokeBackup
import com.outsmartis.yoke.cockpit.CockpitPrefs
import com.outsmartis.yoke.databinding.FragmentBackupBinding
import com.outsmartis.yoke.helper.YokeDialog
import com.outsmartis.yoke.helper.createDialog
import com.outsmartis.yoke.helper.showToast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Export, import and the optional vault copy of Yoke's settings. */
class BackupFragment : BaseFragment() {

    private var _binding: FragmentBackupBinding? = null
    private val binding get() = _binding!!
    private var dialog: YokeDialog? = null

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) exportTo(uri)
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmImport { requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBackupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        binding.backupExport.setOnClickListener {
            exportLauncher.launch("yoke-backup-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.json")
        }
        binding.backupImport.setOnClickListener { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
        binding.backupKeepCopy.setOnClickListener {
            if (VaultBackup.keepCopy(ctx)) {
                VaultBackup.setKeepCopy(ctx, false)
            } else if (CockpitPrefs(ctx).vault(ctx) == null) {
                ctx.showToast(R.string.search_needs_vault)
            } else {
                VaultBackup.setKeepCopy(ctx, true)
                Thread {
                    val ok = VaultBackup.write(ctx.applicationContext)
                    activity?.runOnUiThread { ctx.showToast(if (ok) R.string.backup_copy_written else R.string.backup_copy_failed) }
                }.start()
            }
            refresh()
        }
        binding.backupRestoreVault.setOnClickListener {
            if (CockpitPrefs(ctx).vault(ctx) == null) {
                ctx.showToast(R.string.search_needs_vault)
                return@setOnClickListener
            }
            confirmImport { VaultBackup.read(ctx) }
        }
        refresh()
    }

    private fun refresh() {
        binding.backupKeepCopy.setText(if (VaultBackup.keepCopy(requireContext())) R.string.on else R.string.off)
    }

    private fun exportTo(uri: Uri) {
        val ctx = requireContext()
        val ok = runCatching {
            ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(YokeBackup.export(ctx).toByteArray(Charsets.UTF_8)) } != null
        }.getOrDefault(false)
        ctx.showToast(if (ok) R.string.backup_exported else R.string.backup_export_failed)
    }

    /** Asks first, then reads the document with [read] (off the main thread) and applies it. */
    private fun confirmImport(read: () -> String?) {
        val ctx = requireContext()
        dialog?.dismiss()
        dialog = ctx.createDialog(
            title = R.string.backup_title,
            action = R.string.backup_restore,
            message = R.string.backup_import_confirm,
            onAction = {
                Thread {
                    val result = runCatching {
                        val text = read() ?: throw BackupException(getString(R.string.backup_no_vault_copy))
                        YokeBackup.import(ctx.applicationContext, text)
                    }
                    activity?.runOnUiThread {
                        val error = result.exceptionOrNull()
                        if (error == null) {
                            ctx.showToast(R.string.backup_imported)
                            activity?.recreate()
                        } else {
                            ctx.showToast(getString(R.string.backup_import_failed, error.message ?: "?"))
                        }
                    }
                }.start()
            },
        ).also { it.showRespectingStatusBar() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dialog?.dismiss()
        dialog = null
        _binding = null
    }
}
