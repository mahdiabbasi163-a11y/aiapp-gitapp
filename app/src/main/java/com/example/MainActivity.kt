package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.billing.BazaarBillingManager
import com.example.data.AssistantRepository
import com.example.data.api.RetrofitClient
import com.example.data.db.AppDatabase
import com.example.ui.AssistantViewModel
import com.example.ui.AssistantViewModelFactory
import com.example.ui.screens.AssistantScreen
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    var bazaarBillingManager: BazaarBillingManager? = null
    lateinit var viewModel: AssistantViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("MainActivity", "Uncaught exception safely caught on thread: ${thread.name}", throwable)
        }

        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = AssistantRepository(
            assistantDao = database.assistantDao(),
            offlineDataDao = database.offlineDataDao(),
            apiService = RetrofitClient.service
        )

        viewModel = ViewModelProvider(
            this,
            AssistantViewModelFactory(repository, applicationContext)
        )[AssistantViewModel::class.java]

        setContent {
            MyApplicationTheme {
                AssistantScreen(
                    viewModel = viewModel,
                    onPurchasePlan = { sku ->
                        try {
                            bazaarBillingManager?.launchPurchaseFlow(this@MainActivity, sku)
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Launch purchase error", e)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val manager = BazaarBillingManager(applicationContext)
                manager.onPurchaseResultListener = { sku, purchaseToken, orderId ->
                    viewModel.syncBazaarPurchaseToServer(sku, purchaseToken, orderId)
                }
                bazaarBillingManager = manager

                manager.startConnection()
                manager.ownedSubscriptions.collect { ownedSkus ->
                    val activeSku = ownedSkus.firstOrNull {
                        it in listOf("ir.golden.com", "ir.silver.com", "ir.almas.com", "ir.12-month.com")
                    } ?: ""
                    if (activeSku.isNotEmpty()) {
                        viewModel.setPremiumUserLocally(activeSku)
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("MainActivity", "Bazaar billing setup error", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            bazaarBillingManager?.startConnection()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Resume billing error", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            bazaarBillingManager?.endConnection()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Destroy billing error", e)
        }
    }
}
