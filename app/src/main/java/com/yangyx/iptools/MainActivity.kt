package com.yangyx.iptools

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangyx.iptools.ui.components.AppDrawerContent
import com.yangyx.iptools.ui.components.IpActionBottomSheet
import com.yangyx.iptools.ui.screens.DnsScreen
import com.yangyx.iptools.ui.screens.FavoritesScreen
import com.yangyx.iptools.ui.screens.FrpScreen
import com.yangyx.iptools.ui.screens.FscanScreen
import com.yangyx.iptools.ui.screens.HistoryScreen
import com.yangyx.iptools.ui.screens.HomeScreen
import com.yangyx.iptools.ui.screens.IpGeoScreen
import com.yangyx.iptools.ui.screens.IperfScreen
import com.yangyx.iptools.ui.screens.PingScreen
import com.yangyx.iptools.ui.screens.PortScanScreen
import com.yangyx.iptools.ui.screens.TraceScreen
import com.yangyx.iptools.ui.screens.WhoisScreen
import com.yangyx.iptools.ui.theme.IPToolsTheme
import com.yangyx.iptools.ui.viewmodel.AppScreen
import com.yangyx.iptools.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { /* permission result handled */ }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            val userDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkThemeToUse = userDarkMode ?: systemDark

            val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
            val selectedIpAction by viewModel.selectedIpAction.collectAsStateWithLifecycle()

            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            IPToolsTheme(darkTheme = darkThemeToUse) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        AppDrawerContent(
                            viewModel = viewModel,
                            onScreenSelected = { screen ->
                                viewModel.navigateTo(screen)
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = currentScreen.title,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = {
                                        scope.launch {
                                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = "菜单"
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    titleContentColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (currentScreen) {
                                AppScreen.HOME -> HomeScreen(viewModel = viewModel)
                                AppScreen.PING -> PingScreen(viewModel = viewModel)
                                AppScreen.TRACE -> TraceScreen(viewModel = viewModel)
                                AppScreen.PORT_SCAN -> PortScanScreen(viewModel = viewModel)
                                AppScreen.FSCAN -> FscanScreen(viewModel = viewModel)
                                AppScreen.IPERF -> IperfScreen(viewModel = viewModel)
                                AppScreen.DNS -> DnsScreen(viewModel = viewModel)
                                AppScreen.WHOIS -> WhoisScreen(viewModel = viewModel)
                                AppScreen.IP_GEO -> IpGeoScreen(viewModel = viewModel)
                                AppScreen.FRP -> FrpScreen(viewModel = viewModel)
                                AppScreen.FAVORITES -> FavoritesScreen(viewModel = viewModel)
                                AppScreen.HISTORY -> HistoryScreen(viewModel = viewModel)
                            }

                            // Global Quick IP Action Bottom Sheet
                            selectedIpAction?.let { action ->
                                IpActionBottomSheet(
                                    ip = action.ip,
                                    viewModel = viewModel,
                                    onDismiss = { viewModel.dismissIpAction() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
