package com.example.swtermproject.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.swtermproject.R
import com.example.swtermproject.databinding.FragmentMapBinding
import com.example.swtermproject.ui.placedetail.PlaceDetailActivity
import com.example.swtermproject.util.Constants
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.slider.Slider
import java.util.Locale

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MapViewModel
    private lateinit var placeAdapter: PlaceAdapter
    private var googleMap: GoogleMap? = null
    private var searchCircle: Circle? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            viewModel.getCurrentLocation(requireContext(), fineGranted)
            updateMyLocationEnabled(fineGranted || coarseGranted)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[MapViewModel::class.java]

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupRecyclerView()
        setupCategoryChips()
        setupRadiusSlider()
        setupToggleButtons()
        
        binding.btnPlaceTypes.setOnClickListener {
            val sheet = PlaceTypeBottomSheet()
            sheet.onTypeSelected = { type ->
                if (viewModel.currentLocation.value == null) {
                    requestLocationOrLoad()
                } else {
                    viewModel.searchNearby(requireContext(), type)
                }
            }
            sheet.show(childFragmentManager, "placeTypeSheet")
        }
        setupObservers()
        requestLocationOrLoad()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        updateMyLocationEnabled(hasLocationPermission())
        map.setOnMarkerClickListener { marker ->
            viewModel.places.value
                ?.find { it.name == marker.title }
                ?.let { navigateToDetail(it) }
            true
        }
        updateSearchCircle()
    }

    private fun setupRadiusSlider() {
        binding.sliderRadius.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val radiusInMeters = value.toDouble() * 1000.0
                binding.tvRadius.text = String.format(Locale.getDefault(), "Radius: %.1f km", value)
                viewModel.setRadius(radiusInMeters)
                updateSearchCircle()
            }
        }

        binding.sliderRadius.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}

            override fun onStopTrackingTouch(slider: Slider) {
                // Perform search only when user stops sliding to save API quota
                viewModel.searchNearby(requireContext())
            }
        })
    }

    private fun setupToggleButtons() {
        binding.btnToggleChips.setOnClickListener {
            if (binding.chipGroupScroll.visibility == View.VISIBLE) {
                binding.chipGroupScroll.visibility = View.GONE
                binding.btnToggleChips.setImageResource(android.R.drawable.arrow_down_float)
            } else {
                binding.chipGroupScroll.visibility = View.VISIBLE
                binding.btnToggleChips.setImageResource(android.R.drawable.arrow_up_float)
            }
        }

        binding.btnToggleList.setOnClickListener {
            if (binding.rvPlaces.visibility == View.VISIBLE) {
                binding.rvPlaces.visibility = View.GONE
                binding.btnToggleList.setImageResource(android.R.drawable.arrow_up_float)
            } else {
                binding.rvPlaces.visibility = View.VISIBLE
                binding.btnToggleList.setImageResource(android.R.drawable.arrow_down_float)
            }
        }
    }

    private fun updateSearchCircle() {
        val center = viewModel.currentLocation.value ?: return
        val radius = viewModel.radius.value ?: 1500.0
        val map = googleMap ?: return

        searchCircle?.remove()
        searchCircle = map.addCircle(
            CircleOptions()
                .center(center)
                .radius(radius)
                .strokeWidth(2f)
                .strokeColor(Color.BLUE)
                .fillColor(Color.argb(30, 0, 0, 255))
        )
    }

    private fun requestLocationOrLoad() {
        val fineGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            viewModel.getCurrentLocation(requireContext(), fineGranted)
            updateMyLocationEnabled(true)
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun setupRecyclerView() {
        placeAdapter = PlaceAdapter { place -> navigateToDetail(place) }
        binding.rvPlaces.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = placeAdapter
        }
    }

    private fun setupCategoryChips() {
        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val type = when {
                R.id.chipHospital in checkedIds -> "hospital"
                R.id.chipPharmacy in checkedIds -> "pharmacy"
                R.id.chipGovernment in checkedIds -> "local_government_office"
                R.id.chipRestaurant in checkedIds -> "restaurant"
                R.id.chipSubway in checkedIds -> "subway_station"
                R.id.chipHotel in checkedIds -> "lodging"
                R.id.chipPolice in checkedIds -> "police"
                R.id.chipShop in checkedIds -> "store"
                else -> return@setOnCheckedStateChangeListener
            }
            if (viewModel.currentLocation.value == null) {
                requestLocationOrLoad()
                return@setOnCheckedStateChangeListener
            }
            viewModel.searchNearby(requireContext(), type)
        }
    }

    private fun setupObservers() {
        viewModel.currentLocation.observe(viewLifecycleOwner) { latLng ->
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f))
            updateSearchCircle()
        }

        viewModel.places.observe(viewLifecycleOwner) { places ->
            googleMap?.clear()
            // Redraw circle because clear() removes it
            updateSearchCircle()
            places.forEach { place ->
                googleMap?.addMarker(
                    MarkerOptions().position(LatLng(place.lat, place.lng)).title(place.name)
                )
            }
            placeAdapter.submitList(places)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    private fun navigateToDetail(place: com.example.swtermproject.data.model.PlaceModel) {
        val intent = Intent(requireContext(), PlaceDetailActivity::class.java).apply {
            putExtra(Constants.EXTRA_PLACE_ID, place.id)
            putExtra(Constants.EXTRA_PLACE_NAME, place.name)
            putExtra(Constants.EXTRA_PLACE_ADDRESS, place.address)
            putExtra(Constants.EXTRA_PLACE_LAT, place.lat)
            putExtra(Constants.EXTRA_PLACE_LNG, place.lng)
            putExtra(Constants.EXTRA_PLACE_RATING, place.rating)
            putExtra(Constants.EXTRA_PLACE_CATEGORY, place.category)
        }
        startActivity(intent)
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    private fun updateMyLocationEnabled(enabled: Boolean) {
        if (!isAdded || googleMap == null) return
        if (enabled) {
            googleMap?.isMyLocationEnabled = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
