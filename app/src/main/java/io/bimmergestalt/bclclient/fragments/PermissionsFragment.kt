package io.bimmergestalt.bclclient.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import io.bimmergestalt.bclclient.controllers.PermissionsController
import io.bimmergestalt.bclclient.databinding.PermissionsBinding
import io.bimmergestalt.bclclient.models.PermissionsViewModel

class PermissionsFragment: Fragment() {
    val viewModel by viewModels<PermissionsViewModel>()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = PermissionsBinding.inflate(inflater, container, false)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.controller = PermissionsController(requireActivity())
        binding.viewModel = viewModel
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        viewModel.update(requireContext())
    }
}