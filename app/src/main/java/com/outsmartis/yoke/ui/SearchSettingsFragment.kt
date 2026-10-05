package com.outsmartis.yoke.ui

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import com.outsmartis.yoke.R
import com.outsmartis.yoke.cockpit.CockpitPrefs
import com.outsmartis.yoke.databinding.FragmentSearchSettingsBinding
import com.outsmartis.yoke.helper.showToast
import com.outsmartis.yoke.palette.ExtraSearch
import com.outsmartis.yoke.palette.SearchPrefs

/** Which extra sources the search box looks through. */
class SearchSettingsFragment : BaseFragment() {

    private lateinit var prefs: SearchPrefs
    private var _binding: FragmentSearchSettingsBinding? = null
    private val binding get() = _binding!!

    private val contactsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) prefs.contacts = true else requireContext().showToast(R.string.search_contacts_denied)
        refresh()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = SearchPrefs(requireContext())
        binding.searchCards.setOnClickListener { prefs.cards = !prefs.cards; refresh() }
        binding.searchNotes.setOnClickListener { prefs.notes = !prefs.notes; refresh() }
        binding.searchSettings.setOnClickListener { prefs.settings = !prefs.settings; refresh() }
        binding.searchContacts.setOnClickListener {
            when {
                prefs.contacts -> { prefs.contacts = false; refresh() }
                ExtraSearch.hasContactsPermission(requireContext()) -> { prefs.contacts = true; refresh() }
                else -> contactsPermission.launch(Manifest.permission.READ_CONTACTS)
            }
        }
        refresh()
    }

    private fun refresh() {
        fun on(b: Boolean) = getString(if (b) R.string.on else R.string.off)
        binding.searchCards.text = on(prefs.cards)
        binding.searchNotes.text = on(prefs.notes)
        binding.searchContacts.text = on(prefs.contacts && ExtraSearch.hasContactsPermission(requireContext()))
        binding.searchSettings.text = on(prefs.settings)
        val noVault = (prefs.cards || prefs.notes) && CockpitPrefs(requireContext()).vault(requireContext()) == null
        binding.status.text = if (noVault) getString(R.string.search_needs_vault) else ""
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
