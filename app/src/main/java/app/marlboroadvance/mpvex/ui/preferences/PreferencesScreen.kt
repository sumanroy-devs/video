package app.marlboroadvance.mpvex.ui.preferences

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.BuildConfig
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.update.UpdateViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals

@Serializable
object PreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val context = LocalContext.current

    // Activity-scoped: same instance as the UpdateDialog hosted in MainActivity
    val updateViewModel: UpdateViewModel? = if (BuildConfig.ENABLE_UPDATE_FEATURE) {
      viewModel(context as ComponentActivity)
    } else {
      null
    }
    val updateState by (updateViewModel?.updateState ?: MutableStateFlow(UpdateViewModel.UpdateState.Idle)).collectAsState()
    val isCheckingUpdate = updateState is UpdateViewModel.UpdateState.Loading

    // Manual check results → native toasts (update dialog itself is hosted in MainActivity)
    LaunchedEffect(updateState) {
      when (updateState) {
        is UpdateViewModel.UpdateState.NoUpdate -> {
          Toast.makeText(context, "Already using latest version", Toast.LENGTH_SHORT).show()
          updateViewModel?.dismissNoUpdate()
        }
        is UpdateViewModel.UpdateState.Error -> {
          Toast.makeText(context, "Couldn't check for updates. Check your connection.", Toast.LENGTH_SHORT).show()
        }
        else -> {}
      }
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { 
            Text(
              text = stringResource(R.string.pref_preferences),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(
                Icons.AutoMirrored.Outlined.ArrowBack, 
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        LazyColumn(
          modifier =
            Modifier
              .fillMaxSize()
              .padding(padding),
        ) {
          // Search bar - full width, prominent placement
          item {
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable { backstack.add(SettingsSearchScreen) },
              shape = RoundedCornerShape(28.dp),
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              tonalElevation = 2.dp,
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Outlined.Search,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                  text = stringResource(R.string.settings_search_hint),
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.outline,
                )
              }
            }
          }
          
          // UI & Appearance Section
          item {
            PreferenceSectionHeader(title = "UI & Appearance")
          }
          
          item {
            PreferenceCard {
              Preference(
                title = { Text(text = stringResource(id = R.string.pref_appearance_title)) },
                summary = { 
                  Text(
                    text = stringResource(id = R.string.pref_appearance_summary),
                    color = MaterialTheme.colorScheme.outline
                  ) 
                },
                icon = { 
                  Icon(
                    Icons.Outlined.Palette, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  ) 
                },
                onClick = { backstack.add(AppearancePreferencesScreen) },
              )
              
              PreferenceDivider()
              
              Preference(

                title = { Text(text = stringResource(id = R.string.pref_layout_title)) },
                summary = { 
                  Text(
                    text = stringResource(id = R.string.pref_layout_summary),
                    color = MaterialTheme.colorScheme.outline
                  ) 
                },
                icon = { 
                  Icon(
                    Icons.AutoMirrored.Outlined.ViewQuilt, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  ) 
                },
                onClick = { backstack.add(PlayerControlsPreferencesScreen) },
              )
            }
          }
          
          // Playback & Controls Section
          item {
            PreferenceSectionHeader(title = "Playback & Controls")
          }
          
          item {
            PreferenceCard {
              Preference(

                title = { Text(text = stringResource(id = R.string.pref_player)) },
                summary = { 
                  Text(
                    text = stringResource(id = R.string.pref_player_summary),
                    color = MaterialTheme.colorScheme.outline
                  ) 
                },
                icon = { 
                  Icon(
                    Icons.Outlined.PlayCircle, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  ) 
                },
                onClick = { backstack.add(PlayerPreferencesScreen) },
              )
              
              PreferenceDivider()
              
              Preference(

                title = { Text(text = stringResource(id = R.string.pref_gesture)) },
                summary = { 
                  Text(
                    text = stringResource(id = R.string.pref_gesture_summary),
                    color = MaterialTheme.colorScheme.outline
                  ) 
                },
                icon = { 
                  Icon(
                    Icons.Outlined.Gesture, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  ) 
                },
                onClick = { backstack.add(GesturePreferencesScreen) },
              )
            }
          }
          
          // File Management Section
          item {
            PreferenceSectionHeader(title = "File Management")
          }
          
          item {
            PreferenceCard {
              Preference(

                title = { Text(text = stringResource(id = R.string.pref_folders_title)) },
                summary = { 
                  Text(
                    text = stringResource(id = R.string.pref_folders_summary),
                    color = MaterialTheme.colorScheme.outline
                  ) 
                },
                icon = { 
                  Icon(
                    Icons.Outlined.Folder, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  ) 
                },
                onClick = { backstack.add(FoldersPreferencesScreen) },
              )
            }
          }
          
          // Media Settings Section
          item {
            PreferenceSectionHeader(title = "Media Settings")
          }
          
          item {
            PreferenceCard {
              Preference(

                title = { Text(text = stringResource(id = R.string.pref_decoder)) },
                summary = { 
                  Text(
                    text = stringResource(id = R.string.pref_decoder_summary),
                    color = MaterialTheme.colorScheme.outline
                  ) 
                },
                icon = { 
                  Icon(
                    Icons.Outlined.Memory, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  ) 
                },
                onClick = { backstack.add(DecoderPreferencesScreen) },
              )
              
              PreferenceDivider()
              
              Preference(

                title = { Text(text = stringResource(id = R.string.pref_subtitles)) },
                summary = { 
                  Text(
                    text = stringResource(id = R.string.pref_subtitles_summary),
                    color = MaterialTheme.colorScheme.outline
                  ) 
                },
                icon = { 
                  Icon(
                    Icons.Outlined.Subtitles, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  ) 
                },
                onClick = { backstack.add(SubtitlesPreferencesScreen) },
              )
              
              PreferenceDivider()
              
              Preference(

                title = { Text(text = stringResource(id = R.string.pref_audio)) },
                summary = { 
                  Text(
                    text = stringResource(id = R.string.pref_audio_summary),
                    color = MaterialTheme.colorScheme.outline
                  ) 
                },
                icon = { 
                  Icon(
                    Icons.Outlined.Audiotrack, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  ) 
                },
                onClick = { backstack.add(AudioPreferencesScreen) },
              )
            }
          }
          
          // Advanced & About Section
          item {
            PreferenceSectionHeader(title = "Advanced & Updates")
          }
          
          item {
            PreferenceCard {
              Preference(

                title = { Text(text = stringResource(R.string.pref_advanced)) },
                summary = { 
                  Text(
                    text = stringResource(id = R.string.pref_advanced_summary),
                    color = MaterialTheme.colorScheme.outline
                  ) 
                },
                icon = { 
                  Icon(
                    Icons.Outlined.Code, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  ) 
                },
                onClick = { backstack.add(AdvancedPreferencesScreen) },
              )
              
              PreferenceDivider()

              Preference(

                title = { Text(text = stringResource(id = R.string.pref_check_updates_title)) },
                summary = {
                  Text(
                    text = if (isCheckingUpdate) "Checking for updates…" else stringResource(id = R.string.pref_check_updates_summary),
                    color = MaterialTheme.colorScheme.outline
                  )
                },
                icon = {
                  Icon(
                    if (isCheckingUpdate) Icons.Outlined.Sync else Icons.Outlined.Update,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  )
                },
                onClick = { updateViewModel?.checkForUpdate(manual = true) },
              )
            }
          }

          // Developer Section
          item {
            PreferenceSectionHeader(title = "Developer")
          }

          item {
            PreferenceCard {
              Preference(
                title = { Text(text = stringResource(id = R.string.pref_developer_name)) },
                summary = {
                  Text(
                    text = stringResource(id = R.string.pref_developer_email),
                    color = MaterialTheme.colorScheme.outline
                  )
                },
                icon = {
                  Row {
                    Icon(
                      painter = painterResource(id = R.drawable.developer),
                      contentDescription = null,
                      modifier = Modifier.size(50.dp),
                      tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                  }
                },
              )
            }
          }
        }
      }
    }
  }
}
