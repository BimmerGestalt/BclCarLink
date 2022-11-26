package io.bimmergestalt.bclclient

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import io.bimmergestalt.bcl.android.ConnectionStateLiveData
import io.bimmergestalt.bclclient.databinding.FragmentConnectionStatusBinding
import io.bimmergestalt.bclclient.models.ConnectionStatusViewModel

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class ConnectionStatusFragment : Fragment() {

	private var _binding: FragmentConnectionStatusBinding? = null
	val viewModel by activityViewModels<ConnectionStatusViewModel>()

	// This property is only valid between onCreateView and
	// onDestroyView.
	private val binding get() = _binding!!

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {

		val binding = FragmentConnectionStatusBinding.inflate(inflater, container, false)
		binding.lifecycleOwner = viewLifecycleOwner
		binding.viewModel = viewModel
		_binding = binding
		return binding.root

	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		binding.buttonFirst.setOnClickListener {
			findNavController().navigate(R.id.action_ConnectionStatusFragment_to_SettingsFragment)
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}