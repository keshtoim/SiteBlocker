package com.blocksite

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blocksite.data.BlocklistRepository
import com.blocksite.data.PinRepository
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var blocklistRepository: BlocklistRepository
    private lateinit var pinRepository: PinRepository
    private lateinit var adapter: BlocklistAdapter

    private val pinSetupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            finish() // без PIN приложение не работает
        }
    }

    private val pinLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            finish() // отменили ввод PIN — выходим
        }
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startVpnService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        blocklistRepository = BlocklistRepository(applicationContext)
        pinRepository = PinRepository(applicationContext)

        setupRecyclerView()
        setupToggle()
        setupAddDomain()

        checkPinProtection()
    }

    private fun checkPinProtection() {
        if (!pinRepository.hasPin()) {
            pinSetupLauncher.launch(Intent(this, PinSetupActivity::class.java))
        } else {
            pinLauncher.launch(Intent(this, PinActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        adapter = BlocklistAdapter(onRemove = { domain ->
            blocklistRepository.removeDomain(domain)
        })

        findViewById<RecyclerView>(R.id.recyclerDomains).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        lifecycleScope.launch {
            blocklistRepository.domains.collect { domains ->
                adapter.submitList(domains.sorted())
            }
        }
    }

    private fun setupToggle() {
        val toggle = findViewById<Switch>(R.id.switchVpn)
        toggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) requestVpnPermission() else stopVpnService()
        }
    }

    private fun setupAddDomain() {
        val input = findViewById<EditText>(R.id.editDomain)
        val button = findViewById<Button>(R.id.btnAdd)

        button.setOnClickListener {
            val domain = input.text.toString().trim()
            if (domain.isNotEmpty()) {
                blocklistRepository.addDomain(domain)
                input.text.clear()
            }
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        Intent(this, BlockVpnService::class.java).also {
            it.action = BlockVpnService.ACTION_START
            startService(it)
        }
    }

    private fun stopVpnService() {
        Intent(this, BlockVpnService::class.java).also {
            it.action = BlockVpnService.ACTION_STOP
            startService(it)
        }
    }
}
