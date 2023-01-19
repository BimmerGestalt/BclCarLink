package io.bimmergestalt.bclclient.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat
import io.bimmergestalt.bclclient.R

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SettingsFragment : PreferenceFragmentCompat() {
	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		setPreferencesFromResource(R.xml.preferences, rootKey)
	}
}