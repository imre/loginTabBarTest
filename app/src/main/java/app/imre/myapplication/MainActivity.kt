package app.imre.myapplication

// Import statements for required Android, Compose, Navigation, and DataStore libraries.
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// -----------------------------
// DataStore Extension Property
// -----------------------------
// Creates a singleton DataStore instance for the application using the Context.
// This DataStore is used to persist session-related data (e.g. a session token).
private val Context.dataStore by preferencesDataStore(name = "session")

// -----------------------------
// SessionManager (data/SessionManager.kt)
// -----------------------------
/**
 * A class to manage user sessions by reading and writing a session token via DataStore.
 * It provides methods for creating and clearing a session as well as exposing the authentication state.
 */
class SessionManager(private val context: Context) {
    // Retrieve the DataStore instance from the context.
    private val dataStore = context.dataStore

    // Define keys to store values in the DataStore.
    private object Keys {
        val sessionToken = stringPreferencesKey("session_token")
    }

    // Expose a StateFlow representing the authentication state.
    // It maps the DataStore data to a Boolean that indicates if the session token exists.
    val isAuthenticated = dataStore.data
        .map { prefs -> !prefs[Keys.sessionToken].isNullOrEmpty() }
        .stateIn(
            scope = CoroutineScope(Dispatchers.IO),  // Runs on the IO dispatcher.
            started = SharingStarted.Lazily,         // Starts collecting lazily.
            initialValue = false                       // Default value is false (not authenticated).
        )

    /**
     * Creates a session by storing the provided token.
     *
     * @param token The session token to store.
     */
    suspend fun createSession(token: String) {
        dataStore.edit { prefs ->
            prefs[Keys.sessionToken] = token
        }
    }

    /**
     * Clears the session by removing the session token from the DataStore.
     */
    suspend fun clearSession() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.sessionToken)
        }
    }
}

// -----------------------------
// Navigation Destinations (ui/navigation/AppDestination.kt)
// -----------------------------
/**
 * Defines the navigation destinations using a sealed class hierarchy.
 * This approach provides type safety for route names.
 */
sealed class AppDestination(val route: String) {
    // Authentication route.
    object Auth : AppDestination("auth")

    // Main application routes encapsulated under the Main sealed class.
    sealed class Main(route: String) : AppDestination(route) {
        object Feed : Main("main/feed")
        object Profile : Main("main/profile")
        object Settings : Main("main/settings")
    }
}

// -----------------------------
// App Theme (ui/theme/Theme.kt)
// -----------------------------
/**
 * A composable that applies the app's theme using a dynamic light colour scheme.
 * It uses the current context to derive the appropriate colour scheme.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = dynamicLightColorScheme(LocalContext.current),
        content = content
    )
}

// -----------------------------
// AppState (ui/AppState.kt)
// -----------------------------
/**
 * A stable class encapsulating the overall application state.
 * It holds the navigation controller and session manager,
 * and provides functions to sign in and sign out.
 */
@Stable
class AppState(
    val navController: NavHostController,
    val sessionManager: SessionManager
) {
    // Expose the authentication state from SessionManager.
    val isAuthenticated = sessionManager.isAuthenticated

    /**
     * Signs out the user by clearing the session and navigating to the authentication screen.
     */
    suspend fun signOut() {
        sessionManager.clearSession()
        navController.navigate(AppDestination.Auth.route) {
            // Pop up to the start destination to clear the back stack.
            popUpTo(navController.graph.startDestinationId) { inclusive = true }
        }
    }

    /**
     * Signs in the user by creating a session with the provided token and navigating to the main screen.
     *
     * @param token The session token used for signing in.
     */
    suspend fun signIn(token: String) {
        sessionManager.createSession(token)
        // Navigate to the main route.
        navController.navigate("main") {
            popUpTo(navController.graph.startDestinationId) { inclusive = true }
        }
    }
}

/**
 * A composable function to create and remember the AppState.
 * It initialises the NavController and SessionManager, and returns an AppState instance.
 *
 * @param navController Optionally provide an existing NavController.
 */
@Composable
fun rememberAppState(
    navController: NavHostController? = null
): AppState {
    // Use the provided NavController or create a new one if null.
    val navControllerLocal = navController ?: rememberNavController()

    // Obtain the current Context.
    val context = LocalContext.current

    // Create a SessionManager using the current context.
    val sessionManager = remember { SessionManager(context) }

    // Return a remembered instance of AppState.
    return remember(navControllerLocal, sessionManager) {
        AppState(navControllerLocal, sessionManager)
    }
}

// -----------------------------
// Main App (ui/App.kt)
// -----------------------------
/**
 * The main composable function that sets up the app's navigation and theme.
 * It defines the navigation graph for both authentication and the main application.
 */
