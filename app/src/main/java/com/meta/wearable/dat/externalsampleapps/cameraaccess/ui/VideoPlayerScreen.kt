package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R
import java.io.File

@Composable
fun LoadingScreen(message: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Background Image
        Image(
            painter = painterResource(id = R.drawable.loading_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Center Content (Logos & Title) - moved 10dp up
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(170.dp) // Increased outer container to fit larger outer logo
            ) {
                Image(
                    painter = painterResource(id = R.drawable.inner_logo),
                    contentDescription = null,
                    modifier = Modifier.size(108.dp)
                )
                Image(
                    painter = painterResource(id = R.drawable.outer_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(170.dp) // Increased outer logo size to create visual gap
                        .rotate(rotation)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Creating your 3D memory",
                color = Color.White,
                fontSize = 24.sp,
                fontFamily = FontFamily(Font(R.font.plus_jakarta_sans_bold)),
                textAlign = TextAlign.Center
            )
        }

        // Bottom Content (Status Messages)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 68.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.80f),
                fontSize = 18.sp,
                fontFamily = FontFamily(Font(R.font.plus_jakarta_sans_medium)),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Please wait for 5 mins...",
                color = Color.White.copy(alpha = 0.80f),
                fontSize = 14.sp,
                fontFamily = FontFamily(Font(R.font.plus_jakarta_sans_medium)),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun VideoPlayerScreen(
    videoPath: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    convertViewModel: ConvertViewModel = viewModel()
) {
    val convertUiState by convertViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (convertUiState is ConvertUiState.Success) {
        WorldViewerScreen(
            splatUrl = (convertUiState as ConvertUiState.Success).splatUrl,
            onClose = { convertViewModel.resetState() }
        )
        return
    }

    if (convertUiState is ConvertUiState.Loading) {
        LoadingScreen(message = (convertUiState as ConvertUiState.Loading).message)
        return
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val file = File(videoPath)
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Video",
                tint = Color.White
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (convertUiState is ConvertUiState.Error) {
                Text(
                    text = (convertUiState as ConvertUiState.Error).message,
                    color = Color.Red,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            Button(
                onClick = { convertViewModel.convertToWorld(File(videoPath)) },
                enabled = convertUiState is ConvertUiState.Idle || convertUiState is ConvertUiState.Error,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Blue,
                    contentColor = Color.White,
                    disabledContainerColor = Color.Gray
                )
            ) {
                Text(text = "Convert to 3D 🌍")
            }
        }
    }
}
