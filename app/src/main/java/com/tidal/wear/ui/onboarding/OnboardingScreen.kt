package com.tidal.wear.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import com.tidal.wear.core.auth.DeviceAuthSession
import com.tidal.wear.core.auth.TidalAuthException
import com.tidal.wear.core.auth.TidalAuthRepository
import com.tidal.wear.ui.theme.TidalColors
import java.io.IOException
import kotlinx.coroutines.CancellationException

@Composable
fun OnboardingScreen(
    authRepository: TidalAuthRepository,
    onAuthenticated: () -> Unit,
) {
    var session by remember { mutableStateOf<DeviceAuthSession?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableStateOf(0) }
    val isAuthenticated by authRepository.isAuthenticated.collectAsState(initial = false)
    val view = LocalView.current

    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }

    suspend fun start() {
        loading = true
        error = null
        session = null
        try {
            session = authRepository.startDeviceAuth()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = onboardingErrorMessage(e)
        }
        loading = false
    }

    LaunchedEffect(retryCount) { start() }

    session?.let { activeSession ->
        LaunchedEffect(activeSession.deviceCode) {
            val result = authRepository.awaitAuthCompletion(activeSession)
            result.fold(
                onSuccess = { onAuthenticated() },
                onFailure = { throwable ->
                    if (throwable is CancellationException) throw throwable
                    error = onboardingErrorMessage(throwable)
                },
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(TidalColors.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            PulsingCyanDot()
        } else {
            DeviceCodeContent(
                userCode = session?.userCode,
                verificationUri = session?.verificationUri ?: "link.tidal.com",
                errorMessage = error,
                isAuthenticated = isAuthenticated,
                onDone = onAuthenticated,
                onRetry = { retryCount += 1 },
            )
        }
    }

    if (!loading && error != null) {
        LaunchedEffect(error) { session = null }
    }
}

@Composable
private fun DeviceCodeContent(
    userCode: String?,
    verificationUri: String,
    errorMessage: String?,
    isAuthenticated: Boolean,
    onDone: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Spacer(Modifier.weight(1f))
        val prompt = buildAnnotatedString {
            withStyle(SpanStyle(color = TidalColors.White)) { append("Go to ") }
            withStyle(SpanStyle(color = TidalColors.Cyan)) { append(verificationUri.removePrefix("https://").removeSuffix("/")) }
            withStyle(SpanStyle(color = TidalColors.White)) { append(" to connect") }
        }
        Text(
            text = prompt,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp,
            maxLines = 2,
        )
        Spacer(Modifier.height(20.dp))
        if (errorMessage != null || userCode.isNullOrBlank()) {
            Text(
                text = errorMessage ?: "Sign-in failed",
                color = TidalColors.OnSurfaceMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = userCode,
                color = TidalColors.Cyan,
                fontSize = if (userCode.length >= 7) 28.sp else 34.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { if (errorMessage != null) onRetry() else if (isAuthenticated) onDone() },
            enabled = true,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = TidalColors.SurfaceHigh,
                contentColor = TidalColors.White,
            ),
            modifier = Modifier.fillMaxWidth().height(36.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(
                text = if (errorMessage != null) "Retry" else "Done",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun onboardingErrorMessage(throwable: Throwable): String = when (throwable) {
    is TidalAuthException -> when (throwable.message) {
        "expired_token" -> "Code expired"
        "access_denied" -> "Sign-in cancelled"
        "slow_down" -> "Trying again..."
        null -> "Sign-in failed"
        else -> "Sign-in failed: ${throwable.message}"
    }
    is IOException -> "Connection issue - try again"
    else -> "Sign-in failed"
}

@Composable
private fun PulsingCyanDot() {
    val transition = rememberInfiniteTransition(label = "onboarding-loading")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(650), repeatMode = RepeatMode.Reverse),
        label = "dot-alpha",
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .alpha(alpha)
            .background(TidalColors.Cyan, RoundedCornerShape(50)),
    )
}
