/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// NonStreamScreen - DAT Device Selection and Setup
//
// This screen demonstrates DAT device management and pre-streaming setup. It handles device
// registration status, camera permissions, and stream readiness.

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NonStreamScreen(
    viewModel: WearablesViewModel,
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
    modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val gettingStartedSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()

  MaterialTheme(colorScheme = darkColorScheme()) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
      // 1. Background Image
      Image(
          painter = painterResource(id = R.drawable.background_home),
          contentDescription = "Background",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
      )

      Column(
          modifier = Modifier.fillMaxSize()
      ) {
        // 2. Top Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
          Image(
              painter = painterResource(id = R.drawable.top_bar__home_),
              contentDescription = "Top Bar Background",
              modifier = Modifier.fillMaxWidth(),
              contentScale = ContentScale.FillWidth
          )
          Row(
              modifier = Modifier.matchParentSize(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Image(
                painter = painterResource(id = R.drawable.spatial_diary_logo_png),
                contentDescription = "Spatial Diary Logo",
                modifier = Modifier
                    .padding(start = 16.dp)
                    .size(34.dp)
            )
            Text(
                text = "Spatial Diary",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.padding(end = 16.dp)
            )
          }
        }

        // 3. Text 40 pixels below the top bar
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Let's capture your 3D memory",
            color = Color.White,
            fontSize = 18.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        // 4. Web view 16 px below the text
        Spacer(modifier = Modifier.height(16.dp))

        // Web view filling the available container space with stroke
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Fills remaining vertical space
                .padding(horizontal = 32.dp)
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(24.dp)
                )
                .clip(RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
          AndroidView(
              factory = { ctx ->
                WebView(ctx).apply {
                  layoutParams = ViewGroup.LayoutParams(
                      ViewGroup.LayoutParams.MATCH_PARENT,
                      ViewGroup.LayoutParams.MATCH_PARENT
                  )
                  settings.javaScriptEnabled = true
                  settings.domStorageEnabled = true
                  webChromeClient = WebChromeClient()
                  webViewClient = WebViewClient()
                  setBackgroundColor(android.graphics.Color.TRANSPARENT)
                  loadUrl("https://rangesh06.github.io/splat-viewer/?url=https://sparkjs.dev/assets/splats/butterfly.spz")
                }
              },
              modifier = Modifier.fillMaxSize()
          )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 5. Bottom Elements
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          if (!uiState.hasActiveDevice) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
              Icon(
                  painter = painterResource(id = R.drawable.hourglass_icon),
                  contentDescription = "Waiting for device",
                  tint = Color.White.copy(alpha = 0.7f),
                  modifier = Modifier.size(16.dp),
              )
              Text(
                  text = stringResource(R.string.waiting_for_active_device),
                  style = MaterialTheme.typography.bodyMedium,
                  color = Color.White.copy(alpha = 0.7f),
              )
            }
          }

          // Start Recording Button
          Box(
              modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 48.dp)
                  .alpha(if (uiState.hasActiveDevice) 1f else 0.5f)
                  .clickable(enabled = uiState.hasActiveDevice) {
                    viewModel.navigateToStreaming(onRequestWearablesPermission)
                  },
              contentAlignment = Alignment.Center
          ) {
            Image(
                painter = painterResource(id = R.drawable.button),
                contentDescription = "Start Recording Button Background",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
            // Using a simple wrapped Row ensuring Icon and Text render on top of the button
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                  painter = painterResource(id = R.drawable.start_recording),
                  contentDescription = "Start Recording Icon",
                  tint = Color.White,
                  modifier = Modifier.size(24.dp)
              )
              Spacer(modifier = Modifier.width(16.dp))
              Text(
                  text = "Start Recording",
                  color = Color.White,
                  fontSize = 20.sp,
                  fontWeight = FontWeight.Medium
              )
            }
          }
        }
      }

      // Getting Started Sheet
      if (uiState.isGettingStartedSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideGettingStartedSheet() },
            sheetState = gettingStartedSheetState,
        ) {
          GettingStartedSheetContent(
              onContinue = {
                scope.launch {
                  gettingStartedSheetState.hide()
                  viewModel.hideGettingStartedSheet()
                }
              }
          )
        }
      }
    }
  }
}

@Composable
private fun GettingStartedSheetContent(onContinue: () -> Unit, modifier: Modifier = Modifier) {
  Column(
      modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Text(
        text = stringResource(R.string.getting_started_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(8.dp).padding(bottom = 16.dp),
    ) {
      TipItem(
          iconResId = R.drawable.video_icon,
          text = stringResource(R.string.getting_started_tip_permission),
      )
      TipItem(
          iconResId = R.drawable.tap_icon,
          text = stringResource(R.string.getting_started_tip_photo),
      )
      TipItem(
          iconResId = R.drawable.smart_glasses_icon,
          text = stringResource(R.string.getting_started_tip_led),
      )
    }

    SwitchButton(
        label = stringResource(R.string.getting_started_continue),
        onClick = onContinue,
        modifier = Modifier.navigationBarsPadding(),
    )
  }
}

@Composable
private fun TipItem(iconResId: Int, text: String, modifier: Modifier = Modifier) {
  Row(modifier = modifier.fillMaxWidth()) {
    Icon(
        painter = painterResource(id = iconResId),
        contentDescription = "Getting started tip icon",
        modifier = Modifier.padding(start = 4.dp, top = 4.dp).width(24.dp),
    )
    Spacer(modifier = Modifier.width(10.dp))
    Text(text = text)
  }
}
