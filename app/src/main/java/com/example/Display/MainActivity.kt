// 파일 경로: com/example/Display/MainActivity.kt
package com.example.Display

import android.annotation.SuppressLint // << [수정 1] 이 import 문을 추가하세요.
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.Display.ui.theme.DisplayTheme

class MainActivity : ComponentActivity() {

  // << [수정 2] onCreate 메서드 바로 위에 이 어노테이션을 추가하세요.
  @SuppressLint("UnsafeOptInUsageError")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()

    setContent {
      DisplayTheme (dynamicColor = false){
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          // 다른 파일에 정의된 VideoPlayer 함수 호출
          VideoPlayer(
            modifier = Modifier
              .padding(innerPadding)
              .fillMaxSize()
          )
        }
      }
    }
  }
}