@Composable
fun App() {
    AppTheme {
        // Initialise the app state.
        val appState = rememberAppState()
        // Observe the authentication state as a Compose state.
        val isAuthenticated by appState.isAuthenticated.collectAsState()
        // Create a coroutine scope for launching suspend functions.
        val coroutineScope = rememberCoroutineScope()

        // Define the main NavHost with the authentication screen as the start destination.
        NavHost(
            navController = appState.navController,
            startDestination = AppDestination.Auth.route  // Always start with the Auth route.
        ) {
            // Composable for the Authentication screen.
            composable(AppDestination.Auth.route) {
                // React to authentication state changes; navigate to main if already authenticated.
                LaunchedEffect(isAuthenticated) {
                    if (isAuthenticated) {
                        appState.navController.navigate("main") {
                            popUpTo(AppDestination.Auth.route) { inclusive = true }
                        }
                    }
                }

                // Render the AuthScreen and pass in the sign-in callback.
                AuthScreen(
                    onSignIn = { token ->
                        coroutineScope.launch {
                            appState.signIn(token)
                        }
                    }
                )
            }

            // Define a nested navigation graph for the main authenticated section.
            navigation(
                route = "main",
                startDestination = AppDestination.Main.Feed.route
            ) {
                // Composable for the Feed screen.
                composable(AppDestination.Main.Feed.route) {
                    // If the user becomes unauthenticated, navigate back to the Auth screen.
                    LaunchedEffect(isAuthenticated) {
                        if (!isAuthenticated) {
                            appState.navController.navigate(AppDestination.Auth.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                    // Render the main screen which contains the bottom navigation.
                    MainScreen(appState = appState)
                }
            }
        }
    }
}

// -----------------------------
// Auth Screen (ui/screens/AuthScreen.kt)
// -----------------------------
/**
 * The authentication screen composable.
 * It presents an input field for the username (used as a token) and a sign-in button.
 *
 * @param onSignIn A suspend function callback to handle sign in.
 */
@Composable
fun AuthScreen(
    onSignIn: suspend (String) -> Unit
) {
    // Remember a coroutine scope for launching suspend functions.
    val scope = rememberCoroutineScope()
    // Local state to hold the user's input (username/token).
    var username by remember { mutableStateOf("") }

    // Layout the screen using a Column.
    Column(
        modifier = Modifier
            .fillMaxSize()  // Fill the entire screen.
            .padding(16.dp), // Apply padding to the content.
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Text field for username input.
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") }
        )

        // Spacer to add vertical space between the text field and button.
        Spacer(modifier = Modifier.height(16.dp))

        // Button to trigger sign in.
        Button(onClick = {
            scope.launch {
                onSignIn(username)
            }
        }) {
            Text("Sign In")
        }
    }
}

// -----------------------------
// Main Screen (ui/screens/MainScreen.kt)
// -----------------------------
/**
 * The main screen composable for authenticated users.
 * It sets up bottom navigation and a nested NavHost to switch between tabs.
 *
 * @param appState The overall application state.
 */
@Composable
fun MainScreen(appState: AppState) {
    // Create a NavController dedicated to bottom navigation.
    val bottomNavController = rememberNavController()
    // Observe the current back stack entry to determine which tab is selected.
    val currentBackStackEntry by bottomNavController.currentBackStackEntryAsState()
    // Remember a coroutine scope for handling suspend operations.
    val coroutineScope = rememberCoroutineScope()

    // Define the list of bottom navigation tabs.
    val tabs = listOf(
        AppDestination.Main.Feed,
        AppDestination.Main.Profile,
        AppDestination.Main.Settings
    )

    // Use a Scaffold to set up the screen structure including a bottom bar.
    Scaffold(
        bottomBar = {
            NavigationBar {
                // Create a NavigationBarItem for each tab.
                tabs.forEach { destination ->
                    // Determine if the current destination matches the tab's route.
                    val selected = currentBackStackEntry?.destination?.route == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            bottomNavController.navigate(destination.route) {
                                // Pop up to the feed destination to avoid stacking multiple copies.
                                popUpTo(AppDestination.Main.Feed.route) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination.
                                launchSingleTop = true
                                // Restore previous state when the tab is reselected.
                                restoreState = true
                            }
                        },
                        // Set the icon based on the destination.
                        icon = {
                            when (destination) {
                                AppDestination.Main.Feed -> Icon(Icons.Default.Home, contentDescription = "Feed")
                                AppDestination.Main.Profile -> Icon(Icons.Default.Person, contentDescription = "Profile")
                                AppDestination.Main.Settings -> Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        },
                        // Set the label for the navigation item.
                        label = {
                            Text(
                                when (destination) {
                                    AppDestination.Main.Feed -> "Feed"
                                    AppDestination.Main.Profile -> "Profile"
                                    AppDestination.Main.Settings -> "Settings"
                                }
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        // Create a nested NavHost for the content area of each tab.
        NavHost(
            navController = bottomNavController,
            startDestination = AppDestination.Main.Feed.route,
            modifier = Modifier.padding(padding)
        ) {
            // Define the composable for the Feed screen.
            composable(AppDestination.Main.Feed.route) {
                FeedScreen()
            }
            // Define the composable for the Profile screen.
            composable(AppDestination.Main.Profile.route) {
                ProfileScreen()
            }
            // Define the composable for the Settings screen, including a sign-out option.
            composable(AppDestination.Main.Settings.route) {
                SettingsScreen(
                    onSignOut = {
                        coroutineScope.launch {
                            appState.signOut()
                        }
                    }
                )
            }
        }
    }
}

// -----------------------------
// Dummy Screen Implementations
// -----------------------------
/**
 * A simple composable representing the Feed screen.
 */
@Composable
fun FeedScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Feed Screen")
    }
}

/**
 * A simple composable representing the Profile screen.
 */
@Composable
fun ProfileScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Profile Screen")
    }
}

/**
 * A simple composable representing the Settings screen.
 * It includes a button to sign out.
 *
 * @param onSignOut A callback invoked when the user taps the sign-out button.
 */
@Composable
fun SettingsScreen(onSignOut: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Settings Screen")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSignOut) {
            Text("Sign Out")
        }
    }
}

// -----------------------------
// MainActivity (MainActivity.kt)
// -----------------------------
/**
 * The main activity that serves as the entry point to the app.
 * It sets the content of the activity to the composable App.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the content view to the App composable.
        setContent {
            App()
        }
    }
}