package com.example.notebucket.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NoAccounts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.notebucket.ui.theme.md_primary
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import com.example.notebucket.R
import com.example.notebucket.ai.BgeEmbedder
import com.example.notebucket.data.SettingsRepository
import com.example.notebucket.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val embedder: BgeEmbedder,
    private val settings: SettingsRepository
) : ViewModel() {

    val loadState: StateFlow<BgeEmbedder.LoadState> =
        embedder.loadState.stateIn(viewModelScope, SharingStarted.Eagerly, BgeEmbedder.LoadState.Idle)

    init {
        viewModelScope.launch { embedder.loadModel() }
    }

    fun retry() {
        viewModelScope.launch { embedder.loadModel() }
    }

    fun completeOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingDone(true)
            onDone()
        }
    }
}

@Composable
fun OnboardingScreen(navController: NavHostController) {
    val vm: OnboardingViewModel = hiltViewModel()
    val state by vm.loadState.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> OnboardingWelcome()
                1 -> OnboardingPrivacy()
                2 -> OnboardingHowItWorks()
                3 -> OnboardingFeatures()
                4 -> OnboardingReady(
                    loadState = state,
                    onRetry = { vm.retry() },
                    onGetStarted = {
                        vm.completeOnboarding {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        }
                    }
                )
            }
        }

        // Skip button (top-right)
        AnimatedVisibility(
            visible = pagerState.currentPage < 4,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            TextButton(
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(4)
                    }
                },
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Progress dots (bottom center)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(5) { index ->
                val isActive = pagerState.currentPage == index
                val alpha by animateFloatAsState(
                    targetValue = if (isActive) 1f else 0.4f,
                    label = "dotAlpha"
                )
                val size = if (isActive) 10.dp else 8.dp
                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(
                            md_primary.copy(alpha = alpha)
                        )
                )
            }
        }
    }
}

@Composable
private fun OnboardingWelcome() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = md_primary.copy(alpha = 0.8f)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.onboarding_welcome_tagline),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = md_primary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OnboardingPrivacy() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = md_primary
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.onboarding_privacy_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        PrivacyItem(
            icon = Icons.Default.CloudOff,
            text = stringResource(R.string.onboarding_privacy_cloud)
        )
        PrivacyItem(
            icon = Icons.Default.NoAccounts,
            text = stringResource(R.string.onboarding_privacy_accounts)
        )
        PrivacyItem(
            icon = Icons.Default.Analytics,
            text = stringResource(R.string.onboarding_privacy_analytics)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.onboarding_privacy_footer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PrivacyItem(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = md_primary
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun OnboardingHowItWorks() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.onboarding_how_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        // Flow: Write → AI reads → Auto-filed
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HowStep(
                icon = Icons.Default.Edit,
                label = stringResource(R.string.onboarding_how_step1)
            )
            Text(
                text = "→",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HowStep(
                icon = Icons.Default.AutoAwesome,
                label = stringResource(R.string.onboarding_how_step2)
            )
            Text(
                text = "→",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HowStep(
                icon = Icons.Default.FolderOpen,
                label = stringResource(R.string.onboarding_how_step3)
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.onboarding_how_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HowStep(icon: ImageVector, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = md_primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun OnboardingFeatures() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.onboarding_features_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FeatureCard(
                icon = Icons.Default.Search,
                title = stringResource(R.string.onboarding_feature_search),
                body = stringResource(R.string.onboarding_feature_search_body)
            )
            FeatureCard(
                icon = Icons.Default.AttachFile,
                title = stringResource(R.string.onboarding_feature_attachments),
                body = stringResource(R.string.onboarding_feature_attachments_body)
            )
            FeatureCard(
                icon = Icons.Default.Shield,
                title = stringResource(R.string.onboarding_feature_drafts),
                body = stringResource(R.string.onboarding_feature_drafts_body)
            )
        }
    }
}

@Composable
private fun FeatureCard(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = md_primary
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OnboardingReady(
    loadState: BgeEmbedder.LoadState,
    onRetry: () -> Unit,
    onGetStarted: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.onboarding_ready_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        when (loadState) {
            BgeEmbedder.LoadState.Loading, BgeEmbedder.LoadState.Idle -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.onboarding_model_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            BgeEmbedder.LoadState.Loaded -> {
                Text(
                    text = stringResource(R.string.onboarding_model_ready),
                    style = MaterialTheme.typography.bodyMedium,
                    color = md_primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.onboarding_get_started))
                }
            }
            BgeEmbedder.LoadState.Failed -> {
                Text(
                    text = "Model failed to load. Ensure bge-small-en-v1.5.Q8_0.gguf is present in app/src/main/assets/.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.onboarding_retry))
                }
            }
        }
    }
}
