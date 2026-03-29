package com.soulmint.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soulmint.data.model.AvatarVariant
import com.soulmint.data.model.MatchProfile
import com.soulmint.data.model.MintProgressStep
import com.soulmint.data.model.ProfileCard
import com.soulmint.data.model.TraitTag
import com.soulmint.ui.theme.AccentGlow
import com.soulmint.ui.theme.AccentGold
import com.soulmint.ui.theme.AccentRose
import com.soulmint.ui.theme.AccentViolet
import com.soulmint.ui.theme.BgElevated
import com.soulmint.ui.theme.BorderSubtle
import com.soulmint.ui.theme.Success
import com.soulmint.ui.theme.TextMuted
import com.soulmint.ui.theme.TextPrimary
import com.soulmint.ui.theme.TextSecondary
import coil3.compose.SubcomposeAsyncImage

@Composable
fun ScreenContainer(innerPadding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        content = content
    )
}

@Composable
fun HeroHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.displayLarge, color = TextPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagGroup(title: String, tags: List<TraitTag>, onToggle: ((String) -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag -> TagChip(tag, onToggle?.let { { it(tag.label) } }) }
        }
    }
}

@Composable
fun TagChip(tag: TraitTag, onClick: (() -> Unit)? = null) {
    val background = if (tag.selected) AccentViolet else BgElevated
    val textColor = if (tag.selected) TextPrimary else TextSecondary
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = onClick != null) { onClick?.invoke() },
        shape = RoundedCornerShape(16.dp),
        color = background,
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Text(tag.label, color = textColor, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable
fun AvatarArt(
    avatar: AvatarVariant,
    modifier: Modifier = Modifier,
    label: String? = null,
    onReady: (() -> Unit)? = null
) {
    var didNotifyReady by remember(avatar.id, avatar.previewUrl) { mutableStateOf(false) }
    fun notifyReadyOnce() {
        if (!didNotifyReady) {
            didNotifyReady = true
            onReady?.invoke()
        }
    }
    Box(modifier = modifier.clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(avatar.palette))) {
        avatar.previewUrl?.takeIf { it.isNotBlank() }?.let { previewUrl ->
            SubcomposeAsyncImage(
                model = previewUrl,
                contentDescription = label ?: avatar.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { notifyReadyOnce() },
                onError = { notifyReadyOnce() },
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(color = AccentGlow)
                            Text("Loading portrait preview...", color = TextPrimary)
                        }
                    }
                },
                error = {
                    AvatarFallbackSurface(avatar = avatar, label = label)
                }
            )
        }
        if (avatar.previewUrl.isNullOrBlank()) {
            AvatarFallbackSurface(avatar = avatar, label = label)
            LaunchedEffect(avatar.id) {
                notifyReadyOnce()
            }
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0A0A0F)))
            )
        )
        if (label != null) {
            Text(label, color = TextPrimary, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.BottomStart).padding(18.dp))
        }
    }
}

@Composable
private fun AvatarFallbackSurface(avatar: AvatarVariant, label: String? = null) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(
                        avatar.palette.last().copy(alpha = 0.95f),
                        avatar.palette.first().copy(alpha = 0.9f),
                        Color(0xFF0A0A0F)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .aspectRatio(0.82f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 34.dp)
                        .size(118.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 26.dp)
                        .fillMaxWidth(0.72f)
                        .height(150.dp)
                        .clip(RoundedCornerShape(topStart = 110.dp, topEnd = 110.dp, bottomStart = 26.dp, bottomEnd = 26.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp)
                        .size(width = 168.dp, height = 70.dp)
                        .clip(RoundedCornerShape(60.dp))
                        .background(avatar.palette.last().copy(alpha = 0.28f))
                )
            }
            Text(label ?: avatar.title, color = TextPrimary, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NftCard(card: ProfileCard, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().background(
                Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, Color(0xFF231A2E)))
            )
        ) {
            AvatarArt(card.avatar, modifier = Modifier.fillMaxWidth().height(280.dp), label = card.name)
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Surface(color = Color.Transparent, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, AccentGold)) {
                        Text("◈ ${card.tier.uppercase()}", color = AccentGold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                    Text("#${card.tokenId.toString().padStart(3, '0')}", color = TextSecondary, fontFamily = FontFamily.Monospace)
                }
                Text(card.bio, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Text("About me", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    card.selfTags.forEach { TagChip(TraitTag(it, true)) }
                }
                Text("Looking for", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    card.dreamTags.forEach { TagChip(TraitTag(it, false)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = AccentRose)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${card.compatibility}% compatible", color = AccentRose, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun MatchCard(profile: MatchProfile, modifier: Modifier = Modifier) {
    NftCard(
        card = ProfileCard(
            name = profile.name,
            tier = profile.tier,
            tokenId = profile.tokenId,
            selfTags = profile.selfTags,
            dreamTags = profile.dreamTags,
            compatibility = profile.compatibility,
            avatar = profile.avatar,
            bio = "A strong mutual signal with room for curiosity, chemistry, and a little mystery.",
            dreamDescription = ""
        ),
        modifier = modifier
    )
}

@Composable
fun MintStepper(steps: List<MintProgressStep>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        steps.forEach { step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dotColor = when {
                    step.completed -> Success
                    step.active -> AccentGlow
                    else -> BorderSubtle
                }
                Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(dotColor))
                Spacer(modifier = Modifier.width(12.dp))
                Text(step.title, color = if (step.completed || step.active) TextPrimary else TextSecondary)
            }
        }
    }
}

@Composable
fun AvatarVariantTile(avatar: AvatarVariant, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) AccentGlow else BorderSubtle
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .border(2.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(2.dp)
    ) {
        AvatarArt(avatar, modifier = Modifier.fillMaxSize(), label = avatar.title)
    }
}
