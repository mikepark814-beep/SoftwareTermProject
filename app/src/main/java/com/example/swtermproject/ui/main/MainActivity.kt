package com.example.swtermproject.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.swtermproject.R
import com.example.swtermproject.databinding.ActivityMainBinding
import com.example.swtermproject.ui.camera.CameraActivity
import com.example.swtermproject.ui.chat.ChatFragment
import com.example.swtermproject.ui.favorite.FavoriteFragment
import com.example.swtermproject.ui.map.MapFragment
import com.example.swtermproject.ui.phrase.PhraseFragment

import com.example.swtermproject.util.Constants
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerToggle: ActionBarDrawerToggle

    var currentLanguage = Constants.DEFAULT_LANGUAGE
        private set

    companion object {
        const val REQUEST_CAMERA = 1001
        private const val TAB_INDEX_TRANSLATE = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupDrawer()
        setupViewPager()
        setupFab()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
    }

    private fun setupDrawer() {
        drawerToggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.drawer_open, R.string.drawer_close
        )
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        binding.navView.setNavigationItemSelectedListener { item ->
            currentLanguage = when (item.itemId) {
                R.id.nav_language_ko -> "ko"
                else -> "en"
            }
            item.isChecked = true
            binding.drawerLayout.closeDrawers()
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(currentLanguage)
            )
            true
        }
    }

    private fun setupViewPager() {
        val fragments = listOf(
            MapFragment(), ChatFragment(), FavoriteFragment(), PhraseFragment()
        )
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = fragments.size
            override fun createFragment(position: Int): Fragment = fragments[position]
        }
        binding.viewPager.isUserInputEnabled = false

        val tabIcons = listOf(
            R.drawable.ic_map, R.drawable.ic_chat,
            R.drawable.ic_favorite, R.drawable.ic_translate
        )
        val tabLabels = listOf(
            R.string.tab_map, R.string.tab_chat,
            R.string.tab_favorites, R.string.tab_translate
        )
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.setIcon(tabIcons[pos])
            tab.setText(tabLabels[pos])
        }.attach()

        // Sync FAB visibility with tab selection
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.fabCamera.visibility = if (position == TAB_INDEX_TRANSLATE) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        })
    }

    private fun setupFab() {
        // Initial visibility
        binding.fabCamera.visibility = View.GONE

        binding.fabCamera.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java).apply {
                putExtra(Constants.EXTRA_LANGUAGE_CODE, currentLanguage)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_CAMERA)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAMERA && resultCode == RESULT_OK) {
            val result = data?.getStringExtra(Constants.EXTRA_TRANSLATION_RESULT) ?: return
            Snackbar.make(binding.root, result, Snackbar.LENGTH_LONG).show()
        }
    }
}
