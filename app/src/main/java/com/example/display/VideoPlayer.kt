package com.example.display

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import com.example.display.ui.theme.DisplayTheme
import kotlinx.coroutines.delay
import java.io.File

// 콘텐츠 타입을 파일 경로(Uri)와 함께 저장하도록 변경
enum class ContentType { VIDEO, IMAGE }
data class PlaylistItem(val uri: Uri, val type: ContentType)

// 앱의 전용 폴더 이름
const val APP_FOLDER_NAME = "Display"

@Composable
fun VideoPlayer(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  var playlist by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }
  var currentContentIndex by remember { mutableIntStateOf(0) }
  var hasPermissions by remember { mutableStateOf(false) }
  var showSettingsDialog by remember { mutableStateOf(false) }

  val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
  } else {
    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
  }

  val activity = LocalActivity.current

  val appSettingsLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
    onResult = {
      Log.d("Settings", "설정 화면실행.")
    }
  )

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
    onResult = { permissions ->
      val allGranted = permissions.values.all { it }
      if (allGranted) {
        hasPermissions = true
        showSettingsDialog = false
      } else {
        hasPermissions = false
        val isPermanentlyDenied = permissionsToRequest.any { permission ->
          activity?.shouldShowRequestPermissionRationale(permission) == false
        }
        if (isPermanentlyDenied) {
          showSettingsDialog = true
          Log.w("Permission", "Permissions Denied.")
        } else {
          showSettingsDialog = false
          Log.w("Permission", "Permissions Temporary Denied.")
        }
      }
    }
  )

  val appDirectory = File(
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
    APP_FOLDER_NAME
  )
  if (!appDirectory.exists()) {
    appDirectory.mkdirs()
  }

  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, permissionLauncher) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_START) {
        val allPermissionsGranted = permissionsToRequest.all {
          ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!allPermissionsGranted) {
          permissionLauncher.launch(permissionsToRequest)
        } else {
          hasPermissions = true
          showSettingsDialog = false
        }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  LaunchedEffect(hasPermissions) {
    if (hasPermissions) {
      val files = appDirectory.listFiles()
      if (files != null && files.isNotEmpty()) {
        val loadedPlaylist = files.mapNotNull { file ->
          val type = when (file.extension.lowercase()) {
            "mp4", "mkv", "webm" -> ContentType.VIDEO
            "jpg", "jpeg", "png", "webp" -> ContentType.IMAGE
            else -> null
          }
          type?.let { PlaylistItem(file.toUri(), it) }
        }.sortedBy { it.uri.path }
        playlist = loadedPlaylist
        Log.d("Playlist", "Loaded ${loadedPlaylist.size} items.")
      } else {
        playlist = emptyList()
        Log.w("Playlist", "${appDirectory.path} Empty.")
      }
    }
  }

  if (showSettingsDialog) {
    SettingsDialog(
      onDismiss = { showSettingsDialog = false },
      onConfirm = {
        showSettingsDialog = false
        // [최종 해결] 특정 권한 그룹 설정으로 직접 이동하는 인텐트 생성 및 실행
        val intent = Intent("android.settings.action.MANAGE_APP_PERMISSION").apply {
          putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
          // 안드로이드 버전에 따라 정확한 권한 그룹 이름을 지정
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 안드로이드 13 이상은 'Photos and Videos' 권한 그룹
            putExtra(
              "android.provider.extra.PERMISSION_GROUP_NAME",
              Manifest.permission_group.READ_MEDIA_VISUAL
            ) // <- 정확한 값으로 수정
          } else {
            // 그 이전 버전은 'Storage' 권한 그룹
            putExtra(
              "android.provider.extra.PERMISSION_GROUP_NAME",
              Manifest.permission_group.STORAGE
            )
          }
        }
        try {
          appSettingsLauncher.launch(intent)
        } catch (e: Exception) {
          Log.e(
            "SettingsIntent",
            "Failed to move specific permission settings. Executing a replacement intent.",
            e
          )
          val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
          }
          appSettingsLauncher.launch(fallbackIntent)
        }
      }
    )
  }

  if (!hasPermissions) {
    PermissionRequestUI(folderPath = appDirectory.path)
  } else if (playlist.isNotEmpty()) {
    val currentItem = playlist[currentContentIndex]
    val playNext = { currentContentIndex = (currentContentIndex + 1) % playlist.size }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      when (currentItem.type) {
        ContentType.IMAGE -> {
          Image(
            painter = rememberAsyncImagePainter(model = currentItem.uri),
            contentDescription = "Display Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
          )
          LaunchedEffect(key1 = currentContentIndex) {
            delay(10000)
            playNext()
          }
        }

        ContentType.VIDEO -> {
          val exoPlayer = remember { ExoPlayer.Builder(context).build() }
          DisposableEffect(key1 = currentContentIndex) {
            val mediaItem = MediaItem.fromUri(currentItem.uri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            val listener = object : Player.Listener {
              override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                  playNext()
                }
              }
            }
            exoPlayer.addListener(listener)
            onDispose {
              exoPlayer.removeListener(listener)
              exoPlayer.release()
            }
          }
          AndroidView(
            factory = {
              PlayerView(it).apply {
                player = exoPlayer
                useController = false
              }
            },
            modifier = Modifier.fillMaxSize()
          )
        }
      }
    }
  } else {
    EmptyPlaylistUI(folderPath = appDirectory.path)
  }
}

@Composable
fun SettingsDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("권한이 필요해요") },
    text = { Text("미디어 파일을 재생하려면\n저장소 접근 권한이 필요해요.\n\n'설정 > 권한'으로 이동하여\n권한을 허용해주세요.") },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(
          text = "설정으로 이동",
          color = MaterialTheme.colorScheme.primary,
          style = MaterialTheme.typography.labelLarge
        )
      }
    },
  )
}

@Composable
fun EmptyPlaylistUI(modifier: Modifier = Modifier, folderPath: String) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp), contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Icon(
        Icons.Rounded.VideocamOff,
        "콘텐츠 없음",
        Modifier.size(48.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(modifier = Modifier.height(16.dp))
      Text("재생할 콘텐츠가 없어요", style = MaterialTheme.typography.titleMedium)
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "아래 폴더에 동영상이나 이미지 파일을\n추가해주세요.\n\n$folderPath",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )
    }
  }
}

@Composable
fun PermissionRequestUI(modifier: Modifier = Modifier, folderPath: String) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp), contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Icon(
        Icons.Rounded.SdCard,
        "권한 필요",
        Modifier.size(48.dp),
        tint = MaterialTheme.colorScheme.error
      )
      Spacer(modifier = Modifier.height(16.dp))
      Text("저장소 접근 권한이 필요해요", style = MaterialTheme.typography.titleMedium)
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "앱이 '${folderPath}' 폴더의\n파일을 읽을 수 있도록 허용해주세요.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )
    }
  }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  DisplayTheme(dynamicColor = false) {
    EmptyPlaylistUI(folderPath = "/storage/emulated/0/Movies/Display")
  }
}
