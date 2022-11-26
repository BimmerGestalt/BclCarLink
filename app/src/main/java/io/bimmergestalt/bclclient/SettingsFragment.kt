package io.bimmergestalt.bclclient

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SettingsFragment : PreferenceFragmentCompat() {
	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		setPreferencesFromResource(R.xml.preferences, rootKey)
	}
}